/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReactiveLifecycleAwareSessionManager}.
 *
 * @author Mark Paluch
 */
@RunWith(MockitoJUnitRunner.class)
public class ReactiveLifecycleAwareSessionManagerUnitTests {

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

	private ReactiveLifecycleAwareSessionManager sessionManager;

	@Before
	public void before() {

		// POST
		when(webClient.post()).thenReturn(requestBodyUriSpec);
		when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
		when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
		when(requestBodySpec.retrieve()).thenReturn(responseSpec);

		// GET
		when(webClient.get()).thenReturn(requestHeadersUriSpec);
		when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.headers(any())).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

		sessionManager = new ReactiveLifecycleAwareSessionManager(tokenSupplier,
				taskScheduler, webClient);
	}

	@Test
	public void shouldObtainTokenFromClientAuthentication() {

		mockToken(LoginToken.of("login"));

		sessionManager.getSessionToken().as(StepVerifier::create)
				.expectNext(LoginToken.of("login")).verifyComplete();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void shouldSelfLookupToken() {

		VaultResponse vaultResponse = new VaultResponse();
		vaultResponse.setData(Collections.singletonMap("ttl", 100));

		mockToken(VaultToken.of("login"));

		when(responseSpec.bodyToMono((Class) any())).thenReturn(Mono.just(vaultResponse));

		sessionManager
				.getSessionToken()
				.as(StepVerifier::create)
				.assertNext(
						it -> {

							LoginToken sessionToken = (LoginToken) it;
							assertThat(sessionToken.getLeaseDuration()).isEqualTo(
									Duration.ofSeconds(100));
						}).verifyComplete();

		verify(webClient.get()).uri("auth/token/lookup-self");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void shouldContinueIfSelfLookupFails() {

		VaultResponse vaultResponse = new VaultResponse();
		vaultResponse.setData(Collections.singletonMap("ttl", 100));

		mockToken(VaultToken.of("login"));

		when(responseSpec.bodyToMono((Class) any())).thenReturn(
				Mono.error(new WebClientResponseException("forbidden", 403, "Forbidden",
						null, null, null)));

		sessionManager.getSessionToken().as(StepVerifier::create).assertNext(it -> {
			assertThat(it).isExactlyInstanceOf(VaultToken.class);
		}).verifyComplete();
	}

	@Test
	public void shouldRevokeLoginTokenOnDestroy() {

		mockToken(VaultToken.of("login"));

		sessionManager.setTokenSelfLookupEnabled(false);
		sessionManager.renewToken().as(StepVerifier::create).expectNextCount(1)
				.verifyComplete();
		sessionManager.destroy();

		verify(webClient, never()).post();
		verify(webClient.post(), never()).uri("auth/token/revoke-self");
	}

	@Test
	public void shouldNotThrowExceptionsOnRevokeErrors() {

		mockToken(LoginToken.of("login"));

		when(responseSpec.bodyToMono((Class) any())).thenReturn(
				Mono.error(new WebClientResponseException("forbidden", 403, "Forbidden",
						null, null, null)));

		sessionManager.renewToken().as(StepVerifier::create).expectNextCount(1)
				.verifyComplete();
		sessionManager.destroy();

		verify(requestBodyUriSpec).uri("auth/token/revoke-self");
	}

	@Test
	public void shouldScheduleTokenRenewal() {

		mockToken(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));

		sessionManager.getSessionToken().as(StepVerifier::create).expectNextCount(1)
				.verifyComplete();

		verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
	}

	@Test
	public void shouldRunTokenRenewal() {

		mockToken(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

		VaultResponse vaultResponse = new VaultResponse();
		Map<String, Object> auth = new HashMap<>();
		auth.put("client_token", "login");
		auth.put("ttl", 100);
		vaultResponse.setAuth(auth);

		when(responseSpec.bodyToMono(VaultResponse.class)).thenReturn(
				Mono.just(vaultResponse));

		sessionManager.getSessionToken().as(StepVerifier::create).expectNextCount(1)
				.verifyComplete();

		verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));

		runnableCaptor.getValue().run();

		verify(webClient).post();
		verify(webClient.post()).uri("auth/token/renew-self");

		verify(tokenSupplier, times(1)).getVaultToken();
	}

	@Test
	public void shouldReScheduleTokenRenewalAfterSuccessfulRenewal() {

		mockToken(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));

		when(responseSpec.bodyToMono(VaultResponse.class)).thenReturn(
				Mono.just(fromToken(LoginToken.of("foo".toCharArray(),
						Duration.ofSeconds(10)))));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

		sessionManager.getSessionToken().as(StepVerifier::create).expectNextCount(1)
				.verifyComplete();
		verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));

		runnableCaptor.getValue().run();

		verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
	}

	@Test
	public void shouldNotScheduleRenewalIfRenewalTtlExceedsThreshold() {

		mockToken(LoginToken.renewable("login".toCharArray(), Duration.ofSeconds(5)));
		when(responseSpec.bodyToMono(VaultResponse.class)).thenReturn(
				Mono.just(fromToken(LoginToken.of("foo".toCharArray(),
						Duration.ofSeconds(2)))));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

		sessionManager.getSessionToken().as(StepVerifier::create).expectNextCount(1)
				.verifyComplete();
		verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));

		runnableCaptor.getValue().run();

		verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
	}

	@Test
	public void shouldReLoginIfRenewalTtlExceedsThreshold() {

		when(tokenSupplier.getVaultToken())
				.thenReturn(
						Mono.just(LoginToken.renewable("login".toCharArray(),
								Duration.ofSeconds(5))),
						Mono.just(LoginToken.renewable("bar".toCharArray(),
								Duration.ofSeconds(5))));
		when(responseSpec.bodyToMono(VaultResponse.class)).thenReturn(
				Mono.just(fromToken(LoginToken.of("foo".toCharArray(),
						Duration.ofSeconds(2)))));

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
		sessionManager.getSessionToken().as(StepVerifier::create).expectNextCount(1)
				.verifyComplete();
		verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
		runnableCaptor.getValue().run();

		sessionManager
				.getSessionToken()
				.as(StepVerifier::create)
				.expectNext(
						LoginToken.renewable("bar".toCharArray(), Duration.ofSeconds(5)))
				.verifyComplete();

		verify(tokenSupplier, times(2)).getVaultToken();
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
		when(tokenSupplier.getVaultToken()).thenReturn(Mono.just(token));
	}
}
