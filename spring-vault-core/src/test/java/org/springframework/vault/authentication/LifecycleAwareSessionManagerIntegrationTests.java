/*
 * Copyright 2016 the original author or authors.
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

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.vault.core.RestOperationsCallback;
import org.springframework.vault.core.VaultTokenOperations;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.VaultTokenRequest;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Integration tests for {@link LifecycleAwareSessionManager}.
 * 
 * @author Mark Paluch
 */
public class LifecycleAwareSessionManagerIntegrationTests extends IntegrationTestSupport {

	private AsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor();

	@Test
	public void shouldLogin() {

		LoginToken loginToken = createLoginToken();
		TokenAuthentication tokenAuthentication = new TokenAuthentication(loginToken);

		LifecycleAwareSessionManager sessionManager = new LifecycleAwareSessionManager(
				tokenAuthentication, taskExecutor, prepare().getVaultClient());

		assertThat(sessionManager.getSessionToken()).isSameAs(loginToken);
	}

	// Expect no exception to be thrown.
	@Test
	public void shouldRenewToken() {

		VaultTokenOperations tokenOperations = prepare().getVaultOperations()
				.opsForToken();

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder() //
				.renewable().ttl(1, TimeUnit.HOURS) //
				.explicitMaxTtl(10, TimeUnit.HOURS) //
				.build();

		VaultToken token = tokenOperations.create(tokenRequest).getToken();

		TokenAuthentication tokenAuthentication = new TokenAuthentication(
				LoginToken.renewable(token.getToken(), 0));

		final AtomicInteger counter = new AtomicInteger();
		LifecycleAwareSessionManager sessionManager = new LifecycleAwareSessionManager(
				tokenAuthentication, taskExecutor, prepare().getVaultClient()) {
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
	public void shouldRevokeOnDisposal() {

		final LoginToken loginToken = createLoginToken();
		TokenAuthentication tokenAuthentication = new TokenAuthentication(loginToken);

		LifecycleAwareSessionManager sessionManager = new LifecycleAwareSessionManager(
				tokenAuthentication, taskExecutor, prepare().getVaultClient());

		sessionManager.getSessionToken();
		sessionManager.destroy();

		prepare().getVaultOperations().doWithSession(
				new RestOperationsCallback<Object>() {
					@Override
					public Object doWithRestOperations(RestOperations restOperations) {

						try {
							restOperations.getForEntity(
									String.format("auth/token/lookup/%s",
											loginToken.getToken()), Map.class);

							fail("Missing RestClientException");
						}
						catch (HttpStatusCodeException e) {
							// Compatibility across Vault versions.
							assertThat(e.getStatusCode()).isIn(HttpStatus.BAD_REQUEST,
									HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
						}

						return null;
					}
				});
	}

	private LoginToken createLoginToken() {

		VaultTokenOperations tokenOperations = prepare().getVaultOperations()
				.opsForToken();
		VaultToken token = tokenOperations.createOrphan().getToken();

		return LoginToken.of(token.getToken());
	}
}
