/*
 * Copyright 2017-2020 the original author or authors.
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
package org.springframework.vault.config;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.KubernetesAuthentication;
import org.springframework.vault.util.VaultExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EnvironmentVaultConfiguration} with Kube authentication.
 *
 * @author Michal Budzyn
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ExtendWith(VaultExtension.class)
@TestPropertySource(properties = { "vault.uri=https://localhost:8123", "vault.authentication=kubernetes",
		"vault.kubernetes.role=my-role", "vault.kubernetes.service-account-token-file=target/token" })
class EnvironmentVaultConfigurationKubernetesAuthenticationUnitTests {

	@Configuration
	@Import(EnvironmentVaultConfiguration.class)
	static class ApplicationConfiguration {

	}

	@BeforeAll
	static void beforeClass() throws Exception {
		Files.write(Paths.get("target", "token"), "token".getBytes());
	}

	@Autowired
	EnvironmentVaultConfiguration configuration;

	@Test
	void shouldConfigureAuthentication() {

		ClientAuthentication clientAuthentication = this.configuration.clientAuthentication();

		assertThat(clientAuthentication).isInstanceOf(KubernetesAuthentication.class);
	}

}
