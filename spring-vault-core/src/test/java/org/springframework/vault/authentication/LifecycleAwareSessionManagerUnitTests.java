/*
 * Copyright 2016-2019 the original author or authors.
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
import org.springframework.vault.authentication.event.AfterLoginEvent;
import org.springframework.vault.authentication.event.AfterLoginTokenRenewedEvent;
import org.springframework.vault.authentication.event.AfterLoginTokenRevocationEvent;
import org.springframework.vault.authentication.event.AuthenticationErrorEvent;
import org.springframework.vault.authentication.event.AuthenticationErrorListener;
import org.springframework.vault.authentication.event.AuthenticationEvent;
import org.springframework.vault.authentication.event.AuthenticationListener;
import org.springframework.vault.authentication.event.BeforeLoginTokenRenewedEvent;
import org.springframework.vault.authentication.event.BeforeLoginTokenRevocationEvent;
import org.springframework.vault.authentication.event.LoginFailedEvent;
import org.springframework.vault.authentication.event.LoginTokenExpiredEvent;
import org.springframework.vault.authentication.event.LoginTokenRevocationFailedEvent;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.support.LeaseStrategy;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

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
		sessionManager = new LifecycleAwareSessionManager(clientAuthentication,
				taskScheduler, restOperations);
		sessionManager.addAuthenticationListener(listener);
		sessionManager.addErrorListener(errorListener);
	}

	@Test
	void shouldObtainTokenFromClientAuthentication() {

		when(clientAuthentication.login()).thenReturn(LoginToken.of("login"));

		assertThat(sessionManager.getSessionToken()).isEqualTo(LoginToken.of("login"));
		verify(listener).onAuthenticationEvent(any(AfterLoginEvent.class));
	}

	@Test
	void loginShouldFail() {

		when(clientAuthentication.login()).thenThrow(new VaultLoginException("foo"));

		assertThatExceptionOfType(VaultLoginException.class)
				.isThrownBy(() -> sessionManager.getSessionToken());
		verifyZeroInteractions(listener);
		verify(errorListener).onAuthenticationError(any(LoginFailedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldSelfLookupToken() {

		VaultResponse vaultResponse = new VaultResponse();
		vaultResponse.setData(Collections.singletonMap("ttl", 100));

		when(clientAuthentication.login()).thenReturn(VaultToken.of("login"));

		when(restOperations.exchange(anyString(), any(), any(),
				ArgumentMatchers.<Class> any()))
						.thenReturn(new ResponseEntity<>(vaultResponse, HttpStatus.OK));

		LoginToken sessionToken = (LoginToken) sessionManager.getSessionToken();
		assertThat(sessionToken.getLeaseDuration()).isEqualTo(Duration.ofSeconds(100));

		verify(restOperations).exchange(eq("auth/token/lookup-self"), eq(HttpMethod.GET),
				eq(new HttpEntity<>(VaultHttpHeaders.from(LoginToken.of("login")))),
				any(Class.class));

		verify(listener).onAuthenticationEvent(captor.capture());
		AfterLoginEvent event = (AfterLoginEvent) captor.getValue();
		assertThat(event.getSource()).isSameAs(sessionToken);
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldContinueIfSelfLookupFails() {

		VaultResponse vaultResponse = new VaultResponse();
		vaultResponse.setData(Collections.singletonMap("ttl", 100));

		when(clientAuthentication.login()).thenReturn(VaultToken.of("login"));

		when(restOperations.exchange(anyString(), any(), any(),
				ArgumentMatchers.<Class> any()))
						.thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));

		VaultToken sessionToken = sessionManager.getSessionToken();
		assertThat(sessionToken).isExactlyInstanceOf(VaultToken.class);
		verify(listener).onAuthenticationEvent(any(AfterLoginEvent.class));
		verify(errorListener).onAuthenticationError(any());
	}

	@Test
	void shouldTranslateExceptionOnTokenRenewal() {

		when(clientAuthentication.login()).thenReturn(
				LoginToken.renewable("login".toCharArray(), Duration.ofMinutes(5)));
		when(restOperations.postForObject(anyString(), any(HttpEntity.class), any()))
				.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR,
						"Some server error"));

		AtomicReference<AuthenticationErrorEvent> listener = new AtomicReference<>();
		sessionManager.addErrorListener(listener::set);

		sessionManager.getSessionToken();
		sessionManager.renewToken();

		assertThat(listener.get().getException())
				.isInstanceOf(VaultTokenRenewalException.class)
				.hasCauseInstanceOf(HttpServerErrorException.class)
				.hasMessageContaining("Cannot renew token: Status 500 Some server error");
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldRevokeLoginTokenOnDestroy() {

		when(clientAuthentication.login()).thenReturn(LoginToken.of("login"));

		sessionManager.renewToken();
		sessionManager.destroy();

		verify(restOperations).postForObject(eq("auth/token/revoke-self"),
				eq(new HttpEntity<>(VaultHttpHeaders.from(LoginToken.of("login")))),
				any(Class.class));

		verify(listener)
				.onAuthenticationEvent(any(BeforeLoginTokenRevocationEvent.class));
		verify(listener).onAuthenticationEvent(any(AfterLoginTokenRevocationEvent.class));
	}

	@Test
	void shouldNotRevokeRegularTokenOnDestroy() {

		when(clientAuthentication.login()).thenReturn(VaultToken.of("login"));

		sessionManager.setTokenSelfLookupEnabled(false);
		sessionManager.renewToken();
		sessionManager.destroy();

		verifyZeroInteractions(restOperations);
		verify(listener).onAuthenticationEvent(any(AfterLoginEvent.class));
		verifyNoMoreInteractions(listener);
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldNotThrowExceptionsOnRevokeErrors() {

		when(clientAuthentication.login()).thenReturn(LoginToken.of("login"));

		when(restOperations.postForObject(anyString(), any(),
				ArgumentMatchers.<Class> any())).thenThrow(
						new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		sessionManager.renewToken();
		sessionManager.destroy();

		verify(restOperations).postForObject(eq("auth/token/revoke-self"),
				eq(new HttpEntity<>(VaultHttpHeaders.from(LoginToken.of("login")))),
				any(Class.class));
		verify(listener).onAuthenticationEvent(any(AfterLoginEvent.class));
		verify(listener)
				.onAuthenticationEvent(any(BeforeLoginTokenRevocationEvent.class));
		verifyNoMoreInteractions(listener);
		verify(errorListener)
				.onAuthenticationError(any(LoginTokenRevocationFailedEvent.class));
	}

	@Test
	void shouldScheduleTokenRenewal() {

		when(clientAuthentication.login()).thenReturn(
				LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));

		sessionManager.getSessionToken();

		verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldRunTokenRenewal() {

		when(clientAuthentication.login()).thenReturn(
				LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		when(restOperations.postForObject(anyString(), any(), eq(VaultResponse.class)))
				.thenReturn(fromToken(
						LoginToken.of("foo".toCharArray(), Duration.ofSeconds(10))));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

		sessionManager.getSessionToken();
		verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));

		runnableCaptor.getValue().run();

		verify(restOperations).postForObject(eq("auth/token/renew-self"),
				eq(new HttpEntity<>(VaultHttpHeaders.from(LoginToken
						.renewable("login".toCharArray(), Duration.ofSeconds(5))))),
				any(Class.class));
		verify(clientAuthentication, times(1)).login();
		verify(listener).onAuthenticationEvent(any(BeforeLoginTokenRenewedEvent.class));
		verify(listener).onAuthenticationEvent(any(AfterLoginTokenRenewedEvent.class));
	}

	@Test
	void shouldReScheduleTokenRenewalAfterSuccessfulRenewal() {

		when(clientAuthentication.login()).thenReturn(
				LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		when(restOperations.postForObject(anyString(), any(), eq(VaultResponse.class)))
				.thenReturn(fromToken(
						LoginToken.of("foo".toCharArray(), Duration.ofSeconds(10))));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

		sessionManager.getSessionToken();
		verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));

		runnableCaptor.getValue().run();

		verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
	}

	@Test
	void shouldNotScheduleRenewalIfRenewalTtlExceedsThreshold() {

		when(clientAuthentication.login()).thenReturn(
				LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		when(restOperations.postForObject(anyString(), any(), eq(VaultResponse.class)))
				.thenReturn(fromToken(
						LoginToken.of("foo".toCharArray(), Duration.ofSeconds(2))));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

		sessionManager.getSessionToken();
		verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));

		runnableCaptor.getValue().run();

		verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
	}

	@Test
	void shouldReLoginIfRenewalTtlExceedsThreshold() {

		when(clientAuthentication.login()).thenReturn(
				LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)),
				LoginToken.renewable("bar".toCharArray(), Duration.ofSeconds(5)));
		when(restOperations.postForObject(anyString(), any(), eq(VaultResponse.class)))
				.thenReturn(fromToken(
						LoginToken.of("foo".toCharArray(), Duration.ofSeconds(2))));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
		sessionManager.getSessionToken();
		verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
		runnableCaptor.getValue().run();

		assertThat(sessionManager.getSessionToken()).isEqualTo(
				LoginToken.renewable("bar".toCharArray(), Duration.ofSeconds(5)));

		verify(clientAuthentication, times(2)).login();
		verify(listener, times(2)).onAuthenticationEvent(any(AfterLoginEvent.class));
		verify(listener).onAuthenticationEvent(any(LoginTokenExpiredEvent.class));
	}

	@Test
	void shouldReLoginIfRenewalFails() {

		when(clientAuthentication.login()).thenReturn(
				LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)),
				LoginToken.renewable("bar".toCharArray(), Duration.ofSeconds(5)));
		when(restOperations.postForObject(anyString(), any(), eq(VaultResponse.class)))
				.thenThrow(new ResourceAccessException("Connection refused"));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
		sessionManager.getSessionToken();
		verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
		runnableCaptor.getValue().run();

		assertThat(sessionManager.getSessionToken()).isEqualTo(
				LoginToken.renewable("bar".toCharArray(), Duration.ofSeconds(5)));

		verify(clientAuthentication, times(2)).login();
	}

	@Test
	void shouldRetainTokenAfterRenewalFailure() {

		when(clientAuthentication.login()).thenReturn(
				LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)),
				LoginToken.renewable("bar".toCharArray(), Duration.ofSeconds(5)));
		when(restOperations.postForObject(anyString(), any(), eq(VaultResponse.class)))
				.thenThrow(new ResourceAccessException("Connection refused"));
		sessionManager.setLeaseStrategy(LeaseStrategy.retainOnError());

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
		sessionManager.getSessionToken();
		verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
		runnableCaptor.getValue().run();

		assertThat(sessionManager.getSessionToken()).isEqualTo(
				LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));

		verify(clientAuthentication).login();
	}

	@Test
	void shouldUseTaskScheduler() {

		sessionManager = new LifecycleAwareSessionManager(clientAuthentication,
				taskScheduler, restOperations);

		when(clientAuthentication.login()).thenReturn(
				LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));

		ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);

		sessionManager.getSessionToken();
		verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());

		assertThat(triggerCaptor.getValue().nextExecutionTime(null)).isNotNull();
		assertThat(triggerCaptor.getValue().nextExecutionTime(null)).isNull();
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldNotReScheduleTokenRenewalAfterFailedRenewal() {

		when(clientAuthentication.login()).thenReturn(
				LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		when(restOperations.postForObject(anyString(), any(),
				ArgumentMatchers.<Class> any())).thenThrow(
						new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

		sessionManager.getSessionToken();
		verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));

		runnableCaptor.getValue().run();

		verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
	}

	@Test
	void shouldObtainTokenIfNoTokenAvailable() {

		when(clientAuthentication.login()).thenReturn(
				LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));

		sessionManager.renewToken();

		assertThat(sessionManager.getSessionToken()).isEqualTo(
				LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		verify(clientAuthentication, times(1)).login();
	}

	@Test
	@SuppressWarnings("unchecked")
	void renewShouldReportFalseIfTokenRenewalFails() {

		when(clientAuthentication.login()).thenReturn(
				LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		when(restOperations.postForObject(anyString(), ArgumentMatchers.any(),
				ArgumentMatchers.<Class> any()))
						.thenThrow(new HttpServerErrorException(HttpStatus.BAD_REQUEST));

		sessionManager.getSessionToken();

		assertThat(sessionManager.renewToken()).isFalse();
		verify(clientAuthentication, times(1)).login();
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
