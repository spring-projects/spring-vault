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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.RequiresVaultVersion;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.Version;

/**
 * Integration tests for {@link AppRoleAuthentication}.
 *
 * @author Mark Paluch
 * @author Christophe Tafani-Dereeper
 */
@RequiresVaultVersion(AppRoleAuthenticationIntegrationTestBase.SUITABLE_FOR_APP_ROLE_TESTS)
class AppRoleAuthenticationIntegrationTestBase extends IntegrationTestSupport {

	static final String SUITABLE_FOR_APP_ROLE_TESTS = "0.6.2";
	static final Version sysUnwrapSince = Version.parse("0.6.2");

	@BeforeEach
	public void before() {

		if (!prepare().hasAuth("approle")) {
			prepare().mountAuth("approle");
		}

		getVaultOperations().doWithSession(restOperations -> {

			Map<String, String> withSecretId = new HashMap<>();
			withSecretId.put("policies", "dummy");
			withSecretId.put("bound_cidr_list", "0.0.0.0/0");
			withSecretId.put("bind_secret_id", "true");
			withSecretId.put("token_ttl", "60s");
			withSecretId.put("token_max_ttl", "60s");

			restOperations.postForEntity("auth/approle/role/with-secret-id", withSecretId,
					Map.class);

			Map<String, String> noSecretIdRole = new HashMap<>();
			noSecretIdRole.put("policies", "dummy"); // policy
			noSecretIdRole.put("bound_cidr_list", "0.0.0.0/0");
			noSecretIdRole.put("bind_secret_id", "false");
			noSecretIdRole.put("max_ttl", "60s");

			restOperations.postForEntity("auth/approle/role/no-secret-id", noSecretIdRole,
					Map.class);

			return null;
		});
	}

	VaultOperations getVaultOperations() {
		return prepare().getVaultOperations();
	}

	String getRoleId(String roleName) {
		return (String) getVaultOperations()
				.read(String.format("auth/approle/role/%s/role-id", roleName))
				.getRequiredData().get("role_id");
	}

	VaultToken generateWrappedSecretIdResponse() {

		return getVaultOperations().doWithVault(restOperations -> {

			HttpEntity<String> httpEntity = getWrappingHeaders();

			VaultResponse response = restOperations
					.exchange("auth/approle/role/with-secret-id/secret-id",
							HttpMethod.PUT, httpEntity, VaultResponse.class)
					.getBody();

			return VaultToken.of(response.getWrapInfo().get("token"));
		});
	}

	VaultToken generateWrappedRoleIdResponse() {

		return getVaultOperations().doWithVault(restOperations -> {

			HttpEntity<String> httpEntity = getWrappingHeaders();

			VaultResponse response = restOperations
					.exchange("auth/approle/role/with-secret-id/role-id", HttpMethod.GET,
							httpEntity, VaultResponse.class)
					.getBody();

			return VaultToken.of(response.getWrapInfo().get("token"));
		});
	}

	UnwrappingEndpoints getUnwrappingEndpoints() {
		return useSysWrapping() ? UnwrappingEndpoints.SysWrapping
				: UnwrappingEndpoints.Cubbyhole;
	}

	private boolean useSysWrapping() {
		return prepare().getVersion().isGreaterThanOrEqualTo(sysUnwrapSince);
	}

	private HttpEntity<String> getWrappingHeaders() {

		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Vault-Wrap-Ttl", "3600");
		headers.set("X-Vault-Token", Settings.token().getToken());
		return new HttpEntity<>(null, headers);
	}
}
