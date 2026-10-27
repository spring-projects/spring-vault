/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.vault.client;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.vault.support.CertificateBundle;
import org.springframework.vault.support.JacksonCompat;
import org.springframework.vault.support.SslConfiguration;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ClientConfiguration}.
 *
 * @author Tim Jungk
 */
class ClientConfigurationUnitTests {

	@Test
	void shouldLoadAsciiPemCertificate() throws Exception {

		CertificateBundle bundle = JacksonCompat.instance()
				.getObjectMapperAccessor()
				.deserialize(new ClassPathResource("certificate-response-rsa-pem.json").getContentAsByteArray(),
						CertificateBundle.class);
		ByteArrayResource resource = new ByteArrayResource(bundle.getCertificate().getBytes(StandardCharsets.US_ASCII));

		KeyStore keyStore = ClientConfiguration.getKeyStore(
				SslConfiguration.KeyStoreConfiguration.of(resource).withStoreType(SslConfiguration.PEM_KEYSTORE_TYPE));

		assertThat(keyStore.size()).isEqualTo(1);
		assertThat(keyStore.getCertificate(keyStore.aliases().nextElement())).isEqualTo(bundle.getX509Certificate());
	}

}
