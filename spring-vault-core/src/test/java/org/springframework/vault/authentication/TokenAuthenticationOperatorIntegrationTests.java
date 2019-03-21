/*
 * Copyright 2017-2019 the original author or authors.
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

import org.junit.Test;
import reactor.test.StepVerifier;

import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.VaultTokenRequest;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestWebClientFactory;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TokenAuthentication} using
 * {@link AuthenticationStepsOperator}.
 *
 * @author Mark Paluch
 */
public class TokenAuthenticationOperatorIntegrationTests extends
		TokenAuthenticationIntegrationTestBase {

	WebClient webClient = TestWebClientFactory.create(Settings.createSslConfiguration());

	@Test
	public void shouldSelfLookup() {

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder()
				.ttl(Duration.ofSeconds(60)).renewable().numUses(1).build();

		VaultToken token = prepare().getVaultOperations().opsForToken()
				.create(tokenRequest).getToken();

		AuthenticationStepsOperator operator = new AuthenticationStepsOperator(
				TokenAuthentication.createAuthenticationSteps(token, true), webClient);

		StepVerifier
				.create(operator.getVaultToken())
				.consumeNextWith(
						actual -> {

							assertThat(actual).isInstanceOf(LoginToken.class);

							LoginToken loginToken = (LoginToken) actual;

							assertThat(loginToken.getLeaseDuration()).isBetween(
									Duration.ofSeconds(40), Duration.ofSeconds(60));
							assertThat(loginToken.isRenewable()).isTrue();

						}).verifyComplete();
	}

	@Test
	public void shouldFailDuringSelfLookup() {

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder()
				.ttl(Duration.ofSeconds(60)).renewable().numUses(1).build();

		VaultToken token = prepare().getVaultOperations().opsForToken()
				.create(tokenRequest).getToken();

		AuthenticationStepsOperator operator = new AuthenticationStepsOperator(
				TokenAuthentication.createAuthenticationSteps(token, true), webClient);

		// first usage
		StepVerifier.create(operator.getVaultToken()).expectNextCount(1).verifyComplete();

		StepVerifier.create(operator.getVaultToken()).expectError(VaultException.class)
				.verify();
	}
}
