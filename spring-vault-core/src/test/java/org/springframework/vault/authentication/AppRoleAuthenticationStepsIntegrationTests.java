/*
 * Copyright 2017-2025 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.RoleId;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.SecretId;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.Settings;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link AppRoleAuthentication} using
 * {@link AuthenticationStepsExecutor}.
 *
 * @author Mark Paluch
 * @author Christophe Tafani-Dereeper
 */
class AppRoleAuthenticationStepsIntegrationTests extends AppRoleAuthenticationIntegrationTestBase {

	@Test
	void shouldAuthenticateWithRoleIdOnly() {

		String roleId = getRoleId("no-secret-id");
		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId(RoleId.provided(roleId))
				.build();

		AuthenticationStepsExecutor executor = TestAuthenticationStepsExecutor
			.create(AppRoleAuthentication.createAuthenticationSteps(options), prepare().getVaultClient());

		assertThat(executor.login()).isNotNull();
	}

	@Test
	void authenticationStepsShouldAuthenticateWithWrappedSecretId() {

		String roleId = getRoleId("with-secret-id");
		VaultToken unwrappingToken = generateWrappedSecretIdResponse();

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.secretId(SecretId.wrapped(unwrappingToken))
				.roleId(RoleId.provided(roleId))
				.unwrappingEndpoints(getUnwrappingEndpoints())
				.build();

		AuthenticationStepsExecutor executor = getExecutor(options);

		assertThat(executor.login()).isNotNull();
	}

	@Test
	void authenticationStepsShouldAuthenticateWithWrappedRoleId() {

		String secretId = (String) getVaultOperations()
				.write("auth/approle/role/%s/secret-id".formatted("with-secret-id"), null)
				.getRequiredData()
				.get("secret_id");

		VaultToken roleIdToken = generateWrappedRoleIdResponse();

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.secretId(SecretId.provided(secretId))
				.roleId(RoleId.wrapped(roleIdToken))
				.unwrappingEndpoints(getUnwrappingEndpoints())
				.build();

		AuthenticationStepsExecutor executor = getExecutor(options);

		assertThat(executor.login()).isNotNull();
	}

	@Test
	void shouldAuthenticateWithFullPullMode() {

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.appRole("with-secret-id")
				.roleId(RoleId.pull(Settings.token()))
				.secretId(SecretId.pull(Settings.token()))
				.build();

		AuthenticationStepsExecutor executor = getExecutor(options);

		assertThat(executor.login()).isNotNull();
	}

	@Test
	void authenticationStepsShouldAuthenticateWithPullSecretId() {

		String roleId = getRoleId("with-secret-id");

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.appRole("with-secret-id")
				.secretId(SecretId.pull(Settings.token()))
				.roleId(RoleId.provided(roleId))
				.build();

		AuthenticationStepsExecutor executor = getExecutor(options);

		assertThat(executor.login()).isNotNull();
	}

	@Test
	void authenticationStepsShouldAuthenticateWithPullRoleId() {

		String secretId = (String) getVaultOperations()
				.write("auth/approle/role/%s/secret-id".formatted("with-secret-id"), null)
				.getRequiredData()
				.get("secret_id");

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.secretId(SecretId.provided(secretId))
				.appRole("with-secret-id")
				.roleId(RoleId.pull(Settings.token()))
				.build();

		AuthenticationStepsExecutor executor = getExecutor(options);

		assertThat(executor.login()).isNotNull();
	}

	@Test
	void authenticationStepsShouldAuthenticatePullModeFailsWithWrongSecretId() {

		String roleId = getRoleId("with-secret-id");

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId(RoleId.provided(roleId))
				.secretId(SecretId.provided("this-is-a-wrong-secret-id"))
				.build();

		AuthenticationStepsExecutor executor = getExecutor(options);

		assertThatExceptionOfType(VaultException.class).isThrownBy(executor::login);
	}

	@Test
	void authenticationStepsShouldAuthenticatePushModeWithProvidedSecretId() {

		String roleId = getRoleId("with-secret-id");
		String secretId = "hello_world_two";

		VaultResponse customSecretIdResponse = getVaultOperations().write(
				"auth/approle/role/with-secret-id/custom-secret-id", Collections.singletonMap("secret_id", secretId));

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId(RoleId.provided(roleId))
				.secretId(SecretId.provided(secretId))
				.build();

		AuthenticationStepsExecutor executor = getExecutor(options);

		assertThat(executor.login()).isNotNull();

		getVaultOperations().write("auth/approle/role/with-secret-id/secret-id-accessor/destroy",
				customSecretIdResponse.getRequiredData());
	}

	@Test
	void authenticationStepsShouldAuthenticatePushMode() {

		String roleId = getRoleId("with-secret-id");

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId(RoleId.provided(roleId))
				.appRole("with-secret-id")
				.secretId(SecretId.pull(Settings.token()))
				.build();

		AuthenticationStepsExecutor executor = getExecutor(options);

		assertThat(executor.login()).isNotNull();
	}

	private AuthenticationStepsExecutor getExecutor(AppRoleAuthenticationOptions options) {
		return TestAuthenticationStepsExecutor.create(AppRoleAuthentication.createAuthenticationSteps(options),
				prepare().getVaultClient());
	}

}
