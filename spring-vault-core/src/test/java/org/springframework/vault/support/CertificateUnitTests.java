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

package org.springframework.vault.support;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Certificate}.
 *
 * @author Mark Paluch
 */
class CertificateUnitTests {

	ObjectMapper OBJECT_MAPPER = ObjectMapperSupplier.get();

	Certificate certificate;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void before() throws Exception {
		Map<String, String> data = this.OBJECT_MAPPER.readValue(getClass().getResourceAsStream("/certificate.json"),
				Map.class);

		this.certificate = Certificate.of(data.get("serial_number"), data.get("certificate"), data.get("issuing_ca"),
				List.of(), 0L);
	}

	@Test
	void getX509CertificateShouldReturnCertificate() {

		X509Certificate x509Certificate = this.certificate.getX509Certificate();

		assertThat(x509Certificate.getSubjectX500Principal().getName()).isEqualTo("CN=hello.example.com");
	}

	@Test
	void getX509IssuerCertificateShouldReturnCertificate() {

		X509Certificate x509Certificate = this.certificate.getX509IssuerCertificate();

		assertThat(x509Certificate.getSubjectX500Principal().getName()).startsWith("CN=Intermediate CA Certificate");
	}

	@Test
	void getAsTrustStore() throws Exception {

		KeyStore keyStore = this.certificate.createTrustStore();

		assertThat(keyStore.size()).isEqualTo(2);
	}

}
