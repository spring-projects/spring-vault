/*
 * Copyright 2016-2017 the original author or authors.
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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import org.assertj.core.util.Files;
import org.junit.Before;
import org.junit.Test;

import org.springframework.core.NestedRuntimeException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.config.ClientHttpRequestFactoryFactory;
import org.springframework.vault.core.RestOperationsCallback;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.SslConfiguration.KeyStoreConfiguration;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestRestTemplateFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.vault.util.Settings.createSslConfiguration;
import static org.springframework.vault.util.Settings.findWorkDir;

/**
 * Integration tests for {@link ClientCertificateAuthentication}.
 *
 * @author Mark Paluch
 */
public class ClientCertificateAuthenticationIntegrationTests extends
		IntegrationTestSupport {

	@Before
	public void before() {

		if (!prepare().hasAuth("cert")) {
			prepare().mountAuth("cert");
		}

		prepare().getVaultOperations().doWithSession(
				(RestOperationsCallback<Object>) restOperations -> {
					File workDir = findWorkDir();

					String certificate = Files.contentOf(new File(workDir,
							"ca/certs/client.cert.pem"), StandardCharsets.US_ASCII);

					return restOperations.postForEntity("auth/cert/certs/my-role",
							Collections.singletonMap("certificate", certificate),
							Map.class);
				});
	}

	@Test
	public void shouldLoginSuccessfully() {

		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory
				.create(new ClientOptions(), prepareCertAuthenticationMethod());

		RestTemplate restTemplate = VaultClients.createRestTemplate(
				TestRestTemplateFactory.TEST_VAULT_ENDPOINT, clientHttpRequestFactory);
		ClientCertificateAuthentication authentication = new ClientCertificateAuthentication(
				restTemplate);
		VaultToken login = authentication.login();

		assertThat(login.getToken()).isNotEmpty();
	}

	// Compatibility for Vault 0.6.0 and below. Vault 0.6.1 fixed that issue and we
	// receive a VaultException here.
	@Test(expected = NestedRuntimeException.class)
	public void loginShouldFail() {

		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory
				.create(new ClientOptions(), Settings.createSslConfiguration());
		RestTemplate restTemplate = VaultClients.createRestTemplate(
				TestRestTemplateFactory.TEST_VAULT_ENDPOINT, clientHttpRequestFactory);

		new ClientCertificateAuthentication(restTemplate).login();
	}

	@Test
	public void authenticationStepsShouldLoginSuccessfully() {

		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory
				.create(new ClientOptions(), prepareCertAuthenticationMethod());

		RestTemplate restTemplate = VaultClients.createRestTemplate(
				TestRestTemplateFactory.TEST_VAULT_ENDPOINT, clientHttpRequestFactory);
		ClientCertificateAuthentication authentication = new ClientCertificateAuthentication(
				restTemplate);

		AuthenticationStepsExecutor executor = new AuthenticationStepsExecutor(
				authentication.getAuthenticationSteps(), restTemplate);

		VaultToken login = executor.login();

		assertThat(login.getToken()).isNotEmpty();
	}

	// Compatibility for Vault 0.6.0 and below. Vault 0.6.1 fixed that issue and we
	// receive a VaultException here.
	@Test(expected = NestedRuntimeException.class)
	public void authenticationStepsLoginShouldFail() {

		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory
				.create(new ClientOptions(), Settings.createSslConfiguration());
		RestTemplate restTemplate = VaultClients.createRestTemplate(
				TestRestTemplateFactory.TEST_VAULT_ENDPOINT, clientHttpRequestFactory);

		AuthenticationSteps steps = new ClientCertificateAuthentication(restTemplate)
				.getAuthenticationSteps();

		new AuthenticationStepsExecutor(steps, restTemplate).login();
	}

	private SslConfiguration prepareCertAuthenticationMethod() {

		SslConfiguration original = createSslConfiguration();

		return new SslConfiguration(new KeyStoreConfiguration(new FileSystemResource(
				new File(findWorkDir(), "client-cert.jks")), "changeit".toCharArray(),
				null), original.getTrustStoreConfiguration());
	}
}
