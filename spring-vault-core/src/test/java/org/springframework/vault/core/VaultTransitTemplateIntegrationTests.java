/*
 * Copyright 2016-2018 the original author or authors.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.Ciphertext;
import org.springframework.vault.support.Plaintext;
import org.springframework.vault.support.VaultDecryptionResult;
import org.springframework.vault.support.VaultEncryptionResult;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultTransitContext;
import org.springframework.vault.support.VaultTransitKey;
import org.springframework.vault.support.VaultTransitKeyConfiguration;
import org.springframework.vault.support.VaultTransitKeyCreationRequest;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Integration tests for {@link VaultTransitTemplate} through
 * {@link VaultTransitOperations}.
 *
 * @author Mark Paluch
 * @author Praveendra Singh
 * @author Mikko Koli
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
public class VaultTransitTemplateIntegrationTests extends IntegrationTestSupport {

	private static final Version BATCH_INTRODUCED_IN_VERSION = Version.parse("0.6.5");

	@Autowired
	private VaultOperations vaultOperations;
	private VaultTransitOperations transitOperations;

	private Version vaultVersion;

	@Before
	public void before() {

		transitOperations = vaultOperations.opsForTransit();

		if (!vaultOperations.opsForSys().getMounts().containsKey("transit/")) {
			vaultOperations.opsForSys().mount("transit", VaultMount.create("transit"));
		}

		vaultVersion = prepare().getVersion();

		removeKeys();
	}

	@After
	public void tearDown() {
		removeKeys();
	}

	private void deleteKey(String keyName) {

		try {
			transitOperations.configureKey(keyName, VaultTransitKeyConfiguration
					.builder().deletionAllowed(true).build());
		}
		catch (Exception e) {
		}

		try {
			transitOperations.deleteKey(keyName);
		}
		catch (Exception e) {
		}
	}

	private void removeKeys() {

		if (prepare().getVersion().isGreaterThanOrEqualTo(Version.parse("0.6.4"))) {
			List<String> keys = vaultOperations.opsForTransit().getKeys();
			for (String keyName : keys) {
				deleteKey(keyName);
			}
		}
		else {
			deleteKey("mykey");
			deleteKey("derived");
		}
	}

	@Test
	public void createKeyShouldCreateKey() {

		transitOperations.createKey("mykey");

		VaultTransitKey mykey = transitOperations.getKey("mykey");

		assertThat(mykey.getType()).startsWith("aes");

		assertThat(mykey.getName()).isEqualTo("mykey");
		assertThat(mykey.isDeletionAllowed()).isFalse();
		assertThat(mykey.isDerived()).isFalse();
		assertThat(mykey.getMinDecryptionVersion()).isEqualTo(1);
		assertThat(mykey.isLatestVersion()).isTrue();

		if (vaultVersion.isGreaterThanOrEqualTo(Version.parse("0.7.0"))) {

			assertThat(mykey.supportsDecryption()).isTrue();
			assertThat(mykey.supportsEncryption()).isTrue();
			assertThat(mykey.supportsDerivation()).isTrue();
			assertThat(mykey.supportsSigning()).isFalse();
		}
	}

	@Test
	public void createKeyShouldCreateKeyWithOptions() {

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
	public void shouldConfigureKey() {

		transitOperations.createKey("mykey");
		transitOperations.rotate("mykey");
		transitOperations.rotate("mykey");

		VaultTransitKeyConfiguration configuration = VaultTransitKeyConfiguration
				.builder().deletionAllowed(true).minDecryptionVersion(1)
				.minEncryptionVersion(2).build();

		transitOperations.configureKey("mykey", configuration);

		VaultTransitKey mykey = transitOperations.getKey("mykey");

		assertThat(mykey.getMinDecryptionVersion()).isEqualTo(1);

		if (vaultVersion.isGreaterThanOrEqualTo(Version.parse("0.8.0"))) {
			assertThat(mykey.getMinEncryptionVersion()).isEqualTo(2);
		}
		else {
			assertThat(mykey.getMinEncryptionVersion()).isEqualTo(0);
		}
	}

	@Test
	public void shouldEnumerateKey() {

		assumeTrue(prepare().getVersion().isGreaterThanOrEqualTo(Version.parse("0.6.4")));

		assertThat(transitOperations.getKeys()).isEmpty();

		transitOperations.createKey("mykey");

		assertThat(transitOperations.getKeys()).contains("mykey");
	}

	@Test
	public void getKeyShouldReturnNullIfKeyNotExists() {

		VaultTransitKey key = transitOperations.getKey("hello-world");
		assertThat(key).isNull();
	}

	@Test
	public void deleteKeyShouldFailIfKeyNotExists() {

		try {
			transitOperations.deleteKey("hello-world");
			fail("Missing VaultException");
		}
		catch (VaultException e) {
			assertThat(e).hasMessageContaining("Status 400");
		}
	}

	@Test
	public void deleteKeyShouldDeleteKey() {

		transitOperations.createKey("mykey");
		transitOperations.configureKey("mykey", VaultTransitKeyConfiguration.builder()
				.deletionAllowed(true).build());
		transitOperations.deleteKey("mykey");

		assertThat(transitOperations.getKey("mykey")).isNull();
	}

	@Test
	public void encryptShouldCreateCiphertext() {

		transitOperations.createKey("mykey");

		String ciphertext = transitOperations.encrypt("mykey", "hello-world");
		assertThat(ciphertext).startsWith("vault:v");
	}

	@Test
	public void encryptShouldCreateCiphertextWithNonceAndContext() {

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
	public void encryptShouldEncryptEmptyValues() {

		assumeTrue(vaultVersion.isGreaterThanOrEqualTo(BATCH_INTRODUCED_IN_VERSION));

		transitOperations.createKey("mykey", VaultTransitKeyCreationRequest.builder()
				.convergentEncryption(true).derived(true).build());

		VaultTransitContext context = VaultTransitContext.builder()
				.context("blubb".getBytes()) //
				.nonce("123456789012".getBytes()) //
				.build();

		Ciphertext ciphertext = transitOperations.encrypt("mykey",
				Plaintext.of("").with(context));

		assertThat(ciphertext.getCiphertext()).startsWith("vault:v1:");
		assertThat(ciphertext.getContext()).isEqualTo(context);
	}

	@Test
	public void encryptShouldCreateWrappedCiphertextWithNonceAndContext() {

		transitOperations.createKey("mykey", VaultTransitKeyCreationRequest.builder()
				.convergentEncryption(true).derived(true).build());

		VaultTransitContext context = VaultTransitContext.builder()
				.context("blubb".getBytes()) //
				.nonce("123456789012".getBytes()) //
				.build();

		Ciphertext ciphertext = transitOperations.encrypt("mykey",
				Plaintext.of("hello-world").with(context));

		assertThat(ciphertext.getCiphertext()).startsWith("vault:v1:");
		assertThat(ciphertext.getContext()).isEqualTo(context);
	}

	@Test
	public void decryptShouldCreatePlaintext() {

		transitOperations.createKey("mykey");

		String ciphertext = transitOperations.encrypt("mykey", "hello-world");
		String plaintext = transitOperations.decrypt("mykey", ciphertext);

		assertThat(plaintext).isEqualTo("hello-world");
	}

	@Test
	public void decryptShouldCreatePlaintextWithNonceAndContext() {

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
	public void decryptShouldCreateWrappedPlaintextWithNonceAndContext() {

		transitOperations.createKey("mykey", VaultTransitKeyCreationRequest.builder()
				.convergentEncryption(true).derived(true).build());

		VaultTransitContext context = VaultTransitContext.builder() //
				.context("blubb".getBytes()) //
				.nonce("123456789012".getBytes()) //
				.build();

		Ciphertext ciphertext = transitOperations.encrypt("mykey",
				Plaintext.of("hello-world").with(context));
		Plaintext plaintext = transitOperations.decrypt("mykey", ciphertext);

		assertThat(plaintext.asString()).isEqualTo("hello-world");
		assertThat(plaintext.getContext()).isEqualTo(context);
	}

	@Test
	public void encryptAndRewrapShouldCreateCiphertext() {

		transitOperations.createKey("mykey");

		String ciphertext = transitOperations.encrypt("mykey", "hello-world");
		transitOperations.rotate("mykey");

		String rewrapped = transitOperations.rewrap("mykey", ciphertext);

		assertThat(rewrapped).startsWith("vault:v2:");
	}

	@Test
	public void shouldEncryptBinaryPlaintext() {

		transitOperations.createKey("mykey");

		byte[] plaintext = new byte[] { 1, 2, 3, 4, 5 };

		String ciphertext = transitOperations.encrypt("mykey", plaintext,
				VaultTransitContext.empty());

		byte[] decrypted = transitOperations.decrypt("mykey", ciphertext,
				VaultTransitContext.empty());

		assertThat(decrypted).isEqualTo(plaintext);
	}

	@Test
	public void encryptAndRewrapShouldCreateCiphertextWithNonceAndContext() {

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

	@Test
	public void shouldBatchEncrypt() {

		assumeTrue(prepare().getVersion().isGreaterThanOrEqualTo(
				BATCH_INTRODUCED_IN_VERSION));

		transitOperations.createKey("mykey");

		List<VaultEncryptionResult> encrypted = transitOperations.encrypt("mykey",
				Arrays.asList(Plaintext.of("one"), Plaintext.of("two")));

		assertThat(encrypted.get(0).get().getCiphertext()).startsWith("vault:");
		assertThat(encrypted.get(1).get().getCiphertext()).startsWith("vault:");
	}

	@Test
	public void shouldBatchDecrypt() {

		assumeTrue(prepare().getVersion().isGreaterThanOrEqualTo(
				BATCH_INTRODUCED_IN_VERSION));

		transitOperations.createKey("mykey");

		Ciphertext one = transitOperations.encrypt("mykey", Plaintext.of("one"));
		Ciphertext two = transitOperations.encrypt("mykey", Plaintext.of("two"));

		Plaintext plainOne = transitOperations.decrypt("mykey", one);
		Plaintext plainTwo = transitOperations.decrypt("mykey", two);

		List<VaultDecryptionResult> decrypted = transitOperations.decrypt("mykey",
				Arrays.asList(one, two));

		assertThat(decrypted.get(0).get()).isEqualTo(plainOne);
		assertThat(decrypted.get(0).getAsString()).isEqualTo("one");
		assertThat(decrypted.get(1).get()).isEqualTo(plainTwo);
		assertThat(decrypted.get(1).getAsString()).isEqualTo("two");
	}

	@Test
	public void shouldBatchEncryptWithContext() {

		assumeTrue(prepare().getVersion().isGreaterThanOrEqualTo(
				BATCH_INTRODUCED_IN_VERSION));

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder() //
				.derived(true) //
				.build();

		transitOperations.createKey("mykey", request);

		Plaintext one = Plaintext.of("one").with(
				VaultTransitContext.builder().context("oneContext".getBytes()).build());

		Plaintext two = Plaintext.of("two").with(
				VaultTransitContext.builder().context("twoContext".getBytes()).build());

		List<VaultEncryptionResult> encrypted = transitOperations.encrypt("mykey",
				Arrays.asList(one, two));

		assertThat(encrypted.get(0).get().getContext()).isEqualTo(one.getContext());
		assertThat(encrypted.get(1).get().getContext()).isEqualTo(two.getContext());
	}

	@Test
	public void shouldBatchDecryptWithContext() {

		assumeTrue(prepare().getVersion().isGreaterThanOrEqualTo(
				BATCH_INTRODUCED_IN_VERSION));

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder() //
				.derived(true) //
				.build();

		transitOperations.createKey("mykey", request);

		Plaintext one = Plaintext.of("one").with(
				VaultTransitContext.builder().context("oneContext".getBytes()).build());

		Plaintext two = Plaintext.of("two").with(
				VaultTransitContext.builder().context("twoContext".getBytes()).build());

		List<VaultEncryptionResult> encrypted = transitOperations.encrypt("mykey",
				Arrays.asList(one, two));
		List<VaultDecryptionResult> decrypted = transitOperations.decrypt("mykey",
				Arrays.asList(encrypted.get(0).get(), encrypted.get(1).get()));

		assertThat(decrypted.get(0).get()).isEqualTo(one);
		assertThat(decrypted.get(1).get()).isEqualTo(two);
	}

	@Test
	public void shouldBatchDecryptWithWrongContext() {

		assumeTrue(prepare().getVersion().isGreaterThanOrEqualTo(
				BATCH_INTRODUCED_IN_VERSION));

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder() //
				.derived(true) //
				.build();

		transitOperations.createKey("mykey", request);

		Plaintext one = Plaintext.of("one").with(
				VaultTransitContext.builder().context("oneContext".getBytes()).build());

		Plaintext two = Plaintext.of("two").with(
				VaultTransitContext.builder().context("twoContext".getBytes()).build());

		List<VaultEncryptionResult> encrypted = transitOperations.encrypt("mykey",
				Arrays.asList(one, two));

		Ciphertext encryptedOne = encrypted.get(0).get();
		Ciphertext decryptedTwo = encrypted.get(1).get();

		Ciphertext tampered = decryptedTwo.with(encryptedOne.getContext());

		List<VaultDecryptionResult> decrypted = transitOperations.decrypt("mykey",
				Arrays.asList(encryptedOne, tampered));

		assertThat(decrypted.get(0).get()).isEqualTo(one);
		assertThat(decrypted.get(1).isSuccessful()).isEqualTo(false);
		assertThat(decrypted.get(1).getCause()).isInstanceOf(VaultException.class);
	}

	@Test
	public void shouldBatchDecryptEmptyPlaintext() {

		assumeTrue(vaultVersion.isGreaterThanOrEqualTo(BATCH_INTRODUCED_IN_VERSION));

		transitOperations.createKey("mykey");

		Ciphertext empty = transitOperations.encrypt("mykey", Plaintext.empty());

		List<VaultDecryptionResult> decrypted = transitOperations.decrypt("mykey",
				Collections.singletonList(empty));

		assertThat(decrypted.get(0).getAsString()).isEqualTo("");
	}

	@Test
	public void shouldBatchDecryptEmptyPlaintextWithContext() {

		assumeTrue(vaultVersion.isGreaterThanOrEqualTo(BATCH_INTRODUCED_IN_VERSION));

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder() //
				.derived(true) //
				.build();

		transitOperations.createKey("mykey", request);

		Plaintext empty = Plaintext.empty().with(
				VaultTransitContext.builder().context("oneContext".getBytes()).build());

		List<VaultEncryptionResult> encrypted = transitOperations.encrypt("mykey",
				Collections.singletonList(empty));
		List<VaultDecryptionResult> decrypted = transitOperations.decrypt("mykey",
				Collections.singletonList(encrypted.get(0).get()));

		assertThat(decrypted.get(0).get()).isEqualTo(empty);
	}
}
