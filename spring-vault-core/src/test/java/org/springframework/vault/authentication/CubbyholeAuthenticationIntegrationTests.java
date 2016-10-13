/*
 * Copyright 2016 the original author or authors.
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

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.VaultException;
import org.springframework.vault.client.VaultResponseEntity;
import org.springframework.vault.core.VaultOperations.SessionCallback;
import org.springframework.vault.core.VaultOperations.VaultSession;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestRestTemplateFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assume.assumeNotNull;

/**
 * Integration tests for {@link CubbyholeAuthentication}.
 * 
 * @author Mark Paluch
 */
public class CubbyholeAuthenticationIntegrationTests extends IntegrationTestSupport {

	@Test
	public void shouldCreateWrappedToken() throws Exception {

		VaultResponseEntity<VaultResponse> response = prepare().getVaultOperations()
				.doWithVault(new SessionCallback<VaultResponseEntity<VaultResponse>>() {
					@Override
					public VaultResponseEntity<VaultResponse> doWithVault(
							VaultSession session) {

						HttpHeaders headers = new HttpHeaders();
						headers.add("X-Vault-Wrap-TTL", "10m");

						return session.exchange("auth/token/create", HttpMethod.POST,
								new HttpEntity<Object>(headers), VaultResponse.class,
								null);
					}
				});

		Map<String, String> wrapInfo = response.getBody().getWrapInfo();

		// Response Wrapping requires Vault 0.6.0+
		assumeNotNull(wrapInfo);

		String initialToken = wrapInfo.get("token");

		CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
				.initialToken(VaultToken.of(initialToken)).wrapped().build();

		VaultClient vaultClient = new VaultClient(TestRestTemplateFactory.create(Settings
				.createSslConfiguration()), new VaultEndpoint());

		CubbyholeAuthentication authentication = new CubbyholeAuthentication(options,
				vaultClient);
		VaultToken login = authentication.login();
		assertThat(login.getToken()).doesNotContain(Settings.token().getToken());
	}

	@Test
	public void loginShouldFail() throws Exception {

		CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
				.initialToken(VaultToken.of("Hello")).wrapped().build();

		VaultClient vaultClient = new VaultClient(TestRestTemplateFactory.create(Settings
				.createSslConfiguration()), new VaultEndpoint());

		CubbyholeAuthentication authentication = new CubbyholeAuthentication(options,
				vaultClient);
		try {
			authentication.login();
			fail("Missing VaultException");
		}
		catch (VaultException e) {
			assertThat(e).hasMessageContaining("Cannot retrieve Token from cubbyhole")
					.hasMessageContaining("permission denied");
		}
	}
}
