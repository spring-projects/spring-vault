/*
 * Copyright 2017-2024 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.util.TestWebClientFactory;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.test.StepVerifier;

/**
 * Integration tests for {@link ClientCertificateAuthentication} using
 * {@link AuthenticationStepsOperator}.
 *
 * @author Mark Paluch
 */
class ClientCertificateAuthenticationOperatorIntegrationTests
		extends ClientCertificateAuthenticationIntegrationTestBase {

	@Test
	void authenticationStepsShouldLoginSuccessfully() {

		WebClient webClient = TestWebClientFactory.create(prepareCertAuthenticationMethod());

		AuthenticationStepsOperator operator = new AuthenticationStepsOperator(
				ClientCertificateAuthentication.createAuthenticationSteps(), webClient);

		operator.getVaultToken() //
			.as(StepVerifier::create) //
			.expectNextCount(1) //
			.verifyComplete();
	}

	@Test
	void shouldSelectKey() {

		WebClient webClient = TestWebClientFactory.create(
				prepareCertAuthenticationMethod(SslConfiguration.KeyConfiguration.of("changeit".toCharArray(), "1")));

		AuthenticationStepsOperator operator = new AuthenticationStepsOperator(
				ClientCertificateAuthentication.createAuthenticationSteps(), webClient);

		operator.getVaultToken() //
			.as(StepVerifier::create) //
			.expectNextCount(1) //
			.verifyComplete();
	}

	@Test
	void shouldSelectInvalidKey() {

		WebClient webClient = TestWebClientFactory.create(
				prepareCertAuthenticationMethod(SslConfiguration.KeyConfiguration.of("changeit".toCharArray(), "2")));

		AuthenticationStepsOperator operator = new AuthenticationStepsOperator(
				ClientCertificateAuthentication.createAuthenticationSteps(), webClient);

		operator.getVaultToken() //
			.as(StepVerifier::create) //
			.verifyError(VaultLoginException.class);
	}

	@Test
	void shouldSelectRoleOne() {

		WebClient webClient = TestWebClientFactory.create(prepareCertAuthenticationMethod());

		AuthenticationStepsOperator operator = new AuthenticationStepsOperator(
				ClientCertificateAuthentication.createAuthenticationSteps(
						ClientCertificateAuthenticationOptions.builder().role("my-default-role").build()),
				webClient);

		operator.getVaultToken() //
			.as(StepVerifier::create) //
			.assertNext(token -> assertThatPolicies(token).contains("cert-auth1") //
				.doesNotContain("cert-auth2")) //
			.verifyComplete();
	}

	@Test
	void shouldSelectRoleTwo() {

		WebClient webClient = TestWebClientFactory.create(prepareCertAuthenticationMethod());

		AuthenticationStepsOperator operator = new AuthenticationStepsOperator(
				ClientCertificateAuthentication.createAuthenticationSteps(
						ClientCertificateAuthenticationOptions.builder().role("my-alternate-role").build()),
				webClient);

		operator.getVaultToken() //
			.as(StepVerifier::create) //
			.assertNext(token -> assertThatPolicies(token).contains("cert-auth2") //
				.doesNotContain("cert-auth1")) //
			.verifyComplete();
	}

	@Test
	void shouldProvideInvalidKeyPassword() {
		assertThatIllegalStateException().isThrownBy(() -> TestWebClientFactory
			.create(prepareCertAuthenticationMethod(SslConfiguration.KeyConfiguration.of("wrong".toCharArray(), "1"))));
	}

}
