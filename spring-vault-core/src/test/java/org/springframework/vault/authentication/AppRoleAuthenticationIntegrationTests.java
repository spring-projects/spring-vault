/*
 * Copyright 2016-2017 the original author or authors.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import org.springframework.vault.VaultException;
import org.springframework.vault.core.RestOperationsCallback;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.web.client.RestOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assume.assumeThat;

/**
 * Integration tests for {@link AppRoleAuthentication}.
 *
 * @author Mark Paluch
 */
public class AppRoleAuthenticationIntegrationTests extends IntegrationTestSupport {

	@Before
	public void before() {

		assumeThat(
				prepare().getVaultOperations().opsForSys().health().getVersion(),
				not(anyOf(nullValue(), equalTo(""), containsString("0.5"),
						containsString("0.6.1"))));

		if (!prepare().hasAuth("approle")) {
			prepare().mountAuth("approle");
		}

		getVaultOperations().doWithSession(new RestOperationsCallback<Object>() {
			@Override
			public Object doWithRestOperations(RestOperations restOperations) {

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
			}
		});
	}

	@Test
	public void shouldAuthenticateWithRoleIdOnly() {

		String roleId = getRoleId("no-secret-id");
		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId(roleId).build();
		AppRoleAuthentication authentication = new AppRoleAuthentication(options,
				prepare().getRestTemplate());

		assertThat(authentication.login()).isNotNull();
	}

	@Test
	public void shouldAuthenticatePullModeWithGeneratedSecretId() {

		String roleId = getRoleId("with-secret-id");
		String secretId = (String) getVaultOperations()
				.write(String.format("auth/approle/role/%s/secret-id", "with-secret-id"),
						null).getData().get("secret_id");

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId(roleId).secretId(secretId).build();
		AppRoleAuthentication authentication = new AppRoleAuthentication(options,
				prepare().getRestTemplate());

		assertThat(authentication.login()).isNotNull();
	}

	@Test(expected = VaultException.class)
	public void shouldAuthenticatePullModeFailsWithoutSecretId() {

		String roleId = getRoleId("with-secret-id");

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId(roleId).build();
		AppRoleAuthentication authentication = new AppRoleAuthentication(options,
				prepare().getRestTemplate());

		assertThat(authentication.login()).isNotNull();
	}

	@Test(expected = VaultException.class)
	public void shouldAuthenticatePullModeFailsWithWrongSecretId() {

		String roleId = getRoleId("with-secret-id");

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId(roleId).secretId("this-is-a-wrong-secret-id").build();
		AppRoleAuthentication authentication = new AppRoleAuthentication(options,
				prepare().getRestTemplate());

		assertThat(authentication.login()).isNotNull();
	}

	@Test
	public void shouldAuthenticatePushModeWithProvidedSecretId() {

		String roleId = getRoleId("with-secret-id");
		final String secretId = "hello_world";

		final VaultResponse customSecretIdResponse = getVaultOperations().write(
				"auth/approle/role/with-secret-id/custom-secret-id",
				Collections.singletonMap("secret_id", secretId));

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId(roleId).secretId(secretId).build();
		AppRoleAuthentication authentication = new AppRoleAuthentication(options,
				prepare().getRestTemplate());

		assertThat(authentication.login()).isNotNull();

		getVaultOperations().write(
				"auth/approle/role/with-secret-id/secret-id-accessor/destroy",
				customSecretIdResponse.getData());
	}

	private VaultOperations getVaultOperations() {
		return prepare().getVaultOperations();
	}

	private String getRoleId(String roleName) {
		return (String) getVaultOperations()
				.read(String.format("auth/approle/role/%s/role-id", roleName)).getData()
				.get("role_id");
	}
}
