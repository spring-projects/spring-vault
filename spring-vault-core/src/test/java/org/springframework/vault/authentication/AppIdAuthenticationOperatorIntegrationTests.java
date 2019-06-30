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
import reactor.test.StepVerifier;

import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestWebClientFactory;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Integration tests for {@link AppIdAuthentication} using
 * {@link AuthenticationStepsOperator}.
 *
 * @author Mark Paluch
 */
class AppIdAuthenticationOperatorIntegrationTests extends
		AppIdAuthenticationIntegrationTestBase {

	WebClient webClient = TestWebClientFactory.create(Settings.createSslConfiguration());

	@Test
	void authenticationStepsShouldLoginSuccessfully() {

		AppIdAuthenticationOptions options = AppIdAuthenticationOptions.builder()
				.appId("myapp") //
				.userIdMechanism(new StaticUserId("static-userid-value")) //
				.build();

		AuthenticationStepsOperator supplier = new AuthenticationStepsOperator(
				AppIdAuthentication.createAuthenticationSteps(options), webClient);

		StepVerifier.create(supplier.getVaultToken()).expectNextCount(1).verifyComplete();
	}

	@Test
	void authenticationStepsLoginShouldFail() {

		AppIdAuthenticationOptions options = AppIdAuthenticationOptions.builder()
				.appId("wrong") //
				.userIdMechanism(new StaticUserId("wrong")) //
				.build();

		AuthenticationStepsOperator supplier = new AuthenticationStepsOperator(
				AppIdAuthentication.createAuthenticationSteps(options), webClient);

		StepVerifier.create(supplier.getVaultToken()).expectError().verify();
	}
}
