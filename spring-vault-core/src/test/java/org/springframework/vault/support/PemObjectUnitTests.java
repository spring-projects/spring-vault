/*
 * Copyright 2020 the original author or authors.
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

import java.io.File;
import java.io.IOException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.util.FileCopyUtils;
import org.springframework.vault.util.Settings;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PemObject}.
 *
 * @author Mark Paluch
 */
class PemObjectUnitTests {

	final File workdir = Settings.findWorkDir();

	final File privateDir = new File(this.workdir, "ca/private");

	@BeforeEach
	void setUp() {
		assertThat(this.privateDir).exists()
				.isDirectoryContaining(file -> file.getName().equalsIgnoreCase("localhost.public.key.pem"));
		assertThat(this.privateDir).exists()
				.isDirectoryContaining(file -> file.getName().equalsIgnoreCase("localhost.decrypted.key.pem"));
	}

	@Test
	void shouldDecodePublicKey() throws IOException {

		String content = new String(
				FileCopyUtils.copyToByteArray(new File(this.privateDir, "localhost.public.key.pem")));

		PemObject pemObject = PemObject.parseFirst(content);
		assertThat(pemObject.isPrivateKey()).isFalse();
		assertThat(pemObject.isPublicKey()).isTrue();
		assertThat(pemObject.getRSAPublicKeySpec()).isNotNull();
	}

	@Test
	void shouldDecodePrivateKey() throws IOException {

		String content = new String(
				FileCopyUtils.copyToByteArray(new File(this.privateDir, "localhost.decrypted.key.pem")));

		PemObject pemObject = PemObject.parseFirst(content);
		assertThat(pemObject.isPrivateKey()).isTrue();
		assertThat(pemObject.isPublicKey()).isFalse();
		assertThat(pemObject.getRSAPrivateKeySpec()).isNotNull();
	}

	@Test
	void shouldDecodeConcatenatedPEMContent() throws IOException {

		String content1 = new String(
				FileCopyUtils.copyToByteArray(new File(this.privateDir, "localhost.public.key.pem")));
		String content2 = new String(
				FileCopyUtils.copyToByteArray(new File(this.privateDir, "localhost.decrypted.key.pem")));

		List<PemObject> pemObjects = PemObject.parse(content1 + content2);

		assertThat(pemObjects).hasSize(2);
		assertThat(pemObjects.get(0).isPublicKey()).isTrue();
		assertThat(pemObjects.get(1).isPrivateKey()).isTrue();
	}

	@Test
	void keysShouldMatch() throws IOException {

		PemObject publicKey = PemObject.parseFirst(
				new String(FileCopyUtils.copyToByteArray(new File(this.privateDir, "localhost.public.key.pem"))));

		PemObject privateKey = PemObject.parseFirst(
				new String(FileCopyUtils.copyToByteArray(new File(this.privateDir, "localhost.decrypted.key.pem"))));

		RSAPublicKeySpec publicSpec = publicKey.getRSAPublicKeySpec();
		RSAPrivateCrtKeySpec privateKeySpec = privateKey.getRSAPrivateKeySpec();

		assertThat(publicSpec.getModulus()).isEqualTo(privateKeySpec.getModulus());
		assertThat(publicSpec.getPublicExponent()).isEqualTo(privateKeySpec.getPublicExponent());
	}

	@Test
	void shouldDecodeX509Certificate() throws IOException {

		String content = new String(FileCopyUtils.copyToByteArray(new File(this.workdir, "ca/certs/ca.cert.pem")));

		PemObject pemObject = PemObject.parseFirst(content);

		assertThat(pemObject.isCertificate()).isTrue();
		assertThat(pemObject.getCertificate().getSubjectDN().getName()).contains("O=spring-cloud-vault-config");
	}

}
