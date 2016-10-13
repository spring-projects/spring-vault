/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.vault.core;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.client.VaultException;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultTransitContext;
import org.springframework.vault.support.VaultTransitKey;
import org.springframework.vault.support.VaultTransitKeyConfiguration;
import org.springframework.vault.support.VaultTransitKeyCreationRequest;
import org.springframework.vault.util.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Integration tests for {@link VaultTransitTemplate} through
 * {@link VaultTransitOperations}.
 *
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
public class VaultTransitTemplateIntegrationTests extends IntegrationTestSupport {

	@Autowired
	private VaultOperations vaultOperations;
	private VaultTransitOperations transitOperations;

	@Before
	public void before() throws Exception {
		transitOperations = vaultOperations.opsForTransit();

		if (!vaultOperations.opsForSys().getMounts().containsKey("transit/")) {
			vaultOperations.opsForSys().mount("transit", VaultMount.create("transit"));
		}

		try {
			transitOperations.configureKey("mykey", VaultTransitKeyConfiguration
					.builder().deletionAllowed(true).build());
		}
		catch (Exception e) {
		}

		try {
			transitOperations.deleteKey("mykey");
		}
		catch (Exception e) {
		}
	}

	@Test
	public void createKeyShouldCreateKey() throws Exception {

		transitOperations.createKey("mykey");

		VaultTransitKey mykey = transitOperations.getKey("mykey");

		assertThat(mykey.getType()).startsWith("aes");

		assertThat(mykey.getName()).isEqualTo("mykey");
		assertThat(mykey.isDeletionAllowed()).isFalse();
		assertThat(mykey.isDerived()).isFalse();
		assertThat(mykey.getMinDecryptionVersion()).isEqualTo(1);
		assertThat(mykey.isLatestVersion()).isTrue();
	}

	@Test
	public void createKeyShouldCreateKeyWithOptions() throws Exception {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder() //
				.convergentEncryption(true) //
				.derived(true) //
				.build();

		transitOperations.createKey("mykey", request);

		VaultTransitKey mykey = transitOperations.getKey("mykey");

		assertThat(mykey.getName()).isEqualTo("mykey");
		assertThat(mykey.isDeletionAllowed()).isFalse();
		assertThat(mykey.isDerived()).isTrue();
		assertThat(mykey.getMinDecryptionVersion()).isEqualTo(1);
		assertThat(mykey.isLatestVersion()).isTrue();
	}

	@Test
	public void getKeyShouldReturnNullIfKeyNotExists() throws Exception {

		VaultTransitKey key = transitOperations.getKey("hello-world");
		assertThat(key).isNull();
	}

	@Test
	public void deleteKeyShouldFailIfKeyNotExists() throws Exception {

		try {
			transitOperations.deleteKey("hello-world");
			fail("Missing VaultException");
		}
		catch (VaultException e) {
			assertThat(e).hasMessageContaining("Status 400");
		}
	}

	@Test
	public void deleteKeyShouldDeleteKey() throws Exception {

		transitOperations.createKey("mykey");
		transitOperations.configureKey("mykey", VaultTransitKeyConfiguration.builder()
				.deletionAllowed(true).build());
		transitOperations.deleteKey("mykey");

		assertThat(transitOperations.getKey("mykey")).isNull();
	}

	@Test
	public void encryptShouldCreateCiphertext() throws Exception {

		transitOperations.createKey("mykey");

		String ciphertext = transitOperations.encrypt("mykey", "hello-world");
		assertThat(ciphertext).startsWith("vault:v");
	}

	@Test
	public void encryptShouldCreateCiphertextWithNonceAndContext() throws Exception {

		transitOperations.createKey("mykey", VaultTransitKeyCreationRequest.builder()
				.convergentEncryption(true).derived(true).build());

		VaultTransitContext transitRequest = VaultTransitContext.builder()
				.context("blubb".getBytes()) //
				.nonce("123456789012".getBytes()) //
				.build();

		String ciphertext = transitOperations.encrypt("mykey", "hello-world".getBytes(),
				transitRequest);
		assertThat(ciphertext).startsWith("vault:v1:");
	}

	@Test
	public void decryptShouldCreatePlaintext() throws Exception {

		transitOperations.createKey("mykey");

		String ciphertext = transitOperations.encrypt("mykey", "hello-world");
		String plaintext = transitOperations.decrypt("mykey", ciphertext);

		assertThat(plaintext).isEqualTo("hello-world");
	}

	@Test
	public void decryptShouldCreatePlaintextWithNonceAndContext() throws Exception {

		transitOperations.createKey("mykey", VaultTransitKeyCreationRequest.builder()
				.convergentEncryption(true).derived(true).build());

		VaultTransitContext transitRequest = VaultTransitContext.builder() //
				.context("blubb".getBytes()) //
				.nonce("123456789012".getBytes()) //
				.build();

		String ciphertext = transitOperations.encrypt("mykey", "hello-world".getBytes(),
				transitRequest);

		byte[] plaintext = transitOperations.decrypt("mykey", ciphertext, transitRequest);
		assertThat(new String(plaintext)).isEqualTo("hello-world");
	}

	@Test
	public void encryptAndRewrapShouldCreateCiphertext() throws Exception {

		transitOperations.createKey("mykey");

		String ciphertext = transitOperations.encrypt("mykey", "hello-world");
		transitOperations.rotate("mykey");

		String rewrapped = transitOperations.rewrap("mykey", ciphertext);

		assertThat(rewrapped).startsWith("vault:v2:");
	}

	@Test
	public void encryptAndRewrapShouldCreateCiphertextWithNonceAndContext()
			throws Exception {

		transitOperations.createKey("mykey", VaultTransitKeyCreationRequest.builder()
				.convergentEncryption(true).derived(true).build());

		VaultTransitContext transitRequest = VaultTransitContext.builder() //
				.context("blubb".getBytes()) //
				.nonce("123456789012".getBytes()) //
				.build();

		String ciphertext = transitOperations.encrypt("mykey", "hello-world".getBytes(),
				transitRequest);
		transitOperations.rotate("mykey");

		String rewrapped = transitOperations.rewrap("mykey", ciphertext, transitRequest);
		assertThat(rewrapped).startsWith("vault:v2");
	}
}
