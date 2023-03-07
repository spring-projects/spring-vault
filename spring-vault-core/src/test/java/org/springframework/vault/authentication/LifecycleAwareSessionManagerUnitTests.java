/*
 * Copyright 2016-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.vault.authentication;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.vault.authentication.event.*;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.support.LeaseStrategy;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestOperations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LifecycleAwareSessionManager}.
 *
 * @author Mark Paluch
 */
@ExtendWith(MockitoExtension.class)
class LifecycleAwareSessionManagerUnitTests {

	@Mock
	ClientAuthentication clientAuthentication;

	@Mock
	TaskScheduler taskScheduler;

	@Mock
	RestOperations restOperations;

	@Mock
	AuthenticationListener listener;

	@Mock
	AuthenticationErrorListener errorListener;

	@Captor
	ArgumentCaptor<AuthenticationEvent> captor;

	private LifecycleAwareSessionManager sessionManager;

	@BeforeEach
	void before() {
		this.sessionManager = new LifecycleAwareSessionManager(this.clientAuthentication, this.taskScheduler,
				this.restOperations);
		this.sessionManager.addAuthenticationListener(this.listener);
		this.sessionManager.addErrorListener(this.errorListener);
	}

	@Test
	void shouldObtainTokenFromClientAuthentication() {

		when(this.clientAuthentication.login()).thenReturn(LoginToken.of("login"));

		assertThat(this.sessionManager.getSessionToken()).isEqualTo(LoginToken.of("login"));
		verify(this.listener).onAuthenticationEvent(any(AfterLoginEvent.class));
	}

