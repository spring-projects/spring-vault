/*
 * Copyright 2017-present the original author or authors.
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

import java.io.File;

import org.junit.jupiter.api.Test;

import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestVaultClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.vault.util.Settings.*;

/**
 * Integration tests for {@link KubernetesAuthentication} using
 * {@link AuthenticationStepsExecutor}.
 *
 * @author Mark Paluch
 */
class KubernetesAuthenticationStepsIntegrationTests extends KubernetesAuthenticationIntegrationTestBase {

	@Test
	void shouldLoginSuccessfully() {

		File tokenFile = new File(findWorkDir(), "minikube/hello-minikube-token");

		KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions.builder()
				.role("my-role")
				.jwtSupplier(new KubernetesServiceAccountTokenFile(tokenFile))
				.build();

		TestVaultClient client = TestVaultClient.create();

		AuthenticationStepsExecutor executor = TestAuthenticationStepsExecutor
				.create(KubernetesAuthentication.createAuthenticationSteps(options), client);

		VaultToken login = executor.login();
		assertThat(login.getToken()).doesNotContain(Settings.token().getToken());
	}

}
