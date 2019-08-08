/*
 * Copyright 2016-2019 the original author or authors.
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

import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestRestTemplateFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Integration tests for {@link AppIdAuthentication}.
 *
 * @author Mark Paluch
 */
class AppIdAuthenticationIntegrationTests extends AppIdAuthenticationIntegrationTestBase {

	@Test
	void shouldLoginSuccessfully() {

		AppIdAuthenticationOptions options = AppIdAuthenticationOptions.builder()
				.appId("myapp") //
				.userIdMechanism(new StaticUserId("static-userid-value")) //
				.build();

		RestTemplate restTemplate = TestRestTemplateFactory
				.create(Settings.createSslConfiguration());

		AppIdAuthentication authentication = new AppIdAuthentication(options,
				restTemplate);
		VaultToken login = authentication.login();

		assertThat(login.getToken()).isNotEmpty();
	}

	@Test
	void loginShouldFail() {

		AppIdAuthenticationOptions options = AppIdAuthenticationOptions.builder()
				.appId("wrong") //
				.userIdMechanism(new StaticUserId("wrong")) //
				.build();

		RestTemplate restTemplate = TestRestTemplateFactory
				.create(Settings.createSslConfiguration());

		assertThatExceptionOfType(VaultException.class)
				.isThrownBy(() -> new AppIdAuthentication(options, restTemplate).login());

	}
}