	@Test
	void loginShouldFail() {

		when(this.clientAuthentication.login()).thenThrow(new VaultLoginException("foo"));

		assertThatExceptionOfType(VaultLoginException.class).isThrownBy(() -> this.sessionManager.getSessionToken());
		verifyZeroInteractions(this.listener);
		verify(this.errorListener).onAuthenticationError(any(LoginFailedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldSelfLookupToken() {

		VaultResponse vaultResponse = new VaultResponse();
		vaultResponse.setData(Collections.singletonMap("ttl", 100));

		when(this.clientAuthentication.login()).thenReturn(VaultToken.of("login"));

		when(this.restOperations.exchange(anyString(), any(), any(), ArgumentMatchers.<Class>any()))
				.thenReturn(new ResponseEntity<>(vaultResponse, HttpStatus.OK));

		LoginToken sessionToken = (LoginToken) this.sessionManager.getSessionToken();
		assertThat(sessionToken.getLeaseDuration()).isEqualTo(Duration.ofSeconds(100));

		verify(this.restOperations).exchange(eq("auth/token/lookup-self"), eq(HttpMethod.GET),
				eq(new HttpEntity<>(VaultHttpHeaders.from(LoginToken.of("login")))), any(Class.class));

		verify(this.listener).onAuthenticationEvent(this.captor.capture());
		AfterLoginEvent event = (AfterLoginEvent) this.captor.getValue();
		assertThat(event.getSource()).isSameAs(sessionToken);
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldContinueIfSelfLookupFails() {

		VaultResponse vaultResponse = new VaultResponse();
		vaultResponse.setData(Collections.singletonMap("ttl", 100));

		when(this.clientAuthentication.login()).thenReturn(VaultToken.of("login"));

		when(this.restOperations.exchange(anyString(), any(), any(), ArgumentMatchers.<Class>any()))
				.thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));

		VaultToken sessionToken = this.sessionManager.getSessionToken();
		assertThat(sessionToken).isExactlyInstanceOf(VaultToken.class);
		verify(this.listener).onAuthenticationEvent(any(AfterLoginEvent.class));
		verify(this.errorListener).onAuthenticationError(any());
	}

	@Test
	void shouldTranslateExceptionOnTokenRenewal() {

		when(this.clientAuthentication.login())
				.thenReturn(LoginToken.renewable("login".toCharArray(), Duration.ofMinutes(5)));
		when(this.restOperations.postForObject(anyString(), any(HttpEntity.class), any()))
				.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Some server error"));

		AtomicReference<AuthenticationErrorEvent> listener = new AtomicReference<>();
		this.sessionManager.addErrorListener(listener::set);

		this.sessionManager.getSessionToken();
		this.sessionManager.renewToken();

		assertThat(listener.get().getException()).isInstanceOf(VaultTokenRenewalException.class)
				.hasCauseInstanceOf(HttpServerErrorException.class)
				.hasMessageContaining("Cannot renew token: Status 500 Some server error");
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldRevokeLoginTokenOnDestroy() {

		when(this.clientAuthentication.login()).thenReturn(LoginToken.of("login"));

		this.sessionManager.renewToken();
		this.sessionManager.destroy();

		verify(this.restOperations).postForObject(eq("auth/token/revoke-self"),
				eq(new HttpEntity<>(VaultHttpHeaders.from(LoginToken.of("login")))), any(Class.class));

		verify(this.listener).onAuthenticationEvent(any(BeforeLoginTokenRevocationEvent.class));
		verify(this.listener).onAuthenticationEvent(any(AfterLoginTokenRevocationEvent.class));
	}

	@Test
	void shouldNotRevokeRegularTokenOnDestroy() {

		when(this.clientAuthentication.login()).thenReturn(VaultToken.of("login"));

		this.sessionManager.setTokenSelfLookupEnabled(false);
		this.sessionManager.renewToken();
		this.sessionManager.destroy();

		verifyZeroInteractions(this.restOperations);
		verify(this.listener).onAuthenticationEvent(any(AfterLoginEvent.class));
		verifyNoMoreInteractions(this.listener);
	}

	@Test
	void shouldNotRevokeBatchToken() {

		LoginToken batchToken = LoginToken.builder().token("login").type("batch").build();

		when(this.clientAuthentication.login()).thenReturn(batchToken);

		this.sessionManager.setTokenSelfLookupEnabled(false);
		this.sessionManager.renewToken();
		this.sessionManager.destroy();

		verifyNoInteractions(this.restOperations);
		verify(this.listener).onAuthenticationEvent(any(AfterLoginEvent.class));
		verifyNoMoreInteractions(this.listener);
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldNotThrowExceptionsOnRevokeErrors() {

		when(this.clientAuthentication.login()).thenReturn(LoginToken.of("login"));

		when(this.restOperations.postForObject(anyString(), any(), ArgumentMatchers.<Class>any()))
				.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		this.sessionManager.renewToken();
		this.sessionManager.destroy();

		verify(this.restOperations).postForObject(eq("auth/token/revoke-self"),
				eq(new HttpEntity<>(VaultHttpHeaders.from(LoginToken.of("login")))), any(Class.class));
		verify(this.listener).onAuthenticationEvent(any(AfterLoginEvent.class));
		verify(this.listener).onAuthenticationEvent(any(BeforeLoginTokenRevocationEvent.class));
		verifyNoMoreInteractions(this.listener);
		verify(this.errorListener).onAuthenticationError(any(LoginTokenRevocationFailedEvent.class));
	}

	@Test
	void shouldScheduleTokenRenewal() {

		when(this.clientAuthentication.login())
				.thenReturn(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));

		this.sessionManager.getSessionToken();

		verify(this.taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldRunTokenRenewal() {

		when(this.clientAuthentication.login())
				.thenReturn(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		when(this.restOperations.postForObject(anyString(), any(), eq(VaultResponse.class)))
				.thenReturn(fromToken(LoginToken.of("foo".toCharArray(), Duration.ofSeconds(10))));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

		this.sessionManager.getSessionToken();
		verify(this.taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));

		runnableCaptor.getValue().run();

		verify(this.restOperations).postForObject(eq("auth/token/renew-self"),
				eq(new HttpEntity<>(
						VaultHttpHeaders.from(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5))))),
				any(Class.class));
		verify(this.clientAuthentication, times(1)).login();
		verify(this.listener).onAuthenticationEvent(any(BeforeLoginTokenRenewedEvent.class));
		verify(this.listener).onAuthenticationEvent(any(AfterLoginTokenRenewedEvent.class));
	}

	@Test
	void shouldReScheduleTokenRenewalAfterSuccessfulRenewal() {

		when(this.clientAuthentication.login())
				.thenReturn(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		when(this.restOperations.postForObject(anyString(), any(), eq(VaultResponse.class)))
				.thenReturn(fromToken(LoginToken.of("foo".toCharArray(), Duration.ofSeconds(10))));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

		this.sessionManager.getSessionToken();
		verify(this.taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));

		runnableCaptor.getValue().run();

		verify(this.taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
	}

	@Test
	void shouldNotScheduleRenewalIfRenewalTtlExceedsThreshold() {

		when(this.clientAuthentication.login())
				.thenReturn(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		when(this.restOperations.postForObject(anyString(), any(), eq(VaultResponse.class)))
				.thenReturn(fromToken(LoginToken.of("foo".toCharArray(), Duration.ofSeconds(2))));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

		this.sessionManager.getSessionToken();
		verify(this.taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));

		runnableCaptor.getValue().run();

		verify(this.taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
	}

	@Test
	void shouldReLoginIfRenewalTtlExceedsThreshold() {

		when(this.clientAuthentication.login()).thenReturn(
				LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)),
				LoginToken.renewable("bar".toCharArray(), Duration.ofSeconds(5)));
		when(this.restOperations.postForObject(anyString(), any(), eq(VaultResponse.class)))
				.thenReturn(fromToken(LoginToken.of("foo".toCharArray(), Duration.ofSeconds(2))));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
		this.sessionManager.getSessionToken();
		verify(this.taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
		runnableCaptor.getValue().run();

		assertThat(this.sessionManager.getSessionToken())
				.isEqualTo(LoginToken.renewable("bar".toCharArray(), Duration.ofSeconds(5)));

		verify(this.clientAuthentication, times(2)).login();
		verify(this.listener, times(2)).onAuthenticationEvent(any(AfterLoginEvent.class));
		verify(this.listener).onAuthenticationEvent(any(LoginTokenExpiredEvent.class));
	}

	@Test
	void shouldReLoginIfRenewalFails() {

		when(this.clientAuthentication.login()).thenReturn(
				LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)),
				LoginToken.renewable("bar".toCharArray(), Duration.ofSeconds(5)));
		when(this.restOperations.postForObject(anyString(), any(), eq(VaultResponse.class)))
				.thenThrow(new ResourceAccessException("Connection refused"));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
		this.sessionManager.getSessionToken();
		verify(this.taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
		runnableCaptor.getValue().run();

		assertThat(this.sessionManager.getSessionToken())
				.isEqualTo(LoginToken.renewable("bar".toCharArray(), Duration.ofSeconds(5)));

		verify(this.clientAuthentication, times(2)).login();
	}

	@Test
	void shouldRetainTokenAfterRenewalFailure() {

		when(this.clientAuthentication.login()).thenReturn(
				LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)),
				LoginToken.renewable("bar".toCharArray(), Duration.ofSeconds(5)));
		when(this.restOperations.postForObject(anyString(), any(), eq(VaultResponse.class)))
				.thenThrow(new ResourceAccessException("Connection refused"));
		this.sessionManager.setLeaseStrategy(LeaseStrategy.retainOnError());

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
		this.sessionManager.getSessionToken();
		verify(this.taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
		runnableCaptor.getValue().run();

		assertThat(this.sessionManager.getSessionToken())
				.isEqualTo(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));

		verify(this.clientAuthentication).login();
	}

	@Test
	void shouldUseTaskScheduler() {

		this.sessionManager = new LifecycleAwareSessionManager(this.clientAuthentication, this.taskScheduler,
				this.restOperations);

		when(this.clientAuthentication.login())
				.thenReturn(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));

		ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);

