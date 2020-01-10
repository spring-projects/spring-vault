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
package org.springframework.vault.support;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Certificate}.
 *
 * @author Mark Paluch
 */
public class CertificateUnitTests {

	Certificate certificate;

	@SuppressWarnings("unchecked")
	@Before
	public void before() throws Exception {
		Map<String, String> data = new ObjectMapper().readValue(
				getClass().getResource("/certificate.json"), Map.class);

		certificate = Certificate.of(data.get("serial_number"), data.get("certificate"),
				data.get("issuing_ca"));
	}

	@Test
	public void getX509CertificateShouldReturnCertificate() {

		X509Certificate x509Certificate = certificate.getX509Certificate();

		assertThat(x509Certificate.getSubjectDN().getName()).isEqualTo(
				"CN=hello.example.com");
	}

	@Test
	public void getX509IssuerCertificateShouldReturnCertificate() {

		X509Certificate x509Certificate = certificate.getX509IssuerCertificate();

		assertThat(x509Certificate.getSubjectDN().getName()).startsWith(
				"CN=Intermediate CA Certificate");
	}

	@Test
	public void getAsTrustStore() throws Exception {

		KeyStore keyStore = certificate.createTrustStore();

		assertThat(keyStore.size()).isEqualTo(2);
	}
}
