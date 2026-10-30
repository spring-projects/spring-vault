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

import reactor.core.publisher.Mono;

import org.springframework.vault.support.VaultResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReactiveVaultTransitTemplate} charset handling.
 *
 * @author Tim Jungk
 */
class ReactiveVaultTransitTemplateUnitTests {

	private final ReactiveVaultOperations operations = mock(ReactiveVaultOperations.class);

	private final ReactiveVaultTransitTemplate transit = new ReactiveVaultTransitTemplate(this.operations, "transit");

	@ParameterizedTest
	@ValueSource(strings = {"UTF-8", "ISO-8859-1", "IBM1047"})
	void shouldEncryptUsingRequestedCharset(String charsetName) {

		Charset charset = Charset.forName(charsetName);
		String plaintext = "Schlüssel";
		Map<String, String> request = Map.of("plaintext",
				Base64.getEncoder().encodeToString(plaintext.getBytes(charset)));
		VaultResponse response = new VaultResponse();
		response.setData(Map.of("ciphertext", "vault:v1:encrypted"));
		when(this.operations.write("transit/encrypt/key", request)).thenReturn(Mono.just(response));

		assertThat(this.transit.encrypt("key", plaintext, charset).block()).isEqualTo("vault:v1:encrypted");
		verify(this.operations).write("transit/encrypt/key", request);
	}

	@ParameterizedTest
	@ValueSource(strings = {"UTF-8", "ISO-8859-1", "IBM1047"})
	void shouldDecryptUsingOriginalCharset(String charsetName) {

		Charset charset = Charset.forName(charsetName);
		VaultResponse response = new VaultResponse();
		response.setData(Map.of("plaintext",
				Base64.getEncoder().encodeToString("Schlüssel".getBytes(charset))));
		when(this.operations.write("transit/decrypt/key", Map.of("ciphertext", "vault:v1:encrypted")))
				.thenReturn(Mono.just(response));

		assertThat(this.transit.decrypt("key", "vault:v1:encrypted", charset).block()).isEqualTo("Schlüssel");
	}


	@Test
	void shouldKeepDefaultCharsetForExistingStringMethods() {

		String plaintext = "Schlüssel";
		String encoded = Base64.getEncoder().encodeToString(plaintext.getBytes(Charset.defaultCharset()));
		VaultResponse response = new VaultResponse();
		response.setData(Map.of("ciphertext", "vault:v1:encrypted", "plaintext", encoded));
		when(this.operations.write("transit/encrypt/key", Map.of("plaintext", encoded)))
				.thenReturn(Mono.just(response));
		when(this.operations.write("transit/decrypt/key", Map.of("ciphertext", "vault:v1:encrypted")))
				.thenReturn(Mono.just(response));

		assertThat(this.transit.encrypt("key", plaintext).block()).isEqualTo("vault:v1:encrypted");
		assertThat(this.transit.decrypt("key", "vault:v1:encrypted").block()).isEqualTo(plaintext);
	}

	@Test
	void shouldRejectNullCharset() {

		assertThatIllegalArgumentException()
				.isThrownBy(() -> this.transit.encrypt("key", "plaintext", (Charset) null))
				.withMessage("Charset must not be null");
		assertThatIllegalArgumentException()
				.isThrownBy(() -> this.transit.decrypt("key", "vault:v1:encrypted", (Charset) null))
				.withMessage("Charset must not be null");
		verifyNoInteractions(this.operations);
	}

}
