/*
 * Copyright 2017-2022 the original author or authors.
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
package org.springframework.vault.support;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.vault.support.SslConfiguration.KeyStoreConfiguration;
import org.springframework.vault.util.Settings;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SslConfiguration}.
 *
 * @author Mark Paluch
 * @author Ryan Gow
 */
class SslConfigurationUnitTests {

	@Test
	void shouldCreateSslConfiguration() {

		SslConfiguration sslConfiguration = Settings.createSslConfiguration();

		assertThat(sslConfiguration.getKeyStoreConfiguration().isPresent()).isFalse();
		assertThat(sslConfiguration.getTrustStoreConfiguration().isPresent()).isTrue();
	}

	@Test
	void shouldCreateEmptySslConfiguration() {

		SslConfiguration sslConfiguration = SslConfiguration.unconfigured();

		assertThat(sslConfiguration.getKeyStoreConfiguration().isPresent()).isFalse();
		assertThat(sslConfiguration.getTrustStoreConfiguration().isPresent()).isFalse();
		assertThat(sslConfiguration.getEnabledCipherSuites()).isEmpty();
		assertThat(sslConfiguration.getEnabledProtocols()).isEmpty();
	}

	@Test
	void shouldCreateConfiguration() {

		KeyStoreConfiguration keystore = KeyStoreConfiguration.of(new ClassPathResource("certificate.json"));
		SslConfiguration ksConfig = SslConfiguration.unconfigured().withKeyStore(keystore);

		assertThat(ksConfig.getKeyStoreConfiguration()).isSameAs(keystore);
		assertThat(ksConfig.getTrustStoreConfiguration().isPresent()).isFalse();

		SslConfiguration tsConfig = SslConfiguration.unconfigured().withTrustStore(keystore);

		assertThat(tsConfig.getTrustStoreConfiguration()).isSameAs(keystore);
		assertThat(tsConfig.getKeyStoreConfiguration().isPresent()).isFalse();
	}

	@Test
	void shouldCreateConfigurationWithEnabledCipherSuites() {

		KeyStoreConfiguration keystore = KeyStoreConfiguration.of(new ClassPathResource("certificate.json"));
		SslConfiguration tsConfig = SslConfiguration.unconfigured().withTrustStore(keystore).withEnabledCipherSuites(
				"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");

		assertThat(tsConfig.getTrustStoreConfiguration()).isSameAs(keystore);
		assertThat(tsConfig.getKeyStoreConfiguration().isPresent()).isFalse();
		assertThat(tsConfig.getEnabledCipherSuites()).hasSize(2);
		assertThat(tsConfig.getEnabledCipherSuites().get(0)).isEqualTo("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384");
		assertThat(tsConfig.getEnabledCipherSuites().get(1)).isEqualTo("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
	}

	@Test
	void shouldCreateConfigurationWithEnabledProtocols() {

		KeyStoreConfiguration keystore = KeyStoreConfiguration.of(new ClassPathResource("certificate.json"));
		SslConfiguration tsConfig = SslConfiguration.unconfigured().withTrustStore(keystore)
				.withEnabledProtocols("TLSv1.2", "TLSv1.1");

		assertThat(tsConfig.getTrustStoreConfiguration()).isSameAs(keystore);
		assertThat(tsConfig.getKeyStoreConfiguration().isPresent()).isFalse();
		assertThat(tsConfig.getEnabledProtocols()).hasSize(2);
		assertThat(tsConfig.getEnabledProtocols().get(0)).isEqualTo("TLSv1.2");
		assertThat(tsConfig.getEnabledProtocols().get(1)).isEqualTo("TLSv1.1");
	}

	@Test
	void shouldCreatePemConfiguration() {

		KeyStoreConfiguration keystore = KeyStoreConfiguration.of(new ClassPathResource("certificate.json"))
				.withStoreType("PEM");
		SslConfiguration configuration = SslConfiguration.forTrustStore(keystore);

		assertThat(configuration.getTrustStoreConfiguration().getStoreType()).isEqualTo("PEM");
	}

}
