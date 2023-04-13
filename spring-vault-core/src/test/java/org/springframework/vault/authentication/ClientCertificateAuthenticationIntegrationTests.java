/*
 * Copyright 2016-2022 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.core.NestedRuntimeException;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.vault.client.ClientHttpRequestFactoryFactory;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.support.*;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestRestTemplateFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Integration tests for {@link ClientCertificateAuthentication}.
 *
 * @author Mark Paluch
 */
class ClientCertificateAuthenticationIntegrationTests extends ClientCertificateAuthenticationIntegrationTestBase {

	@Test
	void shouldLoginSuccessfully() {

		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory.create(new ClientOptions(),
				prepareCertAuthenticationMethod());

		RestTemplate restTemplate = VaultClients.createRestTemplate(TestRestTemplateFactory.TEST_VAULT_ENDPOINT,
				clientHttpRequestFactory);
		ClientCertificateAuthentication authentication = new ClientCertificateAuthentication(restTemplate);
		VaultToken login = authentication.login();

		assertThat(login.getToken()).isNotEmpty();
	}

	@Test
	void shouldSelectKey() {

		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory.create(new ClientOptions(),
				prepareCertAuthenticationMethod(SslConfiguration.KeyConfiguration.of("changeit".toCharArray(), "1")));

		RestTemplate restTemplate = VaultClients.createRestTemplate(TestRestTemplateFactory.TEST_VAULT_ENDPOINT,
				clientHttpRequestFactory);
		ClientCertificateAuthentication authentication = new ClientCertificateAuthentication(restTemplate);
		VaultToken login = authentication.login();

		assertThat(login.getToken()).isNotEmpty();
	}

	@Test
	void shouldSelectInvalidKey() {

		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory.create(new ClientOptions(),
				prepareCertAuthenticationMethod(SslConfiguration.KeyConfiguration.of("changeit".toCharArray(), "2")));

		RestTemplate restTemplate = VaultClients.createRestTemplate(TestRestTemplateFactory.TEST_VAULT_ENDPOINT,
				clientHttpRequestFactory);
		ClientCertificateAuthentication authentication = new ClientCertificateAuthentication(restTemplate);

		assertThatExceptionOfType(NestedRuntimeException.class).isThrownBy(authentication::login);
	}

	@Test
	void shouldProvideInvalidKeyPassword() {

		assertThatIllegalStateException().isThrownBy(() -> ClientHttpRequestFactoryFactory.create(new ClientOptions(),
				prepareCertAuthenticationMethod(SslConfiguration.KeyConfiguration.of("wrong".toCharArray(), "1"))));
	}

	@Test
	void shouldSelectRoleOne() {
		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory.create(new ClientOptions(),
				prepareCertAuthenticationMethod());

		RestTemplate restTemplate = VaultClients.createRestTemplate(TestRestTemplateFactory.TEST_VAULT_ENDPOINT,
				clientHttpRequestFactory);
		ClientCertificateAuthentication authentication = new ClientCertificateAuthentication(
				ClientCertificateAuthenticationOptions.builder().name("my-default-role").build(), restTemplate);
		VaultToken login = authentication.login();

		assertThat(login.getToken()).isNotEmpty();
		assertThatPolicies(login).contains("cert-auth1") //
				.doesNotContain("cert-auth2");
	}

	@Test
	void shouldSelectRoleTwo() {
		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory.create(new ClientOptions(),
				prepareCertAuthenticationMethod());

		RestTemplate restTemplate = VaultClients.createRestTemplate(TestRestTemplateFactory.TEST_VAULT_ENDPOINT,
				clientHttpRequestFactory);
		ClientCertificateAuthentication authentication = new ClientCertificateAuthentication(
				ClientCertificateAuthenticationOptions.builder().name("my-alternate-role").build(), restTemplate);
		VaultToken login = authentication.login();

		assertThat(login.getToken()).isNotEmpty();
		assertThatPolicies(login).contains("cert-auth2") //
				.doesNotContain("cert-auth1");
	}

	// Compatibility for Vault 0.6.0 and below. Vault 0.6.1 fixed that issue and we
	// receive a VaultException here.
	@Test
	void loginShouldFail() {

		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory.create(new ClientOptions(),
				Settings.createSslConfiguration());
		RestTemplate restTemplate = VaultClients.createRestTemplate(TestRestTemplateFactory.TEST_VAULT_ENDPOINT,
				clientHttpRequestFactory);

		assertThatExceptionOfType(NestedRuntimeException.class)
				.isThrownBy(() -> new ClientCertificateAuthentication(restTemplate).login());
	}

}
