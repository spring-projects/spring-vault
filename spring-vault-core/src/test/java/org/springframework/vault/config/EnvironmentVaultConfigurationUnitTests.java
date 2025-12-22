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

package org.springframework.vault.config;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.VaultToken;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link EnvironmentVaultConfiguration}.
 *
 * @author Mark Paluch
 * @author Ryan Gow
 */
@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = {"vault.uri=https://localhost:8123", "vault.token=my-token",
		"vault.ssl.key-store-password=key store password", "vault.ssl.trust-store-password=trust store password"})
class EnvironmentVaultConfigurationUnitTests {

	@Configuration
	@Import(EnvironmentVaultConfiguration.class)
	static class ApplicationConfiguration {

	}

	@Autowired
	EnvironmentVaultConfiguration configuration;

	@Autowired
	ConfigurableEnvironment configurableEnvironment;

	@Test
	void shouldConfigureEndpoint() {
		assertThat(this.configuration.vaultEndpoint().getPort()).isEqualTo(8123);
	}

	@Test
	void shouldConfigureTokenAuthentication() {

		ClientAuthentication clientAuthentication = this.configuration.clientAuthentication();

		assertThat(clientAuthentication).isInstanceOf(TokenAuthentication.class);
		assertThat(clientAuthentication.login()).isEqualTo(VaultToken.of("my-token"));
	}

	@Test
	void shouldConfigureSsl() {

		Map<String, Object> map = new HashMap<String, Object>();
		map.put("vault.ssl.key-store", "classpath:certificate.json");
		map.put("vault.ssl.trust-store", "classpath:certificate.json");
		map.put("vault.ssl.enabled-protocols", "TLSv1.2 , TLSv1.1 ");
		map.put("vault.ssl.enabled-cipher-suites",
				"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384 , TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");

		MapPropertySource propertySource = new MapPropertySource("shouldConfigureSsl", map);
		this.configurableEnvironment.getPropertySources().addFirst(propertySource);

		SslConfiguration sslConfiguration = this.configuration.sslConfiguration();

		assertThat(sslConfiguration.getKeyStore()).isInstanceOf(ClassPathResource.class);
		assertThat(new String(sslConfiguration.getKeyStoreConfiguration().getStorePassword()))
				.isEqualTo("key store password");

		assertThat(sslConfiguration.getTrustStore()).isInstanceOf(ClassPathResource.class);
		assertThat(new String(sslConfiguration.getTrustStoreConfiguration().getStorePassword()))
				.isEqualTo("trust store password");

		assertThat(sslConfiguration.getEnabledProtocols()).containsExactly("TLSv1.2", "TLSv1.1");
		assertThat(sslConfiguration.getEnabledCipherSuites()).containsExactly("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
				"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");

		this.configurableEnvironment.getPropertySources().remove(propertySource.getName());
	}

}
