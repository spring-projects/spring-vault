/*
 * Copyright 2017 the original author or authors.
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
 * Integration tests for {@link AppRoleAuthentication} using
 * {@link AuthenticationStepsExecutor}.
 *
 * @author Mark Paluch
 * @author Christophe Tafani-Dereeper
 */
public class AppRoleAuthenticationStepsIntegrationTests extends
		AppRoleAuthenticationIntegrationTestBase {

	@Test
	public void authenticationStepsShouldAuthenticateWithWrappedSecretId() {

		String roleId = getRoleId("with-secret-id");
		VaultToken unwrappingToken = generateWrappedSecretIdResponse();

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.secretId(SecretId.wrapped(unwrappingToken)).roleId(roleId).build();

		AuthenticationStepsExecutor executor = new AuthenticationStepsExecutor(
				AppRoleAuthentication.createAuthenticationSteps(options), prepare()
						.getRestTemplate());

		assertThat(executor.login()).isNotNull();
	}

	@Test
	public void authenticationStepsShouldAuthenticateWithWrappedRoleId() {

		String secretId = (String) getVaultOperations()
				.write(String.format("auth/approle/role/%s/secret-id", "with-secret-id"),
						null).getData().get("secret_id");

		VaultToken roleIdToken = generateWrappedRoleIdResponse();

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.secretId(SecretId.provided(secretId))
				.roleId(RoleId.wrapped(roleIdToken)).build();

		AuthenticationStepsExecutor executor = new AuthenticationStepsExecutor(
				AppRoleAuthentication.createAuthenticationSteps(options), prepare()
						.getRestTemplate());

		assertThat(executor.login()).isNotNull();
	}

	@Test
	public void authenticationStepsShouldAuthenticateWithPullSecretId() {

		String roleId = getRoleId("with-secret-id");

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.appRole("with-secret-id").secretId(SecretId.pull(Settings.token()))
				.roleId(roleId).build();

		AuthenticationStepsExecutor executor = new AuthenticationStepsExecutor(
				AppRoleAuthentication.createAuthenticationSteps(options), prepare()
						.getRestTemplate());

		assertThat(executor.login()).isNotNull();
	}

	@Test
	public void authenticationStepsShouldAuthenticateWithPullRoleId() {

		String secretId = (String) getVaultOperations()
				.write(String.format("auth/approle/role/%s/secret-id", "with-secret-id"),
						null).getData().get("secret_id");

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.secretId(SecretId.provided(secretId)).appRole("with-secret-id")
				.roleId(RoleId.pull(Settings.token())).build();

		AuthenticationStepsExecutor executor = new AuthenticationStepsExecutor(
				AppRoleAuthentication.createAuthenticationSteps(options), prepare()
						.getRestTemplate());

		assertThat(executor.login()).isNotNull();
	}

	@Test(expected = VaultException.class)
	public void authenticationStepsShouldAuthenticatePullModeFailsWithWrongSecretId() {

		String roleId = getRoleId("with-secret-id");

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId(roleId).secretId("this-is-a-wrong-secret-id").build();

		AuthenticationStepsExecutor executor = new AuthenticationStepsExecutor(
				AppRoleAuthentication.createAuthenticationSteps(options), prepare()
						.getRestTemplate());

		assertThat(executor.login()).isNotNull();
	}

	@Test
	public void authenticationStepsShouldAuthenticatePushModeWithProvidedSecretId() {

		String roleId = getRoleId("with-secret-id");
		String secretId = "hello_world_two";

		VaultResponse customSecretIdResponse = getVaultOperations().write(
				"auth/approle/role/with-secret-id/custom-secret-id",
				Collections.singletonMap("secret_id", secretId));

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId(roleId).secretId(secretId).build();

		AuthenticationStepsExecutor executor = new AuthenticationStepsExecutor(
				AppRoleAuthentication.createAuthenticationSteps(options), prepare()
						.getRestTemplate());

		assertThat(executor.login()).isNotNull();

		getVaultOperations().write(
				"auth/approle/role/with-secret-id/secret-id-accessor/destroy",
				customSecretIdResponse.getData());
	}

	@Test
	public void authenticationStepsShouldAuthenticatePushMode() {

		String roleId = getRoleId("with-secret-id");

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId(roleId).appRole("with-secret-id").initialToken(Settings.token())
				.build();

		AuthenticationStepsExecutor executor = new AuthenticationStepsExecutor(
				AppRoleAuthentication.createAuthenticationSteps(options), prepare()
						.getRestTemplate());

		assertThat(executor.login()).isNotNull();
	}
}
