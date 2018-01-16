/*
 * Copyright 2017-2018 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.Version;

import static org.junit.Assume.assumeTrue;

/**
 * Integration tests for {@link AppRoleAuthentication}.
 *
 * @author Mark Paluch
 * @author Christophe Tafani-Dereeper
 */
public class AppRoleAuthenticationIntegrationTestBase extends IntegrationTestSupport {

	private static Version SUITABLE_FOR_APP_ROLE_TESTS = Version.parse("0.6.2");

	@Before
	public void before() {

		assumeTrue(prepare().getVersion().isGreaterThanOrEqualTo(
				SUITABLE_FOR_APP_ROLE_TESTS));

		if (!prepare().hasAuth("approle")) {
			prepare().mountAuth("approle");
		}

		getVaultOperations().doWithSession(restOperations -> {

			Map<String, String> withSecretId = new HashMap<String, String>();
			withSecretId.put("policies", "dummy"); // policy
				withSecretId.put("bound_cidr_list", "0.0.0.0/0");
				withSecretId.put("bind_secret_id", "true");

				restOperations.postForEntity("auth/approle/role/with-secret-id",
						withSecretId, Map.class);

				Map<String, String> noSecretIdRole = new HashMap<String, String>();
				noSecretIdRole.put("policies", "dummy"); // policy
				noSecretIdRole.put("bound_cidr_list", "0.0.0.0/0");
				noSecretIdRole.put("bind_secret_id", "false");

				restOperations.postForEntity("auth/approle/role/no-secret-id",
						noSecretIdRole, Map.class);

				return null;
			});
	}

	protected VaultOperations getVaultOperations() {
		return prepare().getVaultOperations();
	}

	protected String getRoleId(String roleName) {
		return (String) getVaultOperations()
				.read(String.format("auth/approle/role/%s/role-id", roleName)).getData()
				.get("role_id");
	}

	protected VaultToken generateWrappedSecretIdResponse() {

		return getVaultOperations().doWithVault(
				restOperations -> {

					HttpEntity<String> httpEntity = getWrappingHeaders();

					VaultResponse response = restOperations.exchange(
							"auth/approle/role/with-secret-id/secret-id", HttpMethod.PUT,
							httpEntity, VaultResponse.class).getBody();

					return VaultToken.of(response.getWrapInfo().get("token"));
				});
	}

	protected VaultToken generateWrappedRoleIdResponse() {

		return getVaultOperations().doWithVault(
				restOperations -> {

					HttpEntity<String> httpEntity = getWrappingHeaders();

					VaultResponse response = restOperations.exchange(
							"auth/approle/role/with-secret-id/role-id", HttpMethod.GET,
							httpEntity, VaultResponse.class).getBody();

					return VaultToken.of(response.getWrapInfo().get("token"));
				});
	}

	private HttpEntity<String> getWrappingHeaders() {

		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Vault-Wrap-Ttl", "3600");
		headers.set("X-Vault-Token", Settings.token().getToken());
		return new HttpEntity<>(null, headers);
	}
}
