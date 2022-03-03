/*
 * Copyright 2016-2022 the original author or authors.
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

import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CertificateBundle}.
 *
 * @author Mark Paluch
 * @author Alex Bremora
 */
class CertificateBundleUnitTests {

	ObjectMapper OBJECT_MAPPER = ObjectMapperSupplier.get();

	@Test
	void getPrivateKeySpecShouldCreatePrivateKey() throws Exception {
		CertificateBundle bundle = loadCertificateBundle("/certificate.json");
		KeyFactory kf = KeyFactory.getInstance("RSA");
		PrivateKey privateKey = kf.generatePrivate(bundle.getPrivateKeySpec());

		assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
		assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
	}

	@Test
	void getPrivateKeySpecShouldCreatePrivateKeyDerRsa() throws Exception {
		CertificateBundle bundle = loadCertificateBundle("/certificate-response-rsa-der.json");
		KeyFactory kf = KeyFactory.getInstance("RSA");
		PrivateKey privateKey = kf.generatePrivate(bundle.getPrivateKeySpec());

		assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
		assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
	}

	@Test
	void getPrivateKeySpecShouldCreatePrivateKeyDerEc() throws Exception {
		CertificateBundle bundle = loadCertificateBundle("/certificate-response-ec-der.json");
		KeyFactory kf = KeyFactory.getInstance("ec");
		PrivateKey privateKey = kf.generatePrivate(bundle.getPrivateKeySpec());

		assertThat(privateKey.getAlgorithm()).isEqualTo("EC");
		assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
	}

	@Test
	void getPrivateKeySpecShouldCreatePrivateKeyPemRsa() throws Exception {
    CertificateBundle bundle = loadCertificateBundle("/certificate-response-rsa-pem.json");
    KeyFactory kf = KeyFactory.getInstance("RSA");
    PrivateKey privateKey = kf.generatePrivate(bundle.getPrivateKeySpec());

    assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
    assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
	}

	@Test
	void getPrivateKeySpecShouldCreatePrivateKeyPemRsaPkcs8ThrowsException() throws Exception {
    assertThatThrownBy(() -> {
      CertificateBundle bundle = loadCertificateBundle("/certificate-response-rsa-pem-pkcs8.json");
      KeyFactory kf = KeyFactory.getInstance("RSA");
      PrivateKey privateKey = kf.generatePrivate(bundle.getPrivateKeySpec());

      assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
      assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
    }).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void getPrivateKeySpecShouldCreatePrivateKeyPemBundleRsa() throws Exception {
		CertificateBundle bundle = loadCertificateBundle("/certificate-response-rsa-pembundle.json");
		KeyFactory kf = KeyFactory.getInstance("RSA");
		PrivateKey privateKey = kf.generatePrivate(bundle.getPrivateKeySpec());

		assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
		assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
	}

	@Test
	void getPrivateKeySpecShouldCreatePrivateKeyPemEc() throws Exception {
		CertificateBundle bundle = loadCertificateBundle("/certificate-response-ec-pem.json");
		KeyFactory kf = KeyFactory.getInstance("ec");
		PrivateKey privateKey = kf.generatePrivate(bundle.getPrivateKeySpec());

		assertThat(privateKey.getAlgorithm()).isEqualTo("EC");
		assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
	}

	@Test
	void getPrivateKeySpecShouldCreatePrivateKeyPemEcPkcs8ThrowsException() throws Exception {
    assertThatThrownBy(() -> {
      CertificateBundle bundle = loadCertificateBundle("/certificate-response-ec-pem-pkcs8.json");
      KeyFactory kf = KeyFactory.getInstance("ec");
      PrivateKey privateKey = kf.generatePrivate(bundle.getPrivateKeySpec());

      assertThat(privateKey.getAlgorithm()).isEqualTo("EC");
      assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
    }).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void getAsKeystore() throws Exception {
		CertificateBundle bundle = loadCertificateBundle("/certificate.json");
		KeyStore keyStore = bundle.createKeyStore("mykey");

		assertThat(keyStore.size()).isEqualTo(1);
		assertThat(keyStore.getCertificateChain("mykey")).hasSize(2);
	}

  @ParameterizedTest
  @ValueSource(strings = {
      "/certificate-response-rsa-pem.json",
      "/certificate-response-rsa-der.json",
        "/certificate-response-rsa-pembundle.json",
      "/certificate-response-ec-pem.json",
      "/certificate-response-ec-der.json",
      "/certificate-response-ec-pembundle.json"})
  void createKeystore(String path) throws Exception {
    CertificateBundle bundle = loadCertificateBundle(path);
    KeyStore keyStore = bundle.createKeyStore("localhost");

    assertThat(keyStore).isNotNull();
  }

	@ParameterizedTest
	@ValueSource(strings = {
			"/certificate-response-rsa-pem-pkcs8.json",
			"/certificate-response-ec-pem-pkcs8.json"})
	void createKeystoreNotSupportedThrowsException(String path) {
		assertThatThrownBy(() -> {
			CertificateBundle bundle = loadCertificateBundle(path);
			KeyStore keyStore = bundle.createKeyStore("localhost");

			assertThat(keyStore).isNotNull();
		}).isInstanceOf(UnsupportedOperationException.class);
	}

	@SuppressWarnings("unchecked")
	CertificateBundle loadCertificateBundle(String path) throws Exception {
		Map<String, String> data = this.OBJECT_MAPPER.readValue(getClass().getResource(path), Map.class);

		return CertificateBundle.of(data.get("serial_number"), data.get("certificate"), data.get("issuing_ca"),
				data.get("private_key"), data.get("private_key_type"));
	}

}
