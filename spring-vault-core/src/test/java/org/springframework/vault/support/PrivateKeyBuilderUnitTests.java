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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.KeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Unit tests for {@link PrivateKeyBuilder}.
 *
 * @author Alex Bremora
 */
class PrivateKeyBuilderUnitTests {

	@Test
	void createKeySpecFromPemPrivateKeyRsa2048() throws Exception {

		String content = loadFromResource("privatekey-rsa-2048.pem");
		KeySpec keySpec = PrivateKeyBuilder.create(content, "rsa");

		assertThat(keySpec).isInstanceOf(RSAPrivateCrtKeySpec.class);
	}

	@Test
	void createKeySpecFromPemPrivateKeyEc256() throws Exception {

		String content = loadFromResource("privatekey-ec-256.pem");
		KeySpec keySpec = PrivateKeyBuilder.create(content, "ec");

		assertThat(keySpec).isInstanceOf(ECPrivateKeySpec.class);
	}

	String loadFromResource(String path) throws IOException {
		Resource resource = new ClassPathResource(path);
		try (InputStream inputStream = resource.getInputStream()) {
			byte[] bytes = new byte[inputStream.available()];
			inputStream.read(bytes);
			return new String(bytes, StandardCharsets.UTF_8);
		}
	}

}
