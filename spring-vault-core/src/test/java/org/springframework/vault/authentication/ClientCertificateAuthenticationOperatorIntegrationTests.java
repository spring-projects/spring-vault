/*
 * Copyright 2017 the original author or authors.
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

import org.junit.Test;
import reactor.test.StepVerifier;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.config.ClientHttpRequestFactoryFactory;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.util.TestRestTemplateFactory;
import org.springframework.vault.util.TestWebClientFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Integration tests for {@link ClientCertificateAuthentication} using
 * {@link AuthenticationStepsOperator}.
 *
 * @author Mark Paluch
 */
public class ClientCertificateAuthenticationOperatorIntegrationTests extends
		ClientCertificateAuthenticationIntegrationTestBase {

	@Test
	public void authenticationStepsShouldLoginSuccessfully() {

		WebClient webClient = TestWebClientFactory
				.create(prepareCertAuthenticationMethod());

		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory
				.create(new ClientOptions(), prepareCertAuthenticationMethod());

		RestTemplate restTemplate = VaultClients.createRestTemplate(
				TestRestTemplateFactory.TEST_VAULT_ENDPOINT, clientHttpRequestFactory);
		ClientCertificateAuthentication authentication = new ClientCertificateAuthentication(
				restTemplate);

		AuthenticationStepsOperator operator = new AuthenticationStepsOperator(
				authentication.getAuthenticationSteps(), webClient);

		StepVerifier.create(operator.getVaultToken()).expectNextCount(1).verifyComplete();
	}
}
