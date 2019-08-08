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

import org.junit.jupiter.api.Test;

import org.springframework.core.NestedRuntimeException;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.config.ClientHttpRequestFactoryFactory;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestRestTemplateFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Integration tests for {@link ClientCertificateAuthentication} using
 * {@link AuthenticationStepsExecutor}.
 *
 * @author Mark Paluch
 */
class ClientCertificateAuthenticationStepsIntegrationTests
		extends ClientCertificateAuthenticationIntegrationTestBase {

	@Test
	void authenticationStepsShouldLoginSuccessfully() {

		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory
				.create(new ClientOptions(), prepareCertAuthenticationMethod());

		RestTemplate restTemplate = VaultClients.createRestTemplate(
				TestRestTemplateFactory.TEST_VAULT_ENDPOINT, clientHttpRequestFactory);

		AuthenticationStepsExecutor executor = new AuthenticationStepsExecutor(
				ClientCertificateAuthentication.createAuthenticationSteps(),
				restTemplate);

		VaultToken login = executor.login();

		assertThat(login.getToken()).isNotEmpty();
	}

	// Compatibility for Vault 0.6.0 and below. Vault 0.6.1 fixed that issue and we
	// receive a VaultException here.
	@Test
	void authenticationStepsLoginShouldFail() {

		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory
				.create(new ClientOptions(), Settings.createSslConfiguration());
		RestTemplate restTemplate = VaultClients.createRestTemplate(
				TestRestTemplateFactory.TEST_VAULT_ENDPOINT, clientHttpRequestFactory);

		assertThatExceptionOfType(NestedRuntimeException.class)
				.isThrownBy(() -> new AuthenticationStepsExecutor(
						ClientCertificateAuthentication.createAuthenticationSteps(),
						restTemplate).login());
	}
}
