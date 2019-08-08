/*
 * Copyright 2017-2019 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SslConfiguration}.
 *
 * @author Mark Paluch
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
	}

	@Test
	void shouldCreateConfiguration() {

		KeyStoreConfiguration keystore = KeyStoreConfiguration
				.of(new ClassPathResource("certificate.json"));
		SslConfiguration ksConfig = SslConfiguration.unconfigured()
				.withKeyStore(keystore);

		assertThat(ksConfig.getKeyStoreConfiguration()).isSameAs(keystore);
		assertThat(ksConfig.getTrustStoreConfiguration().isPresent()).isFalse();

		SslConfiguration tsConfig = SslConfiguration.unconfigured()
				.withTrustStore(keystore);

		assertThat(tsConfig.getTrustStoreConfiguration()).isSameAs(keystore);
		assertThat(tsConfig.getKeyStoreConfiguration().isPresent()).isFalse();
	}
}
