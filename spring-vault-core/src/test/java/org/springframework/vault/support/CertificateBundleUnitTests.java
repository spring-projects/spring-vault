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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CertificateBundle}.
 *
 * @author Mark Paluch
 * @author Alex Bremora
 */
class CertificateBundleUnitTests {

	ObjectMapper OBJECT_MAPPER = ObjectMapperSupplier.get();

	CertificateBundle certificateBundle;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void before() throws Exception {
		Map<String, String> data = this.OBJECT_MAPPER.readValue(getClass().getResource("/certificate.json"), Map.class);

		this.certificateBundle = CertificateBundle.of(data.get("serial_number"), data.get("certificate"),
				data.get("issuing_ca"), data.get("private_key"), data.get("private_key_type"));
	}

	@Test
	void getPrivateKeySpecShouldCreatePrivateKey() throws Exception {

		KeyFactory kf = KeyFactory.getInstance("RSA");
		PrivateKey privateKey = kf.generatePrivate(this.certificateBundle.getPrivateKeySpec());

		assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
		assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
	}

	@Test
	void getAsKeystore() throws Exception {

		KeyStore keyStore = this.certificateBundle.createKeyStore("mykey");

		assertThat(keyStore.size()).isEqualTo(1);
		assertThat(keyStore.getCertificateChain("mykey")).hasSize(2);
	}

}
