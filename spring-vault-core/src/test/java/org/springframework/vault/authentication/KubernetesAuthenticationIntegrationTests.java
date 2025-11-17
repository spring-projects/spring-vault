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

import java.io.File;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.TestVaultClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.vault.util.Settings.*;

/**
 * Integration tests for {@link KubernetesAuthentication}.
 *
 * @author Michal Budzyn
 */
class KubernetesAuthenticationIntegrationTests extends KubernetesAuthenticationIntegrationTestBase {

	@Test
	void shouldLoginSuccessfully() {

		File tokenFile = new File(findWorkDir(), "minikube/hello-minikube-token");

		KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions.builder()
				.role("my-role")
				.jwtSupplier(new KubernetesServiceAccountTokenFile(tokenFile))
				.build();

		VaultClient client = TestVaultClient.create();

		KubernetesAuthentication authentication = new KubernetesAuthentication(options, client);
		VaultToken login = authentication.login();

		assertThat(login.getToken()).isNotEmpty();
	}

	@Test
	void loginShouldFailBadRole() {

		File tokenFile = new File(findWorkDir(), "minikube/hello-minikube-token");

		KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions.builder()
				.role("wrong")
				.jwtSupplier(new KubernetesServiceAccountTokenFile(tokenFile))
				.build();

		VaultClient client = TestVaultClient.create();

		assertThatExceptionOfType(VaultException.class)
				.isThrownBy(() -> new KubernetesAuthentication(options, client).login());
	}

	@Test
	void loginShouldFailBadToken() {

		ClassPathResource tokenResource = new ClassPathResource("kube-jwt-token");

		KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions.builder()
				.role("my-role")
				.jwtSupplier(new KubernetesServiceAccountTokenFile(tokenResource))
				.build();

		VaultClient client = TestVaultClient.create();

		assertThatExceptionOfType(VaultException.class)
				.isThrownBy(() -> new KubernetesAuthentication(options, client).login());
	}

}
