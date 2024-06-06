/*
 * Copyright 2016-2024 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.net.URL;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link CertificateBundle}.
 *
 * @author Mark Paluch
 * @author Alex Bremora
 */
class CertificateBundleUnitTests {

	ObjectMapper OBJECT_MAPPER = ObjectMapperSupplier.get();

	CertificateBundle certificateBundle;

	@BeforeEach
	void before() {
		this.certificateBundle = loadCertificateBundle("certificate.json");
	}

	@Test
	void getPrivateKeySpecShouldCreatePrivateKey() throws Exception {

		KeyFactory kf = KeyFactory.getInstance("RSA");
		PrivateKey privateKey = kf.generatePrivate(this.certificateBundle.getPrivateKeySpec());

		assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
		assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
	}

	@Test
	void getPrivateKeySpecShouldCreatePrivateKeyPemRsaPkcs8() throws Exception {

		CertificateBundle bundle = loadCertificateBundle("certificate-response-rsa-pem-pkcs8.json");
		KeyFactory kf = KeyFactory.getInstance("RSA");
		PrivateKey privateKey = kf.generatePrivate(bundle.getPrivateKeySpec());

		assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
		assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
	}

	@Test
	void getPrivateKeySpecShouldCreatePrivateKeyPemBundleRsa() throws Exception {

		CertificateBundle bundle = loadCertificateBundle("certificate-response-rsa-pembundle.json");
		KeyFactory kf = KeyFactory.getInstance("RSA");
		PrivateKey privateKey = kf.generatePrivate(bundle.getPrivateKeySpec());

		assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
		assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
	}

	@Test
	void getPrivateKeySpecShouldCreatePrivateKeyPemEc() throws Exception {

		CertificateBundle bundle = loadCertificateBundle("certificate-response-ec-pem.json");
		KeyFactory kf = KeyFactory.getInstance("ec");
		PrivateKey privateKey = kf.generatePrivate(bundle.getPrivateKeySpec());

		assertThat(privateKey.getAlgorithm()).isEqualTo("EC");
		assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
	}

	@Test
	void invalidEcKeySpecShouldThrowException() {

		CertificateBundle bundle = loadCertificateBundle("certificate-response-ec-pem-pkcs8.json");

		assertThat(bundle.getPrivateKeySpec()).isNotNull();
	}

	@Test
	void shouldReturnPrivateKey() {

		String serialNumber = "aserialnumber";
		String certificate = "certificate";
		String caCertificate = "caCertificate";
		String privateKey = "aprivatekey";

		CertificateBundle bundle = CertificateBundle.of(serialNumber, certificate, caCertificate, privateKey);
		assertThat(bundle.getPrivateKey()).isNotNull();
	}

	@Test
	void getAsKeystore() throws Exception {

		CertificateBundle bundle = loadCertificateBundle("certificate.json");
		KeyStore keyStore = bundle.createKeyStore("mykey");
		assertThat(keyStore.size()).isEqualTo(1);
		assertThat(keyStore.getCertificateChain("mykey")).hasSize(2);

		KeyStore keyStoreWithPassword = bundle.createKeyStore("mykey", "mypassword");
		assertThat(keyStoreWithPassword.size()).isEqualTo(1);
		assertThat(keyStoreWithPassword.getCertificateChain("mykey")).hasSize(2);

		KeyStore keyStoreWithPasswordChar = bundle.createKeyStore("mykey", new char[0]);
		assertThat(keyStoreWithPasswordChar.size()).isEqualTo(1);
		assertThat(keyStoreWithPasswordChar.getCertificateChain("mykey")).hasSize(2);
	}

	@ParameterizedTest
	@ValueSource(strings = {"certificate-response-rsa-pem.json", "certificate-response-rsa-der.json",
			"certificate-response-rsa-pembundle.json", "certificate-response-ec-pem.json",
			"certificate-response-ec-der.json", "certificate-response-ec-pembundle.json"})
	void createKeystore(String path) {

		CertificateBundle bundle = loadCertificateBundle(path);
		KeyStore keyStore = bundle.createKeyStore("localhost");
		assertThat(keyStore).isNotNull();

		KeyStore keyStoreWithPassword = bundle.createKeyStore("localhost", "mypassword");
		assertThat(keyStoreWithPassword).isNotNull();

		KeyStore keyStoreWithPasswordChar = bundle.createKeyStore("localhost", new char[0]);
		assertThat(keyStoreWithPasswordChar).isNotNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"certificate-response-rsa-pem-pkcs8.json", "certificate-response-ec-pem-pkcs8.json"})
	void shouldCreateKeystore(String path) {

		CertificateBundle bundle = loadCertificateBundle(path);
		KeyStore keyStore = bundle.createKeyStore("localhost");
		assertThat(keyStore).isNotNull();

		KeyStore keyStoreWithPassword = bundle.createKeyStore("localhost", "mypassword");
		assertThat(keyStoreWithPassword).isNotNull();

		KeyStore keyStoreWithPasswordChar = bundle.createKeyStore("localhost", new char[0]);
		assertThat(keyStoreWithPasswordChar).isNotNull();
	}

	CertificateBundle loadCertificateBundle(String path) {

		try {
			URL resource = getClass().getClassLoader().getResource(path);
			assertThat(resource).as("Resource " + path).isNotNull();
			return this.OBJECT_MAPPER.readValue(resource, CertificateBundle.class);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
