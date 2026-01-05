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

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestVaultClient;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CubbyholeAuthentication}.
 *
 * @author Mark Paluch
 */
class CubbyholeAuthenticationIntegrationTests extends CubbyholeAuthenticationIntegrationTestBase {

	@Test
	void shouldCreateWrappedToken() {

		Map<String, String> wrapInfo = prepareWrappedToken();

		String initialToken = wrapInfo.get("token");

		CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
				.unwrappingEndpoints(getUnwrappingEndpoints())
				.initialToken(VaultToken.of(initialToken))
				.wrapped()
				.build();
		VaultClient client = TestVaultClient.create();

		CubbyholeAuthentication authentication = new CubbyholeAuthentication(options, client);
		VaultToken login = authentication.login();
		assertThat(login.getToken()).doesNotContain(Settings.token().getToken());
	}

	@Test
	void loginShouldFail() {

		CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
				.unwrappingEndpoints(getUnwrappingEndpoints())
				.initialToken(VaultToken.of("Hello"))
				.wrapped()
				.build();

		VaultClient client = TestVaultClient.create();
		CubbyholeAuthentication authentication = new CubbyholeAuthentication(options, client);

		try {
			authentication.login();
			fail("Missing VaultException");
		} catch (VaultException e) {
			assertThat(e).hasMessageContaining("Cannot login using Cubbyhole");
		}
	}

}