		this.sessionManager.getSessionToken();
		verify(this.taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());

		assertThat(triggerCaptor.getValue().nextExecutionTime(null)).isNotNull();
		assertThat(triggerCaptor.getValue().nextExecutionTime(null)).isNull();
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldNotReScheduleTokenRenewalAfterFailedRenewal() {

		when(this.clientAuthentication.login())
				.thenReturn(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		when(this.restOperations.postForObject(anyString(), any(), ArgumentMatchers.<Class>any()))
				.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

		this.sessionManager.getSessionToken();
		verify(this.taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));

		runnableCaptor.getValue().run();

		verify(this.taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
	}

	@Test
	void shouldObtainTokenIfNoTokenAvailable() {

		when(this.clientAuthentication.login())
				.thenReturn(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));

		this.sessionManager.renewToken();

		assertThat(this.sessionManager.getSessionToken())
				.isEqualTo(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		verify(this.clientAuthentication, times(1)).login();
	}

	@Test
	@SuppressWarnings("unchecked")
	void renewShouldReportFalseIfTokenRenewalFails() {

		when(this.clientAuthentication.login())
				.thenReturn(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		when(this.restOperations.postForObject(anyString(), ArgumentMatchers.any(), ArgumentMatchers.<Class>any()))
				.thenThrow(new HttpServerErrorException(HttpStatus.BAD_REQUEST));

		this.sessionManager.getSessionToken();

		assertThat(this.sessionManager.renewToken()).isFalse();
		verify(this.clientAuthentication, times(1)).login();
	}

	@Test
	void renewShouldRetainTokenOnIoError() {

		when(this.clientAuthentication.login())
				.thenReturn(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		when(this.restOperations.postForObject(anyString(), ArgumentMatchers.any(), ArgumentMatchers.<Class>any()))
				.thenThrow(new ResourceAccessException("err", new SSLException("foo")));

		this.sessionManager.setLeaseStrategy(LeaseStrategy.retainOnIoError());
		this.sessionManager.getSessionToken();

		assertThat(this.sessionManager.renewToken()).isFalse();
		assertThat(this.sessionManager.getToken()).isNotEmpty();
		verify(this.clientAuthentication, times(1)).login();
	}

	private static VaultResponse fromToken(LoginToken loginToken) {

		Map<String, Object> auth = new HashMap<>();

		auth.put("client_token", loginToken.getToken());
		auth.put("renewable", loginToken.isRenewable());
		auth.put("lease_duration", loginToken.getLeaseDuration().getSeconds());

		VaultResponse response = new VaultResponse();
		response.setAuth(auth);

		return response;
	}

}
