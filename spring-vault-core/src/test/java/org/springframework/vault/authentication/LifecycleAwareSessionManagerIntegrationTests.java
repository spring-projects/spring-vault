/*
 * Copyright 2016-2025 the original author or authors.
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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.vault.core.VaultTokenOperations;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.VaultTokenRequest;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.web.client.HttpStatusCodeException;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link LifecycleAwareSessionManager}.
 *
 * @author Mark Paluch
 */
class LifecycleAwareSessionManagerIntegrationTests extends IntegrationTestSupport {

	private ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();

	@BeforeEach
	void before() {
		this.taskScheduler.afterPropertiesSet();
	}

	@AfterEach
	void tearDown() {
		this.taskScheduler.destroy();
	}

	@Test
	void shouldLogin() {

		LoginToken loginToken = createLoginToken();
		TokenAuthentication tokenAuthentication = new TokenAuthentication(loginToken);

		LifecycleAwareSessionManager sessionManager = new LifecycleAwareSessionManager(tokenAuthentication,
				this.taskScheduler, prepare().getRestTemplate());

		assertThat(sessionManager.getSessionToken()).isSameAs(loginToken);
	}

	// Expect no exception to be thrown.
	@Test
	void shouldRenewToken() {

		VaultTokenOperations tokenOperations = prepare().getVaultOperations().opsForToken();

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder() //
			.renewable()
			.ttl(1, TimeUnit.HOURS) //
			.explicitMaxTtl(10, TimeUnit.HOURS) //
			.build();

		VaultToken token = tokenOperations.create(tokenRequest).getToken();

		TokenAuthentication tokenAuthentication = new TokenAuthentication(
				LoginToken.renewable(token.getToken().toCharArray(), Duration.ZERO));

		final AtomicInteger counter = new AtomicInteger();
		LifecycleAwareSessionManager sessionManager = new LifecycleAwareSessionManager(tokenAuthentication,
				this.taskScheduler, prepare().getRestTemplate()) {
			@Override
			public VaultToken getSessionToken() {

				if (counter.getAndIncrement() > 0) {
					throw new IllegalStateException();
				}
				return super.getSessionToken();
			}
		};

		sessionManager.getSessionToken();
		sessionManager.renewToken();
	}

	@Test
	void shouldRevokeToken() {

		final LoginToken loginToken = createLoginToken();
		TokenAuthentication tokenAuthentication = new TokenAuthentication(loginToken);

		LifecycleAwareSessionManager sessionManager = new LifecycleAwareSessionManager(tokenAuthentication,
				this.taskScheduler, prepare().getRestTemplate());

		sessionManager.getSessionToken();
		sessionManager.revoke();

		prepare().getVaultOperations().doWithSession(restOperations -> {

			try {
				restOperations.getForEntity("auth/token/lookup/{token}", Map.class, loginToken.toCharArray());
				fail("Missing HttpStatusCodeException");
			}
			catch (HttpStatusCodeException e) {
				// Compatibility across Vault versions.
				assertThat(e.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
			}

			return null;
		});
	}

	@Test
	void shouldRevokeOnDisposal() {

		final LoginToken loginToken = createLoginToken();
		TokenAuthentication tokenAuthentication = new TokenAuthentication(loginToken);

		LifecycleAwareSessionManager sessionManager = new LifecycleAwareSessionManager(tokenAuthentication,
				this.taskScheduler, prepare().getRestTemplate());

		sessionManager.getSessionToken();
		sessionManager.destroy();

		prepare().getVaultOperations().doWithSession(restOperations -> {

			try {
				restOperations.getForEntity("auth/token/lookup/{token}", Map.class, loginToken.toCharArray());
				fail("Missing HttpStatusCodeException");
			}
			catch (HttpStatusCodeException e) {
				// Compatibility across Vault versions.
				assertThat(e.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
			}

			return null;
		});
	}

	private LoginToken createLoginToken() {

		VaultTokenOperations tokenOperations = prepare().getVaultOperations().opsForToken();
		VaultToken token = tokenOperations.createOrphan().getToken();

		return LoginToken.of(token.getToken());
	}

}
