/*
 * Copyright 2017-2021 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.ClientCertificateAuthentication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EnvironmentVaultConfiguration} with AppRole authentication.
 *
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@TestPropertySource(
		properties = { "vault.uri=https://localhost:8123", "vault.authentication=cert", "vault.aws-ec2.role-id=role" })
class EnvironmentVaultConfigurationClientCertAuthenticationUnitTests {

	@Configuration
	@Import(EnvironmentVaultConfiguration.class)
	static class ApplicationConfiguration {

	}

	@Autowired
	EnvironmentVaultConfiguration configuration;

	@Test
	void shouldConfigureAuthentication() {

		ClientAuthentication clientAuthentication = this.configuration.clientAuthentication();

		assertThat(clientAuthentication).isInstanceOf(ClientCertificateAuthentication.class);
	}

}
