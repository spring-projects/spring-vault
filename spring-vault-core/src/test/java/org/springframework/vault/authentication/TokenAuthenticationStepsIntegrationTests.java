/*
 * Copyright 2017-2025 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.VaultTokenRequest;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestRestTemplateFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link TokenAuthentication} using
 * {@link AuthenticationStepsExecutor}.
 *
 * @author Mark Paluch
 */
class TokenAuthenticationStepsIntegrationTests extends TokenAuthenticationIntegrationTestBase {

	RestTemplate restTemplate = TestRestTemplateFactory.create(Settings.createSslConfiguration());

	@Test
	void shouldSelfLookup() {

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder()
				.ttl(Duration.ofSeconds(60))
				.renewable()
				.numUses(1)
				.build();

		VaultToken token = prepare().getVaultOperations().opsForToken().create(tokenRequest).getToken();

		AuthenticationStepsExecutor operator = new AuthenticationStepsExecutor(
				TokenAuthentication.createAuthenticationSteps(token, true), this.restTemplate);

		VaultToken login = operator.login();
		assertThat(login).isInstanceOf(LoginToken.class);

		LoginToken loginToken = (LoginToken) login;

		assertThat(loginToken.getLeaseDuration()).isBetween(Duration.ofSeconds(40), Duration.ofSeconds(60));
		assertThat(loginToken.isRenewable()).isTrue();
	}

	@Test
	void shouldFailDuringSelfLookup() {

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder()
				.ttl(Duration.ofSeconds(60))
				.renewable()
				.numUses(1)
				.build();

		VaultToken token = prepare().getVaultOperations().opsForToken().create(tokenRequest).getToken();

		AuthenticationStepsExecutor operator = new AuthenticationStepsExecutor(
				TokenAuthentication.createAuthenticationSteps(token, true), this.restTemplate);

		operator.login();
		assertThatExceptionOfType(VaultException.class).isThrownBy(operator::login);
	}

}
