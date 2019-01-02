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

import java.util.Map;

import org.junit.Test;
import reactor.test.StepVerifier;

import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestWebClientFactory;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link CubbyholeAuthentication} using
 * {@link AuthenticationStepsOperator}.
 *
 * @author Mark Paluch
 */
public class CubbyholeAuthenticationOperatorIntegrationTests extends
		CubbyholeAuthenticationIntegrationTestBase {

	WebClient webClient = TestWebClientFactory.create(Settings.createSslConfiguration());

	@Test
	public void authenticationStepsShouldCreateWrappedToken() {

		Map<String, String> wrapInfo = prepareWrappedToken();

		String initialToken = wrapInfo.get("token");

		CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
				.initialToken(VaultToken.of(initialToken)).wrapped().build();

		AuthenticationStepsOperator operator = new AuthenticationStepsOperator(
				CubbyholeAuthentication.createAuthenticationSteps(options), webClient);

		StepVerifier.create(operator.getVaultToken()).consumeNextWith(actual -> {

			assertThat(actual).isNotEqualTo(Settings.token().getToken()).isNotNull();
		}).verifyComplete();
	}
}
