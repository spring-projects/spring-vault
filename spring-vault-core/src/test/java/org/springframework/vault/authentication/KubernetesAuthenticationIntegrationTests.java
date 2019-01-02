/*
 * Copyright 2017-2019 the original author or authors.
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

import org.junit.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestRestTemplateFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.vault.util.Settings.findWorkDir;

/**
 * Integration tests for {@link KubernetesAuthentication}.
 *
 * @author Michal Budzyn
 */
public class KubernetesAuthenticationIntegrationTests extends
		KubernetesAuthenticationIntegrationTestBase {

	@Test
	public void shouldLoginSuccessfully() {

		File tokenFile = new File(findWorkDir(), "minikube/hello-minikube-token");

		KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions
				.builder().role("my-role")
				.jwtSupplier(new KubernetesServiceAccountTokenFile(tokenFile))
				.build();

		RestTemplate restTemplate = TestRestTemplateFactory
				.create(Settings.createSslConfiguration());

		KubernetesAuthentication authentication = new KubernetesAuthentication(options,
				restTemplate);
		VaultToken login = authentication.login();

		assertThat(login.getToken()).isNotEmpty();
	}

	@Test(expected = VaultException.class)
	public void loginShouldFailBadRole() {

		File tokenFile = new File(findWorkDir(), "minikube/hello-minikube-token");

		KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions
				.builder().role("wrong")
				.jwtSupplier(new KubernetesServiceAccountTokenFile(tokenFile))
				.build();

		RestTemplate restTemplate = TestRestTemplateFactory
				.create(Settings.createSslConfiguration());

		new KubernetesAuthentication(options, restTemplate).login();

	}

	@Test(expected = VaultException.class)
	public void loginShouldFailBadToken() {

		ClassPathResource tokenResource = new ClassPathResource("kube-jwt-token");

		KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions
				.builder()
				.role("my-role")
				.jwtSupplier(new KubernetesServiceAccountTokenFile(tokenResource))
				.build();

		RestTemplate restTemplate = TestRestTemplateFactory
				.create(Settings.createSslConfiguration());

		new KubernetesAuthentication(options, restTemplate).login();
	}
}
