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

import java.util.Collections;

import org.junit.Test;

import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.RoleId;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.SecretId;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.Settings;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AppRoleAuthentication}.
 *
 * @author Mark Paluch
 * @author Christophe Tafani-Dereeper
 */
public class AppRoleAuthenticationIntegrationTests extends
		AppRoleAuthenticationIntegrationTestBase {

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
	public void shouldAuthenticateWithFullPullMode() {

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.appRole("with-secret-id").initialToken(Settings.token()).build();
		AppRoleAuthentication authentication = new AppRoleAuthentication(options,
				prepare().getRestTemplate());

		assertThat(authentication.login()).isNotNull();
	}

	@Test
	public void shouldAuthenticateWithPullMode() {

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId(getRoleId("with-secret-id")).appRole("with-secret-id")
				.initialToken(Settings.token()).build();
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

	@Test
	public void shouldAuthenticateWithWrappedSecretId() {

		String roleId = getRoleId("with-secret-id");
		VaultToken unwrappingToken = generateWrappedSecretIdResponse();

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.secretId(SecretId.wrapped(unwrappingToken))
				.roleId(RoleId.provided(roleId)).build();

		AppRoleAuthentication authentication = new AppRoleAuthentication(options,
				prepare().getRestTemplate());

		assertThat(authentication.login()).isNotNull();
	}

	@Test
	public void shouldAuthenticateWithWrappedRoleIdAndSecretId() {

		VaultToken secretIdToken = generateWrappedSecretIdResponse();
		VaultToken roleIdToken = generateWrappedRoleIdResponse();

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.secretId(SecretId.wrapped(secretIdToken))
				.roleId(RoleId.wrapped(roleIdToken)).build();

		AppRoleAuthentication authentication = new AppRoleAuthentication(options,
				prepare().getRestTemplate());

		assertThat(authentication.login()).isNotNull();
	}

	@Test(expected = VaultException.class)
	public void shouldAuthenticateWithWrappedSecretIdFailIfUnwrappingTokenExpired() {

		String roleId = getRoleId("no-secret-id");
		String unwrappingToken = "incorrect-unwrapping-token";

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.secretId(SecretId.wrapped(VaultToken.of(unwrappingToken)))
				.roleId(roleId).build();

		AppRoleAuthentication authentication = new AppRoleAuthentication(options,
				prepare().getRestTemplate());

		authentication.login();
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
		String secretId = "hello_world";

		VaultResponse customSecretIdResponse = getVaultOperations().write(
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
}
