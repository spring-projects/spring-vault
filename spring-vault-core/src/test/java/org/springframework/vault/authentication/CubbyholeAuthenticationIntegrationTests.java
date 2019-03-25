/*
 * Copyright 2016-2018 the original author or authors.
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

import java.util.Map;

import org.junit.Test;

import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestRestTemplateFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Integration tests for {@link CubbyholeAuthentication}.
 *
 * @author Mark Paluch
 */
public class CubbyholeAuthenticationIntegrationTests extends
		CubbyholeAuthenticationIntegrationTestBase {

	@Test
	public void shouldCreateWrappedToken() {

		Map<String, String> wrapInfo = prepareWrappedToken();

		String initialToken = wrapInfo.get("token");

		CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
				.initialToken(VaultToken.of(initialToken)).wrapped().build();
		RestTemplate restTemplate = TestRestTemplateFactory.create(Settings
				.createSslConfiguration());

		CubbyholeAuthentication authentication = new CubbyholeAuthentication(options,
				restTemplate);
		VaultToken login = authentication.login();
		assertThat(login.getToken()).doesNotContain(Settings.token().getToken());
	}

	@Test
	public void loginShouldFail() {

		CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
				.initialToken(VaultToken.of("Hello")).wrapped().build();

		RestTemplate restTemplate = TestRestTemplateFactory.create(Settings
				.createSslConfiguration());
		CubbyholeAuthentication authentication = new CubbyholeAuthentication(options,
				restTemplate);

		try {
			authentication.login();
			fail("Missing VaultException");
		}
		catch (VaultException e) {
			assertThat(e).hasMessageContaining("Cannot retrieve Token from Cubbyhole")
					.hasMessageContaining("permission denied");
		}
	}

}
