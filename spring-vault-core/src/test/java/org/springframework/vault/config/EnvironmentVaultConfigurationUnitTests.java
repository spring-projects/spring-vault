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
package org.springframework.vault.config;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.VaultToken;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EnvironmentVaultConfiguration}.
 *
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@TestPropertySource(properties = { "vault.uri=https://localhost:8123",
		"vault.token=my-token", "vault.ssl.key-store-password=key store password",
		"vault.ssl.trust-store-password=trust store password" })
public class EnvironmentVaultConfigurationUnitTests {

	@Configuration
	@Import(EnvironmentVaultConfiguration.class)
	static class ApplicationConfiguration {
	}

	@Autowired
	private EnvironmentVaultConfiguration configuration;

	@Autowired
	private ConfigurableEnvironment configurableEnvironment;

	@Test
	public void shouldConfigureEndpoint() {
		assertThat(configuration.vaultEndpoint().getPort()).isEqualTo(8123);
	}

	@Test
	public void shouldConfigureTokenAuthentication() {

		ClientAuthentication clientAuthentication = configuration.clientAuthentication();

		assertThat(clientAuthentication).isInstanceOf(TokenAuthentication.class);
		assertThat(clientAuthentication.login()).isEqualTo(VaultToken.of("my-token"));
	}

	@Test
	public void shouldConfigureSsl() {

		Map<String, Object> map = new HashMap<String, Object>();
		map.put("vault.ssl.key-store", "classpath:certificate.json");
		map.put("vault.ssl.trust-store", "classpath:certificate.json");

		MapPropertySource propertySource = new MapPropertySource("shouldConfigureSsl",
				map);
		configurableEnvironment.getPropertySources().addFirst(propertySource);

		SslConfiguration sslConfiguration = configuration.sslConfiguration();

		assertThat(sslConfiguration.getKeyStore()).isInstanceOf(ClassPathResource.class);
		assertThat(sslConfiguration.getKeyStorePassword())
				.isEqualTo("key store password");

		assertThat(sslConfiguration.getTrustStore())
				.isInstanceOf(ClassPathResource.class);
		assertThat(sslConfiguration.getTrustStorePassword()).isEqualTo(
				"trust store password");

		configurableEnvironment.getPropertySources().remove(propertySource.getName());
	}
}
