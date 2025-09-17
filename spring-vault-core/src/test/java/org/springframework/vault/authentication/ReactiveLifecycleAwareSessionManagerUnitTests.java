/*
 * Copyright 2018-2025 the original author or authors.
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
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.vault.authentication.event.*;
import org.springframework.vault.support.LeaseStrategy;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReactiveLifecycleAwareSessionManager}.
 *
 * @author Mark Paluch
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReactiveLifecycleAwareSessionManagerUnitTests {

	@Mock
	VaultTokenSupplier tokenSupplier;

	@Mock
	TaskScheduler taskScheduler;

	@Mock
	WebClient webClient;

	@Mock
	RequestHeadersSpec requestHeadersSpec;

	@Mock
	RequestHeadersUriSpec requestHeadersUriSpec;

	@Mock
	RequestBodyUriSpec requestBodyUriSpec;

	@Mock
	RequestBodySpec requestBodySpec;

	@Mock
	ResponseSpec responseSpec;

	@Mock
	AuthenticationListener listener;

	@Mock
	AuthenticationErrorListener errorListener;

	@Captor
	ArgumentCaptor<AuthenticationEvent> captor;

	private ReactiveLifecycleAwareSessionManager sessionManager;

	@BeforeEach
	void before() {

		// POST
		when(this.webClient.post()).thenReturn(this.requestBodyUriSpec);
		when(this.requestBodyUriSpec.uri(anyString())).thenReturn(this.requestBodySpec);
		when(this.requestBodySpec.headers(any())).thenReturn(this.requestBodySpec);
		when(this.requestBodySpec.retrieve()).thenReturn(this.responseSpec);

		// GET
		when(this.webClient.get()).thenReturn(this.requestHeadersUriSpec);
		when(this.requestHeadersUriSpec.uri(anyString())).thenReturn(this.requestHeadersSpec);
		when(this.requestHeadersSpec.headers(any())).thenReturn(this.requestHeadersSpec);
		when(this.requestHeadersSpec.retrieve()).thenReturn(this.responseSpec);

		this.sessionManager = new ReactiveLifecycleAwareSessionManager(this.tokenSupplier, this.taskScheduler,
				this.webClient);
		this.sessionManager.addAuthenticationListener(this.listener);
		this.sessionManager.addErrorListener(this.errorListener);
	}

	@Test
	void shouldObtainTokenFromClientAuthentication() {

		mockToken(LoginToken.of("login"));

		this.sessionManager.getSessionToken() //
			.as(StepVerifier::create) //
			.expectNext(LoginToken.of("login")) //
			.verifyComplete();
		verify(this.listener).onAuthenticationEvent(any(AfterLoginEvent.class));
	}

	@Test
	void loginShouldFail() {

		when(this.tokenSupplier.getVaultToken()).thenReturn(Mono.error(new VaultLoginException("foo")));

		this.sessionManager.getSessionToken() //
			.as(StepVerifier::create) //
			.verifyError();
		verifyNoInteractions(this.listener);
		verify(this.errorListener).onAuthenticationError(any(LoginFailedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldSelfLookupToken() {

		VaultResponse vaultResponse = new VaultResponse();
		vaultResponse.setData(Collections.singletonMap("ttl", 100));

		mockToken(VaultToken.of("login"));

		when(this.responseSpec.bodyToMono((Class) any())).thenReturn(Mono.just(vaultResponse));

		this.sessionManager.getSessionToken().as(StepVerifier::create).assertNext(it -> {

			LoginToken sessionToken = (LoginToken) it;
			assertThat(sessionToken.getLeaseDuration()).isEqualTo(Duration.ofSeconds(100));
		}).verifyComplete();

		verify(this.webClient.get()).uri("auth/token/lookup-self");
		verify(this.listener).onAuthenticationEvent(this.captor.capture());
		AfterLoginEvent event = (AfterLoginEvent) this.captor.getValue();
		assertThat(event.getSource()).isInstanceOf(LoginToken.class);
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldContinueIfSelfLookupFails() {

		VaultResponse vaultResponse = new VaultResponse();
		vaultResponse.setData(Collections.singletonMap("ttl", 100));

		mockToken(VaultToken.of("login"));

		when(this.responseSpec.bodyToMono((Class) any()))
			.thenReturn(Mono.error(new WebClientResponseException("forbidden", 403, "Forbidden", null, null, null)));

		this.sessionManager.getSessionToken() //
			.as(StepVerifier::create) //
			.assertNext(it -> {
				assertThat(it).isExactlyInstanceOf(VaultToken.class);
			})
			.verifyComplete();
		verify(this.listener).onAuthenticationEvent(any(AfterLoginEvent.class));
		verify(this.errorListener).onAuthenticationError(any());
	}

	@Test
	void tokenRenewalShouldMapException() {

		mockToken(LoginToken.renewable("foo".toCharArray(), Duration.ofMinutes(1)));

		when(this.responseSpec.bodyToMono((Class) any())).thenReturn(Mono
			.error(new WebClientResponseException("Some server error", 500, "Some server error", null, null, null)));

		AtomicReference<AuthenticationErrorEvent> listener = new AtomicReference<>();
		this.sessionManager.addErrorListener(listener::set);

		this.sessionManager.getVaultToken().as(StepVerifier::create).expectNextCount(1).verifyComplete();
		this.sessionManager.renewToken().as(StepVerifier::create).verifyComplete();
		assertThat(listener.get().getException()).isInstanceOf(VaultTokenRenewalException.class)
			.hasCauseInstanceOf(WebClientResponseException.class)
			.hasMessageContaining("Cannot renew token: Status 500 Some server error");

	}

	@Test
	void shouldRevokeLoginTokenOnDestroy() {

		VaultResponse vaultResponse = new VaultResponse();
		vaultResponse.setData(Collections.singletonMap("ttl", 100));

		mockToken(LoginToken.of("login"));
		when(this.responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("OK"));

		this.sessionManager.getVaultToken() //
			.as(StepVerifier::create) //
			.expectNextCount(1) //
			.verifyComplete();

		this.sessionManager.destroy();

		verify(this.webClient.post()).uri("auth/token/revoke-self");
		verify(this.listener).onAuthenticationEvent(any(BeforeLoginTokenRevocationEvent.class));
		verify(this.listener).onAuthenticationEvent(any(AfterLoginTokenRevocationEvent.class));
	}

	@Test
	void shouldNotRevokeRegularTokenOnDestroy() {

		mockToken(VaultToken.of("login"));

		this.sessionManager.setTokenSelfLookupEnabled(false);
		this.sessionManager.renewToken() //
			.as(StepVerifier::create) //
			.expectNextCount(1) //
			.verifyComplete();
		this.sessionManager.destroy();

		verify(this.webClient, never()).post();
		verify(this.webClient.post(), never()).uri("auth/token/revoke-self");
		verify(this.listener).onAuthenticationEvent(any(AfterLoginEvent.class));
		verifyNoMoreInteractions(this.listener);
	}

	@Test
	void shouldNotRevokeBatchTokenOnDestroy() {

		LoginToken batchToken = LoginToken.builder().token("login").type("batch").build();

		mockToken(batchToken);

		this.sessionManager.setTokenSelfLookupEnabled(false);
		this.sessionManager.renewToken() //
			.as(StepVerifier::create) //
			.expectNextCount(1) //
			.verifyComplete();
		this.sessionManager.destroy();

		verify(this.webClient, never()).post();
		verify(this.webClient.post(), never()).uri("auth/token/revoke-self");
		verify(this.listener).onAuthenticationEvent(any(AfterLoginEvent.class));
		verifyNoMoreInteractions(this.listener);
	}

	@Test
	void shouldNotThrowExceptionsOnRevokeErrors() {

		mockToken(LoginToken.of("login"));

		when(this.responseSpec.bodyToMono((Class) any()))
			.thenReturn(Mono.error(new WebClientResponseException("forbidden", 403, "Forbidden", null, null, null)));

		this.sessionManager.renewToken() //
			.as(StepVerifier::create) //
			.expectNextCount(1) //
			.verifyComplete();
		this.sessionManager.destroy();

		verify(this.requestBodyUriSpec).uri("auth/token/revoke-self");
	}

	@Test
	void shouldScheduleTokenRenewal() {

		mockToken(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));

		this.sessionManager.getSessionToken() //
			.as(StepVerifier::create) //
			.expectNextCount(1) //
			.verifyComplete();

		verify(this.taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
	}

	@Test
	void shouldRunTokenRenewal() {

		mockToken(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

		VaultResponse vaultResponse = new VaultResponse();
		Map<String, Object> auth = new HashMap<>();
		auth.put("client_token", "login");
		auth.put("ttl", 100);
		vaultResponse.setAuth(auth);

		when(this.responseSpec.bodyToMono(VaultResponse.class)).thenReturn(Mono.just(vaultResponse));

		this.sessionManager.getSessionToken() //
			.as(StepVerifier::create) //
			.expectNextCount(1) //
			.verifyComplete();

		verify(this.taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));

		runnableCaptor.getValue().run();

		verify(this.webClient).post();
		verify(this.webClient.post()).uri("auth/token/renew-self");

		verify(this.tokenSupplier, times(1)).getVaultToken();
		verify(this.listener).onAuthenticationEvent(any(BeforeLoginTokenRenewedEvent.class));
		verify(this.listener).onAuthenticationEvent(any(AfterLoginTokenRenewedEvent.class));
	}

	@Test
	void shouldReScheduleTokenRenewalAfterSuccessfulRenewal() {

		mockToken(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));

		when(this.responseSpec.bodyToMono(VaultResponse.class))
			.thenReturn(Mono.just(fromToken(LoginToken.of("foo".toCharArray(), Duration.ofSeconds(10)))));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

		this.sessionManager.getSessionToken() //
			.as(StepVerifier::create) //
			.expectNextCount(1) //
			.verifyComplete();
		verify(this.taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));

		runnableCaptor.getValue().run();

		verify(this.taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
	}

	@Test
	void shouldNotScheduleRenewalIfRenewalTtlExceedsThreshold() {

		mockToken(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		when(this.responseSpec.bodyToMono(VaultResponse.class))
			.thenReturn(Mono.just(fromToken(LoginToken.of("foo".toCharArray(), Duration.ofSeconds(2)))));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

		this.sessionManager.getSessionToken().as(StepVerifier::create).expectNextCount(1).verifyComplete();
		verify(this.taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));

		runnableCaptor.getValue().run();

		verify(this.taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
	}

	@Test
	void shouldReLoginIfRenewalTtlExceedsThreshold() {

		when(this.tokenSupplier.getVaultToken()).thenReturn(
				Mono.just(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5))),
				Mono.just(LoginToken.renewable("bar".toCharArray(), Duration.ofSeconds(5))));
		when(this.responseSpec.bodyToMono(VaultResponse.class))
			.thenReturn(Mono.just(fromToken(LoginToken.of("foo".toCharArray(), Duration.ofSeconds(2)))));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
		this.sessionManager.getSessionToken() //
			.as(StepVerifier::create) //
			.expectNextCount(1) //
			.verifyComplete();
		verify(this.taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
		runnableCaptor.getValue().run();

		this.sessionManager.getSessionToken()
			.as(StepVerifier::create)
			.expectNext(LoginToken.renewable("bar".toCharArray(), Duration.ofSeconds(5)))
			.verifyComplete();

		verify(this.tokenSupplier, times(2)).getVaultToken();
		verify(this.listener, times(2)).onAuthenticationEvent(any(AfterLoginEvent.class));
		verify(this.listener).onAuthenticationEvent(any(LoginTokenExpiredEvent.class));
	}

	@Test
	void shouldReLoginIfRenewFails() {

		when(this.tokenSupplier.getVaultToken()).thenReturn(
				Mono.just(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5))),
				Mono.just(LoginToken.renewable("bar".toCharArray(), Duration.ofSeconds(5))));
		when(this.responseSpec.bodyToMono(VaultResponse.class)).thenReturn(Mono.error(new RuntimeException("foo")));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
		this.sessionManager.getSessionToken() //
			.as(StepVerifier::create) //
			.expectNextCount(1) //
			.verifyComplete();
		verify(this.taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
		runnableCaptor.getValue().run();

		this.sessionManager.getSessionToken()
			.as(StepVerifier::create)
			.expectNext(LoginToken.renewable("bar".toCharArray(), Duration.ofSeconds(5)))
			.verifyComplete();

		verify(this.tokenSupplier, times(2)).getVaultToken();
	}

	@Test
	void shouldRetainTokenAfterRenewalFailure() {

		when(this.tokenSupplier.getVaultToken()).thenReturn(
				Mono.just(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5))),
				Mono.just(LoginToken.renewable("bar".toCharArray(), Duration.ofSeconds(5))));
		when(this.responseSpec.bodyToMono(VaultResponse.class)).thenReturn(Mono.error(new RuntimeException("foo")));
		this.sessionManager.setLeaseStrategy(LeaseStrategy.retainOnError());

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
		this.sessionManager.getSessionToken() //
			.as(StepVerifier::create) //
			.expectNextCount(1) //
			.verifyComplete();
		verify(this.taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
		runnableCaptor.getValue().run();

		this.sessionManager.getSessionToken()
			.as(StepVerifier::create)
			.expectNext(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)))
			.verifyComplete();

		verify(this.tokenSupplier).getVaultToken();
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

	private void mockToken(VaultToken token) {
		when(this.tokenSupplier.getVaultToken()).thenReturn(Mono.just(token));
	}

}
