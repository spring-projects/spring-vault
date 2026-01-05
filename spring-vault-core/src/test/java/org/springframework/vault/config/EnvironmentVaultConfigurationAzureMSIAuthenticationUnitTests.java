/*
 * Copyright 2020-present the original author or authors.
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

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.authentication.AzureMsiAuthentication;
import org.springframework.vault.authentication.AzureMsiAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link EnvironmentVaultConfiguration} with AzureMSI
 * authentication.
 *
 * @author Justin Bertrand
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = {"vault.uri=http://null", "vault.authentication=azure", "vault.azure-msi.role=role",
		"vault.azure-msi.metadata-service=http://foo"})
class EnvironmentVaultConfigurationAzureMSIAuthenticationUnitTests {

	@Configuration
	@Import(EnvironmentVaultConfiguration.class)
	static class MyConfig {

	}

	@Test
	void shouldConfigureAuthentication(@Autowired EnvironmentVaultConfiguration configuration) {

		ClientAuthentication clientAuthentication = configuration.clientAuthentication();

		assertThat(clientAuthentication).isInstanceOf(AzureMsiAuthentication.class);

		DirectFieldAccessor accessor = new DirectFieldAccessor(clientAuthentication);
		AzureMsiAuthenticationOptions options = (AzureMsiAuthenticationOptions) accessor.getPropertyValue("options");

		assertThat(options.getIdentityTokenServiceUri())
				.isEqualTo(AzureMsiAuthenticationOptions.DEFAULT_IDENTITY_TOKEN_SERVICE_URI);
		assertThat(options.getInstanceMetadataServiceUri()).isEqualTo(URI.create("http://foo"));
	}

}
