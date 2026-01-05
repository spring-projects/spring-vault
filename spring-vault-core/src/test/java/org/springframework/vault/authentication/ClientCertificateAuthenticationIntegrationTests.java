/*
 * Copyright 2016-present the original author or authors.
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
import org.springframework.vault.client.ClientHttpRequestFactoryFactory;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestVaultClient;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ClientCertificateAuthentication}.
 *
 * @author Mark Paluch
 * @author Andy Lintner
 */
class ClientCertificateAuthenticationIntegrationTests extends ClientCertificateAuthenticationIntegrationTestBase {

	@Test
	void shouldLoginSuccessfully() {

		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory.create(new ClientOptions(),
				prepareCertAuthenticationMethod());

		ClientCertificateAuthentication authentication = new ClientCertificateAuthentication(
				TestVaultClient.create(clientHttpRequestFactory));
		VaultToken login = authentication.login();

		assertThat(login.getToken()).isNotEmpty();
	}

	@Test
	void shouldSelectKey() {

		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory.create(new ClientOptions(),
				prepareCertAuthenticationMethod(SslConfiguration.KeyConfiguration.of("changeit".toCharArray(), "1")));

		ClientCertificateAuthentication authentication = new ClientCertificateAuthentication(
				TestVaultClient.create(clientHttpRequestFactory));
		VaultToken login = authentication.login();

		assertThat(login.getToken()).isNotEmpty();
	}

	@Test
	void shouldSelectInvalidKey() {

		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory.create(new ClientOptions(),
				prepareCertAuthenticationMethod(SslConfiguration.KeyConfiguration.of("changeit".toCharArray(), "2")));

		VaultClient client = TestVaultClient.create(clientHttpRequestFactory);
		ClientCertificateAuthentication authentication = new ClientCertificateAuthentication(client);

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

		VaultClient client = TestVaultClient.create(clientHttpRequestFactory);
		ClientCertificateAuthentication authentication = new ClientCertificateAuthentication(
				ClientCertificateAuthenticationOptions.builder().role("my-default-role").build(), client);
		VaultToken login = authentication.login();

		assertThat(login.getToken()).isNotEmpty();
		assertThatPolicies(login).contains("cert-auth1") //
				.doesNotContain("cert-auth2");
	}

	@Test
	void shouldSelectRoleTwo() {
		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory.create(new ClientOptions(),
				prepareCertAuthenticationMethod());

		VaultClient client = TestVaultClient.create(clientHttpRequestFactory);
		ClientCertificateAuthentication authentication = new ClientCertificateAuthentication(
				ClientCertificateAuthenticationOptions.builder().role("my-alternate-role").build(), client);
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
		VaultClient client = TestVaultClient.create(clientHttpRequestFactory);

		assertThatExceptionOfType(NestedRuntimeException.class)
				.isThrownBy(() -> new ClientCertificateAuthentication(client).login());
	}

}
