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

package org.springframework.vault.core;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.vault.support.VaultResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VaultTransitTemplate} charset handling.
 *
 * @author Tim Jungk
 */
class VaultTransitTemplateUnitTests {

	private final VaultOperations operations = mock(VaultOperations.class);

	private final VaultTransitTemplate transit = new VaultTransitTemplate(this.operations, "transit");


	@Test
	void shouldKeepDefaultCharsetForExistingStringMethods() {

		String plaintext = "Schlüssel";
		String encoded = Base64.getEncoder().encodeToString(plaintext.getBytes(Charset.defaultCharset()));
		VaultResponse response = new VaultResponse();
		response.setData(Map.of("ciphertext", "vault:v1:encrypted", "plaintext", encoded));
		when(this.operations.write("transit/encrypt/key", Map.of("plaintext", encoded)))
				.thenReturn(response);
		when(this.operations.write("transit/decrypt/key", Map.of("ciphertext", "vault:v1:encrypted")))
				.thenReturn(response);

		assertThat(this.transit.encrypt("key", plaintext)).isEqualTo("vault:v1:encrypted");
		assertThat(this.transit.decrypt("key", "vault:v1:encrypted")).isEqualTo(plaintext);
	}

}
