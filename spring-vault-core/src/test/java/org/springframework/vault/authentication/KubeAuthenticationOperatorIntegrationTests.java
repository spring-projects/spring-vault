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

import static org.springframework.vault.util.Settings.findWorkDir;

import java.io.File;

import org.junit.Ignore;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestWebClientFactory;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.test.StepVerifier;

/**
 * Integration tests for {@link KubeAuthentication} using
 * {@link AuthenticationStepsExecutor}.
 *
 * @author Michal Budzyn
 */
public class KubeAuthenticationOperatorIntegrationTests
		extends KubeAuthenticationIntegrationTestBase {

	WebClient webClient = TestWebClientFactory.create(Settings.createSslConfiguration());

	@Test
	@Ignore("TODO: Clarify integration testing with minikube")
	public void authenticationStepsShouldLoginSuccessfully() {

		File tokenFile = new File(findWorkDir(), "minikube/hello-minikube-token");

		KubeAuthenticationOptions options = KubeAuthenticationOptions.builder()
				.role("my-role").jwtSupplier(new KubeServiceAccountTokenFile(tokenFile))
				.build();

		AuthenticationStepsOperator supplier = new AuthenticationStepsOperator(
				KubeAuthentication.createAuthenticationSteps(options), webClient);

		StepVerifier.create(supplier.getVaultToken()).expectNextCount(1).verifyComplete();
	}

	@Test
	@Ignore("TODO: Clarify integration testing with minikube")
	public void authenticationStepsLoginShouldFailBadRole() {

		File tokenFile = new File(findWorkDir(), "minikube/hello-minikube-token");

		KubeAuthenticationOptions options = KubeAuthenticationOptions.builder()
				.role("wrong").jwtSupplier(new KubeServiceAccountTokenFile(tokenFile))
				.build();

		AuthenticationStepsOperator supplier = new AuthenticationStepsOperator(
				KubeAuthentication.createAuthenticationSteps(options), webClient);

		StepVerifier.create(supplier.getVaultToken()).expectError().verify();

	}

	@Test
	@Ignore("TODO: Clarify integration testing with minikube")
	public void authenticationStepsLoginShouldFailBadToken() {

		ClassPathResource tokenResource = new ClassPathResource("kube-jwt-token");

		KubeAuthenticationOptions options = KubeAuthenticationOptions.builder()
				.role("my-role")
				.jwtSupplier(new KubeServiceAccountTokenFile(tokenResource)).build();

		AuthenticationStepsOperator supplier = new AuthenticationStepsOperator(
				KubeAuthentication.createAuthenticationSteps(options), webClient);

		StepVerifier.create(supplier.getVaultToken()).expectError().verify();
	}
}
