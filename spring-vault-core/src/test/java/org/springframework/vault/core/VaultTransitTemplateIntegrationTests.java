/*
 * Copyright 2016-2023 the original author or authors.
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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.Ciphertext;
import org.springframework.vault.support.Hmac;
import org.springframework.vault.support.Plaintext;
import org.springframework.vault.support.RawTransitKey;
import org.springframework.vault.support.Signature;
import org.springframework.vault.support.SignatureValidation;
import org.springframework.vault.support.TransitKeyType;
import org.springframework.vault.support.VaultDecryptionResult;
import org.springframework.vault.support.VaultEncryptionResult;
import org.springframework.vault.support.VaultHmacRequest;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultSignRequest;
import org.springframework.vault.support.VaultSignatureVerificationRequest;
import org.springframework.vault.support.VaultTransitContext;
import org.springframework.vault.support.VaultTransitKey;
import org.springframework.vault.support.VaultTransitKeyConfiguration;
import org.springframework.vault.support.VaultTransitKeyCreationRequest;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.RequiresVaultVersion;
import org.springframework.vault.util.Version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

/**
 * Integration tests for {@link VaultTransitTemplate} through
 * {@link VaultTransitOperations}.
 *
 * @author Mark Paluch
 * @author Praveendra Singh
 * @author Luander Ribeiro
 * @author Mikko Koli
 * @author Nanne Baars
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
class VaultTransitTemplateIntegrationTests extends IntegrationTestSupport {

	static final String KEY_EXPORT_INTRODUCED_IN_VERSION = "0.6.5";

	static final String BATCH_INTRODUCED_IN_VERSION = "0.6.5";

	static final String SIGN_VERIFY_INTRODUCED_IN_VERSION = "0.6.2";

	static final String ED25519_INTRODUCED_IN_VERSION = "0.7.3";

	static final String RSA3072_INTRODUCED_IN_VERSION = "1.4.0";

	static final String ECDSA521_INTRODUCED_IN_VERSION = "1.4.0";

	static final String AES256_GCM96_INTRODUCED_IN_VERSION = "1.4.0";

	@Autowired
	VaultOperations vaultOperations;

	VaultTransitOperations transitOperations;

	Version vaultVersion;

	@BeforeEach
	void before() {

		this.transitOperations = this.vaultOperations.opsForTransit();

		if (!this.vaultOperations.opsForSys().getMounts().containsKey("transit/")) {
			this.vaultOperations.opsForSys().mount("transit", VaultMount.create("transit"));
		}

		this.vaultVersion = prepare().getVersion();

		removeKeys();
	}

	@AfterEach
	void tearDown() {
		removeKeys();
	}

	private void deleteKey(String keyName) {

		try {
			this.transitOperations.configureKey(keyName,
					VaultTransitKeyConfiguration.builder().deletionAllowed(true).build());
		}
		catch (Exception e) {
		}

		try {
			this.transitOperations.deleteKey(keyName);
		}
		catch (Exception e) {
		}
	}

	private void removeKeys() {

		if (this.vaultVersion.isGreaterThanOrEqualTo(Version.parse("0.6.4"))) {
			List<String> keys = this.vaultOperations.opsForTransit().getKeys();
			for (String keyName : keys) {
				deleteKey(keyName);
			}
		}
		else {
			deleteKey("mykey");
			deleteKey("export");
			deleteKey("ecdsa-key");
			deleteKey("ed-key");
			deleteKey("rsa-3072-key");
			deleteKey("ecdsa-p521-key");
			deleteKey("aes256-gcm96-key");
		}
	}

	@Test
	void createKeyShouldCreateKey() {

		this.transitOperations.createKey("mykey");

		VaultTransitKey mykey = this.transitOperations.getKey("mykey");

		assertThat(mykey.getType()).startsWith("aes");

		assertThat(mykey.getName()).isEqualTo("mykey");
		assertThat(mykey.isDeletionAllowed()).isFalse();
		assertThat(mykey.isDerived()).isFalse();
		assertThat(mykey.getMinDecryptionVersion()).isEqualTo(1);
		assertThat(mykey.getLatestVersion()).isEqualTo(1);

		if (this.vaultVersion.isGreaterThanOrEqualTo(Version.parse("0.7.0"))) {

			assertThat(mykey.supportsDecryption()).isTrue();
			assertThat(mykey.supportsEncryption()).isTrue();
			assertThat(mykey.supportsDerivation()).isTrue();
			assertThat(mykey.supportsSigning()).isFalse();
		}
	}

	@Test
	@RequiresVaultVersion("0.6.4")
	void createKeyShouldCreateEcDsaKey() {

		String keyName = createEcdsaP256Key();

		VaultTransitKey mykey = this.transitOperations.getKey(keyName);

		assertThat(mykey.getType()).startsWith("ecdsa");
		assertThat(mykey.getKeys()).isNotEmpty();
	}

	@Test
	@RequiresVaultVersion(ED25519_INTRODUCED_IN_VERSION)
	void createKeyShouldCreateEdKey() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.ofKeyType("ed25519");

		this.transitOperations.createKey("ed-key", request);

		VaultTransitKey mykey = this.transitOperations.getKey("ed-key");

		assertThat(mykey.getType()).startsWith("ed");
		assertThat(mykey.getKeys()).isNotEmpty();
	}

	@Test
	@RequiresVaultVersion(ECDSA521_INTRODUCED_IN_VERSION)
	void createKeyShouldCreateEcdsaKey() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.ofKeyType("ecdsa-p521");

		this.transitOperations.createKey("ecdsa-p521-key", request);

		VaultTransitKey mykey = this.transitOperations.getKey("ecdsa-p521-key");

		assertThat(mykey.getType()).isEqualTo("ecdsa-p521");
		assertThat(mykey.getKeys()).isNotEmpty();
	}

	@Test
	@RequiresVaultVersion(RSA3072_INTRODUCED_IN_VERSION)
	void createKeyShouldCreateRsa3072Key() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.ofKeyType("rsa-3072");

		this.transitOperations.createKey("rsa-3072-key", request);

		VaultTransitKey mykey = this.transitOperations.getKey("rsa-3072-key");

		assertThat(mykey.getType()).isEqualTo("rsa-3072");
		assertThat(mykey.getKeys()).isNotEmpty();
	}

	@Test
	@RequiresVaultVersion(AES256_GCM96_INTRODUCED_IN_VERSION)
	void createKeyShouldCreateAes256Gcm96Key() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.ofKeyType("aes256-gcm96");

		this.transitOperations.createKey("aes256-gcm96-key", request);

		VaultTransitKey mykey = this.transitOperations.getKey("aes256-gcm96-key");

		assertThat(mykey.getType()).isEqualTo("aes256-gcm96");
		assertThat(mykey.getKeys()).isNotEmpty();
	}

	@Test
	void createKeyShouldCreateKeyWithOptions() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder() //
			.convergentEncryption(true) //
			.derived(true) //
			.build();

		this.transitOperations.createKey("mykey", request);

		VaultTransitKey mykey = this.transitOperations.getKey("mykey");

		assertThat(mykey.getName()).isEqualTo("mykey");
		assertThat(mykey.isDeletionAllowed()).isFalse();
		assertThat(mykey.isDerived()).isTrue();
		assertThat(mykey.getMinDecryptionVersion()).isEqualTo(1);
		assertThat(mykey.getLatestVersion()).isEqualTo(1);
		assertThat(mykey.supportsConvergentEncryption()).isTrue();
		assertThat(mykey.getConvergentVersion()).isEqualTo(-1);
	}

	@Test
	void createKeyWithPlaintextBackupOption() {
		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder() //
			.allowPlaintextBackup(true) //
			.build();

		this.transitOperations.createKey("mykey", request);

		VaultTransitKey mykey = this.transitOperations.getKey("mykey");

		assertThat(mykey.getName()).isEqualTo("mykey");
		assertThat(mykey.allowPlaintextBackup()).isTrue();
	}

	@Test
	void shouldConfigureKey() {

		this.transitOperations.createKey("mykey");
		this.transitOperations.rotate("mykey");
		this.transitOperations.rotate("mykey");

		VaultTransitKeyConfiguration configuration = VaultTransitKeyConfiguration.builder()
			.deletionAllowed(true)
			.minDecryptionVersion(1)
			.minEncryptionVersion(2)
			.build();

		this.transitOperations.configureKey("mykey", configuration);

		VaultTransitKey mykey = this.transitOperations.getKey("mykey");

		assertThat(mykey.getMinDecryptionVersion()).isEqualTo(1);

		if (this.vaultVersion.isGreaterThanOrEqualTo(Version.parse("0.8.0"))) {
			assertThat(mykey.getMinEncryptionVersion()).isEqualTo(2);
		}
		else {
			assertThat(mykey.getMinEncryptionVersion()).isEqualTo(0);
		}
	}

	@Test
	@RequiresVaultVersion("0.6.4")
	void shouldEnumerateKey() {

		assertThat(this.transitOperations.getKeys()).isEmpty();

		this.transitOperations.createKey("mykey");

		assertThat(this.transitOperations.getKeys()).contains("mykey");
	}

	@Test
	void getKeyShouldReturnNullIfKeyNotExists() {

		VaultTransitKey key = this.transitOperations.getKey("hello-world");
		assertThat(key).isNull();
	}

	@Test
	void deleteKeyShouldFailIfKeyNotExists() {

		try {
			this.transitOperations.deleteKey("hello-world");
			fail("Missing VaultException");
		}
		catch (VaultException e) {
			assertThat(e).hasMessageContaining("Status 400");
		}
	}

	@Test
	void deleteKeyShouldDeleteKey() {

		this.transitOperations.createKey("mykey");
		this.transitOperations.configureKey("mykey",
				VaultTransitKeyConfiguration.builder().deletionAllowed(true).build());
		this.transitOperations.deleteKey("mykey");

		assertThat(this.transitOperations.getKey("mykey")).isNull();
	}

	@Test
	void encryptShouldCreateCiphertext() {

		this.transitOperations.createKey("mykey");

		String ciphertext = this.transitOperations.encrypt("mykey", "hello-world");
		assertThat(ciphertext).startsWith("vault:v");
	}

	private static Stream<Arguments> encryptWithKeyVersion() {
		return Stream.of(Arguments.of(1, 1, "v1"), Arguments.of(2, 2, "v2"), Arguments.of(1, 2, ""),
				Arguments.of(2, 1, "v1"), Arguments.of("2", "0", "v2"));
	}

	@ParameterizedTest
	@MethodSource
	void encryptWithKeyVersion(int keyVersion, int usedKeyVersionWhileEncrypting, String expectedKeyPrefix) {
		this.transitOperations.createKey("mykey", VaultTransitKeyCreationRequest.builder().build());
		// rotate the key to get the right version
		IntStream.range(0, keyVersion - 1).forEach(__ -> this.transitOperations.rotate("mykey"));

		VaultTransitContext transitRequest = VaultTransitContext.builder()
			.keyVersion(usedKeyVersionWhileEncrypting)
			.build();

		try {
			String ciphertext = this.transitOperations.encrypt("mykey", "hello-world".getBytes(), transitRequest);
			assertThat(ciphertext).startsWith("vault:%s:".formatted(expectedKeyPrefix));
		}
		catch (Exception e) {
			assertThat(expectedKeyPrefix).isNullOrEmpty();
		}
	}

	@Test
	@RequiresVaultVersion(BATCH_INTRODUCED_IN_VERSION)
	void encryptShouldEncryptEmptyValues() {

		this.transitOperations.createKey("mykey",
				VaultTransitKeyCreationRequest.builder().convergentEncryption(true).derived(true).build());

		VaultTransitContext context = VaultTransitContext.builder()
			.context("blubb".getBytes()) //
			.nonce("123456789012".getBytes()) //
			.build();

		Ciphertext ciphertext = this.transitOperations.encrypt("mykey", Plaintext.of("").with(context));

		assertThat(ciphertext.getCiphertext()).startsWith("vault:v1:");
		assertThat(ciphertext.getContext()).isEqualTo(context);
	}

	@Test
	void encryptShouldCreateWrappedCiphertextWithNonceAndContext() {

		this.transitOperations.createKey("mykey",
				VaultTransitKeyCreationRequest.builder().convergentEncryption(true).derived(true).build());

		VaultTransitContext context = VaultTransitContext.builder()
			.context("blubb".getBytes()) //
			.nonce("123456789012".getBytes()) //
			.build();

		Ciphertext ciphertext = this.transitOperations.encrypt("mykey", Plaintext.of("hello-world").with(context));

		assertThat(ciphertext.getCiphertext()).startsWith("vault:v1:");
		assertThat(ciphertext.getContext()).isEqualTo(context);
	}

	@Test
	void decryptShouldCreatePlaintext() {

		this.transitOperations.createKey("mykey");

		String ciphertext = this.transitOperations.encrypt("mykey", "hello-world");
		String plaintext = this.transitOperations.decrypt("mykey", ciphertext);

		assertThat(plaintext).isEqualTo("hello-world");
	}

	private static Stream<Arguments> decryptWithKeyVersion() {
		return Stream.of(Arguments.of(1, 1, true), Arguments.of(2, 2, true), Arguments.of(1, 2, false),
				Arguments.of(2, 1, true), Arguments.of("2", "0", true));
	}

	@ParameterizedTest
	@MethodSource
	void decryptWithKeyVersion(int keyVersion, int usedKeyVersionWhileEncrypting, boolean shouldPass) {
		this.transitOperations.createKey("mykey");
		// rotate the key to get the right version
		IntStream.range(0, keyVersion - 1).forEach(__ -> this.transitOperations.rotate("mykey"));

		VaultTransitContext transitRequest = VaultTransitContext.builder()
			.keyVersion(usedKeyVersionWhileEncrypting)
			.build();

		try {
			String ciphertext = this.transitOperations
				.encrypt("mykey", Plaintext.of("hello-world").with(transitRequest))
				.getCiphertext();
			String plaintext = Plaintext.of(this.transitOperations.decrypt("mykey", ciphertext, transitRequest))
				.asString();

			assertThat(shouldPass).isTrue();
			assertThat(plaintext).isEqualTo("hello-world");

		}
		catch (VaultException e) {
			assertThat(shouldPass).isFalse();
		}
	}

	@Test
	void decryptShouldCreatePlaintextWithNonceAndContext() {

		this.transitOperations.createKey("mykey",
				VaultTransitKeyCreationRequest.builder().convergentEncryption(true).derived(true).build());

		VaultTransitContext transitRequest = VaultTransitContext.builder() //
			.context("blubb".getBytes()) //
			.nonce("123456789012".getBytes()) //
			.build();

		String ciphertext = this.transitOperations.encrypt("mykey", "hello-world".getBytes(), transitRequest);

		byte[] plaintext = this.transitOperations.decrypt("mykey", ciphertext, transitRequest);
		assertThat(new String(plaintext)).isEqualTo("hello-world");
	}

	@Test
	void decryptShouldCreateWrappedPlaintextWithNonceAndContext() {

		this.transitOperations.createKey("mykey",
				VaultTransitKeyCreationRequest.builder().convergentEncryption(true).derived(true).build());

		VaultTransitContext context = VaultTransitContext.builder() //
			.context("blubb".getBytes()) //
			.nonce("123456789012".getBytes()) //
			.build();

		Ciphertext ciphertext = this.transitOperations.encrypt("mykey", Plaintext.of("hello-world").with(context));
		Plaintext plaintext = this.transitOperations.decrypt("mykey", ciphertext);

		assertThat(plaintext.asString()).isEqualTo("hello-world");
		assertThat(plaintext.getContext()).isEqualTo(context);
	}

	@Test
	void encryptAndRewrapShouldCreateCiphertext() {

		this.transitOperations.createKey("mykey");

		String ciphertext = this.transitOperations.encrypt("mykey", "hello-world");
		this.transitOperations.rotate("mykey");

		String rewrapped = this.transitOperations.rewrap("mykey", ciphertext);

		assertThat(rewrapped).startsWith("vault:v2:");
	}

	@Test
	void shouldEncryptBinaryPlaintext() {

		this.transitOperations.createKey("mykey");

		byte[] plaintext = new byte[] { 1, 2, 3, 4, 5 };

		String ciphertext = this.transitOperations.encrypt("mykey", plaintext, VaultTransitContext.empty());

		byte[] decrypted = this.transitOperations.decrypt("mykey", ciphertext, VaultTransitContext.empty());

		assertThat(decrypted).isEqualTo(plaintext);
	}

	@Test
	void encryptAndRewrapShouldCreateCiphertextWithNonceAndContext() {

		this.transitOperations.createKey("mykey",
				VaultTransitKeyCreationRequest.builder().convergentEncryption(true).derived(true).build());

		VaultTransitContext transitRequest = VaultTransitContext.builder() //
			.context("blubb".getBytes()) //
			.nonce("123456789012".getBytes()) //
			.build();

		String ciphertext = this.transitOperations.encrypt("mykey", "hello-world".getBytes(), transitRequest);
		this.transitOperations.rotate("mykey");

		String rewrapped = this.transitOperations.rewrap("mykey", ciphertext, transitRequest);
		assertThat(rewrapped).startsWith("vault:v2");
	}

	@Test
	void encryptAndRewrapInBatchShouldCreateCiphertext() {

		this.transitOperations.createKey("mykey",
				VaultTransitKeyCreationRequest.builder().convergentEncryption(true).derived(true).build());

		VaultTransitContext transitRequest = VaultTransitContext.builder() //
			.context("blubb".getBytes()) //
			.nonce("123456789012".getBytes()) //
			.build();

		String ciphertext1 = this.transitOperations.encrypt("mykey", "hello-world".getBytes(), transitRequest);
		String ciphertext2 = this.transitOperations.encrypt("mykey", "hello-vault".getBytes(), transitRequest);
		this.transitOperations.rotate("mykey");

		List<Ciphertext> batchRequest = Stream.of(ciphertext1, ciphertext2)
			.map(ct -> Ciphertext.of(ct).with(transitRequest))
			.toList();
		List<VaultEncryptionResult> rewrappedResult = this.transitOperations.rewrap("mykey", batchRequest);
		assertThat(rewrappedResult).hasSize(2).allMatch(result -> result.get().getCiphertext().startsWith("vault:v2"));
	}

	@Test
	@RequiresVaultVersion(BATCH_INTRODUCED_IN_VERSION)
	void shouldBatchEncrypt() {

		this.transitOperations.createKey("mykey");

		List<VaultEncryptionResult> encrypted = this.transitOperations.encrypt("mykey",
				Arrays.asList(Plaintext.of("one"), Plaintext.of("two")));

		assertThat(encrypted.get(0).get().getCiphertext()).startsWith("vault:");
		assertThat(encrypted.get(1).get().getCiphertext()).startsWith("vault:");
	}

	@Test
	@RequiresVaultVersion(BATCH_INTRODUCED_IN_VERSION)
	void shouldBatchDecrypt() {

		this.transitOperations.createKey("mykey");

		Ciphertext one = this.transitOperations.encrypt("mykey", Plaintext.of("one"));
		Ciphertext two = this.transitOperations.encrypt("mykey", Plaintext.of("two"));

		Plaintext plainOne = this.transitOperations.decrypt("mykey", one);
		Plaintext plainTwo = this.transitOperations.decrypt("mykey", two);

		List<VaultDecryptionResult> decrypted = this.transitOperations.decrypt("mykey", Arrays.asList(one, two));

		assertThat(decrypted.get(0).get()).isEqualTo(plainOne);
		assertThat(decrypted.get(0).getAsString()).isEqualTo("one");
		assertThat(decrypted.get(1).get()).isEqualTo(plainTwo);
		assertThat(decrypted.get(1).getAsString()).isEqualTo("two");
	}

	@Test
	@RequiresVaultVersion(BATCH_INTRODUCED_IN_VERSION)
	void shouldBatchEncryptWithContext() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder() //
			.derived(true) //
			.build();

		this.transitOperations.createKey("mykey", request);

		Plaintext one = Plaintext.of("one")
			.with(VaultTransitContext.builder().context("oneContext".getBytes()).build());

		Plaintext two = Plaintext.of("two")
			.with(VaultTransitContext.builder().context("twoContext".getBytes()).build());

		List<VaultEncryptionResult> encrypted = this.transitOperations.encrypt("mykey", Arrays.asList(one, two));

		assertThat(encrypted.get(0).get().getContext()).isEqualTo(one.getContext());
		assertThat(encrypted.get(1).get().getContext()).isEqualTo(two.getContext());
	}

	@Test
	@RequiresVaultVersion(BATCH_INTRODUCED_IN_VERSION)
	void shouldBatchDecryptWithContext() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder() //
			.derived(true) //
			.build();

		this.transitOperations.createKey("mykey", request);

		Plaintext one = Plaintext.of("one")
			.with(VaultTransitContext.builder().context("oneContext".getBytes()).build());

		Plaintext two = Plaintext.of("two")
			.with(VaultTransitContext.builder().context("twoContext".getBytes()).build());

		List<VaultEncryptionResult> encrypted = this.transitOperations.encrypt("mykey", Arrays.asList(one, two));
		List<VaultDecryptionResult> decrypted = this.transitOperations.decrypt("mykey",
				Arrays.asList(encrypted.get(0).get(), encrypted.get(1).get()));

		assertThat(decrypted.get(0).get()).isEqualTo(one);
		assertThat(decrypted.get(1).get()).isEqualTo(two);
	}

	@Test
	@RequiresVaultVersion(BATCH_INTRODUCED_IN_VERSION)
	void shouldBatchDecryptWithWrongContext() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder() //
			.derived(true) //
			.build();

		this.transitOperations.createKey("mykey", request);

		Plaintext one = Plaintext.of("one")
			.with(VaultTransitContext.builder().context("oneContext".getBytes()).build());

		Plaintext two = Plaintext.of("two")
			.with(VaultTransitContext.builder().context("twoContext".getBytes()).build());

		List<VaultEncryptionResult> encrypted = this.transitOperations.encrypt("mykey", Arrays.asList(one, two));

		Ciphertext encryptedOne = encrypted.get(0).get();
		Ciphertext decryptedTwo = encrypted.get(1).get();

		Ciphertext tampered = decryptedTwo.with(encryptedOne.getContext());

		try {
			List<VaultDecryptionResult> decrypted = this.transitOperations.decrypt("mykey",
					Arrays.asList(encryptedOne, tampered));

			assertThat(decrypted.get(0).get()).isEqualTo(one);
			assertThat(decrypted.get(1).isSuccessful()).isEqualTo(false);
			assertThat(decrypted.get(1).getCause()).isInstanceOf(VaultException.class);
		}
		catch (VaultException e) {
			assertThat(e).hasMessageContaining("error"); // Vault 1.6 behavior is
			// different
		}
	}

	@Test
	@RequiresVaultVersion(BATCH_INTRODUCED_IN_VERSION)
	void shouldBatchDecryptEmptyPlaintext() {

		this.transitOperations.createKey("mykey");

		Ciphertext empty = this.transitOperations.encrypt("mykey", Plaintext.empty());

		List<VaultDecryptionResult> decrypted = this.transitOperations.decrypt("mykey",
				Collections.singletonList(empty));

		assertThat(decrypted.get(0).getAsString()).isEqualTo("");
	}

	@Test
	@RequiresVaultVersion(BATCH_INTRODUCED_IN_VERSION)
	void shouldBatchDecryptEmptyPlaintextWithContext() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder() //
			.derived(true) //
			.build();

		this.transitOperations.createKey("mykey", request);

		Plaintext empty = Plaintext.empty()
			.with(VaultTransitContext.builder().context("oneContext".getBytes()).build());

		List<VaultEncryptionResult> encrypted = this.transitOperations.encrypt("mykey",
				Collections.singletonList(empty));
		List<VaultDecryptionResult> decrypted = this.transitOperations.decrypt("mykey",
				Collections.singletonList(encrypted.get(0).get()));

		assertThat(decrypted.get(0).get()).isEqualTo(empty);
	}

	@Test
	@RequiresVaultVersion(SIGN_VERIFY_INTRODUCED_IN_VERSION)
	void generateHmacShouldCreateHmac() {

		String keyName = createEcdsaP256Key();

		Hmac hmac = this.transitOperations.getHmac(keyName, Plaintext.of("hello-world"));
		assertThat(hmac.getHmac()).isNotEmpty();
	}

	@Test
	@RequiresVaultVersion(SIGN_VERIFY_INTRODUCED_IN_VERSION)
	void generateHmacShouldCreateHmacForRotatedKey() {

		String keyName = createEcdsaP256Key();
		this.transitOperations.rotate(keyName);

		VaultHmacRequest request = VaultHmacRequest.builder()
			.plaintext(Plaintext.of("hello-world"))
			.keyVersion(2)
			.build();

		Hmac hmac = this.transitOperations.getHmac(keyName, request);
		assertThat(hmac.getHmac()).isNotEmpty();
	}

	@Test
	@RequiresVaultVersion(SIGN_VERIFY_INTRODUCED_IN_VERSION)
	void generateHmacWithCustomAlgorithmShouldCreateHmac() {

		String keyName = createEcdsaP256Key();

		VaultHmacRequest request = VaultHmacRequest.builder()
			.plaintext(Plaintext.of("hello-world"))
			.algorithm("sha2-512")
			.build();

		Hmac hmac = this.transitOperations.getHmac(keyName, request);
		assertThat(hmac.getHmac()).isNotEmpty();
	}

	@Test
	void generateHmacWithInvalidAlgorithmShouldFail() {

		String keyName = createEcdsaP256Key();

		VaultHmacRequest request = VaultHmacRequest.builder()
			.plaintext(Plaintext.of("hello-world"))
			.algorithm("blah-512")
			.build();

		assertThatExceptionOfType(VaultException.class)
			.isThrownBy(() -> this.transitOperations.getHmac(keyName, request));
	}

	@Test
	@RequiresVaultVersion(SIGN_VERIFY_INTRODUCED_IN_VERSION)
	void signShouldCreateSignature() {

		String keyName = createEcdsaP256Key();

		Signature signature = this.transitOperations.sign(keyName, Plaintext.of("hello-world"));
		assertThat(signature.getSignature()).isNotEmpty();
	}

	@Test
	@RequiresVaultVersion(ED25519_INTRODUCED_IN_VERSION)
	void signShouldCreateSignatureUsingEd25519() {

		VaultTransitKeyCreationRequest keyCreationRequest = VaultTransitKeyCreationRequest.ofKeyType("ed25519");
		this.transitOperations.createKey("ed-key", keyCreationRequest);

		Signature signature = this.transitOperations.sign("ed-key", Plaintext.of("hello-world"));
		assertThat(signature.getSignature()).isNotEmpty();
	}

	@Test
	void signWithInvalidKeyFormatShouldFail() {

		this.transitOperations.createKey("mykey");

		assertThatExceptionOfType(VaultException.class)
			.isThrownBy(() -> this.transitOperations.sign("mykey", Plaintext.of("hello-world")));
	}

	@Test
	@RequiresVaultVersion(SIGN_VERIFY_INTRODUCED_IN_VERSION)
	void signWithCustomAlgorithmShouldCreateSignature() {

		String keyName = createEcdsaP256Key();

		Plaintext plaintext = Plaintext.of("hello-world");
		VaultSignRequest request = VaultSignRequest.builder().plaintext(plaintext).hashAlgorithm("sha2-512").build();

		Signature signature = this.transitOperations.sign(keyName, request);
		assertThat(signature.getSignature()).isNotEmpty();
	}

	@Test
	@RequiresVaultVersion(SIGN_VERIFY_INTRODUCED_IN_VERSION)
	void signAndVerifyWithPrehashedInput() {

		String keyName = createEcdsaP256Key();
		Plaintext plaintext = Plaintext.of("P8m2iUWdc4+MiKOkiqnjNUIBa3pAUuABqqU2/KdIE8s=");
		VaultSignRequest signRequest = VaultSignRequest.builder()
			.plaintext(plaintext)
			.signatureAlgorithm("pkcs1v15")
			.prehashed(true)
			.build();

		Signature signature = this.transitOperations.sign(keyName, signRequest);
		assertThat(signature.getSignature()).isNotEmpty();

		VaultSignatureVerificationRequest verifyRequest = VaultSignatureVerificationRequest.builder()
			.prehashed(true)
			.plaintext(plaintext)
			.signature(signature)
			.signatureAlgorithm("pkcs1v15")
			.build();
		SignatureValidation validation = this.transitOperations.verify(keyName, verifyRequest);
		assertThat(validation.isValid()).isTrue();
	}

	@Test
	@RequiresVaultVersion(SIGN_VERIFY_INTRODUCED_IN_VERSION)
	void signWithPrehashedAndVerifyWithoutShouldFail() {

		String keyName = createEcdsaP256Key();
		Plaintext plaintext = Plaintext.of("P8m2iUWdc4+MiKOkiqnjNUIBa3pAUuABqqU2/KdIE8s=");
		VaultSignRequest signRequest = VaultSignRequest.builder()
			.plaintext(plaintext)
			.signatureAlgorithm("pkcs1v15")
			.prehashed(true)
			.build();

		Signature signature = this.transitOperations.sign(keyName, signRequest);
		assertThat(signature.getSignature()).isNotEmpty();

		VaultSignatureVerificationRequest verifyRequest = VaultSignatureVerificationRequest.builder()
			.prehashed(false)
			.plaintext(plaintext)
			.signature(signature)
			.signatureAlgorithm("pkcs1v15")
			.build();
		SignatureValidation validation = this.transitOperations.verify(keyName, verifyRequest);
		assertThat(validation.isValid()).isFalse();
	}

	@Test
	@RequiresVaultVersion(SIGN_VERIFY_INTRODUCED_IN_VERSION)
	void shouldVerifyValidSignature() {

		String keyName = createEcdsaP256Key();

		Plaintext plaintext = Plaintext.of("hello-world");
		Signature signature = this.transitOperations.sign(keyName, plaintext);

		boolean valid = this.transitOperations.verify(keyName, plaintext, signature);
		assertThat(valid).isTrue();
	}

	@Test
	@RequiresVaultVersion(SIGN_VERIFY_INTRODUCED_IN_VERSION)
	void shouldVerifyValidHmac() {

		String keyName = createEcdsaP256Key();

		Plaintext plaintext = Plaintext.of("hello-world");
		Hmac hmac = this.transitOperations.getHmac(keyName, plaintext);

		SignatureValidation valid = this.transitOperations.verify(keyName,
				VaultSignatureVerificationRequest.create(plaintext, hmac));
		assertThat(valid).isEqualTo(SignatureValidation.valid());
	}

	@Test
	@RequiresVaultVersion(SIGN_VERIFY_INTRODUCED_IN_VERSION)
	void shouldVerifyValidSignatureWithCustomAlgorithm() {

		String keyName = createEcdsaP256Key();

		Plaintext plaintext = Plaintext.of("hello-world");
		VaultSignRequest request = VaultSignRequest.builder().plaintext(plaintext).hashAlgorithm("sha2-512").build();

		Signature signature = this.transitOperations.sign(keyName, request);

		VaultSignatureVerificationRequest verificationRequest = VaultSignatureVerificationRequest.builder()
			.hashAlgorithm("sha2-512")
			.plaintext(plaintext)
			.signature(signature)
			.build();

		SignatureValidation valid = this.transitOperations.verify(keyName, verificationRequest);
		assertThat(valid).isEqualTo(SignatureValidation.valid());
	}

	@Test
	@RequiresVaultVersion(KEY_EXPORT_INTRODUCED_IN_VERSION)
	void shouldCreateNewExportableKey() {

		VaultTransitOperations vaultTransitOperations = this.vaultOperations.opsForTransit();
		VaultTransitKeyCreationRequest vaultTransitKeyCreationRequest = VaultTransitKeyCreationRequest.builder()
			.exportable(true)
			.derived(true)
			.build();

		vaultTransitOperations.createKey("export-test", vaultTransitKeyCreationRequest);

		VaultTransitKey vaultTransitKey = vaultTransitOperations.getKey("export-test");

		assertThat(vaultTransitKey.getName()).isEqualTo("export-test");
		assertThat(vaultTransitKey.isExportable()).isTrue();
	}

	@Test
	@RequiresVaultVersion(KEY_EXPORT_INTRODUCED_IN_VERSION)
	void shouldCreateNotExportableKeyByDefault() {

		VaultTransitOperations vaultTransitOperations = this.vaultOperations.opsForTransit();

		vaultTransitOperations.createKey("no-export");

		VaultTransitKey vaultTransitKey = vaultTransitOperations.getKey("no-export");

		assertThat(vaultTransitKey.getName()).isEqualTo("no-export");
		assertThat(vaultTransitKey.isExportable()).isFalse();
	}

	@Test
	@RequiresVaultVersion(KEY_EXPORT_INTRODUCED_IN_VERSION)
	void shouldExportEncryptionKey() {

		this.vaultOperations.write("transit/keys/export", Collections.singletonMap("exportable", true));

		RawTransitKey rawTransitKey = this.transitOperations.exportKey("export", TransitKeyType.ENCRYPTION_KEY);

		assertThat(rawTransitKey.getName()).isEqualTo("export");
		assertThat(rawTransitKey.getKeys()).isNotEmpty();
		assertThat(rawTransitKey.getKeys().get("1")).isNotBlank();
	}

	@Test
	@RequiresVaultVersion(KEY_EXPORT_INTRODUCED_IN_VERSION)
	void shouldNotAllowExportSigningKey() {

		this.vaultOperations.write("transit/keys/export", Collections.singletonMap("exportable", true));

		assertThatExceptionOfType(VaultException.class)
			.isThrownBy(() -> this.transitOperations.exportKey("export", TransitKeyType.SIGNING_KEY));
	}

	@Test
	@RequiresVaultVersion(KEY_EXPORT_INTRODUCED_IN_VERSION)
	void shouldExportHmacKey() {

		this.vaultOperations.write("transit/keys/export", Collections.singletonMap("exportable", true));

		RawTransitKey rawTransitKey = this.transitOperations.exportKey("export", TransitKeyType.HMAC_KEY);

		assertThat(rawTransitKey.getName()).isEqualTo("export");
		assertThat(rawTransitKey.getKeys()).isNotEmpty();
		assertThat(rawTransitKey.getKeys().get("1")).isNotBlank();
	}

	@Test
	@RequiresVaultVersion(KEY_EXPORT_INTRODUCED_IN_VERSION)
	void shouldExportEcDsaKey() {

		VaultTransitOperations transitOperations = this.vaultOperations.opsForTransit();

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder()
			.type("ecdsa-p256")
			.exportable(true)
			.build();

		transitOperations.createKey("ecdsa-key", request);

		RawTransitKey hmacKey = transitOperations.exportKey("ecdsa-key", TransitKeyType.HMAC_KEY);
		RawTransitKey signingKey = transitOperations.exportKey("ecdsa-key", TransitKeyType.SIGNING_KEY);

		assertThat(hmacKey.getKeys()).isNotEmpty();
		assertThat(signingKey.getKeys()).isNotEmpty();
	}

	@Test
	@RequiresVaultVersion(ED25519_INTRODUCED_IN_VERSION)
	void shouldExportEdKey() {

		VaultTransitOperations transitOperations = this.vaultOperations.opsForTransit();

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder()
			.type("ed25519")
			.exportable(true)
			.build();

		transitOperations.createKey("ed-key", request);

		RawTransitKey hmacKey = transitOperations.exportKey("ed-key", TransitKeyType.HMAC_KEY);
		RawTransitKey signingKey = transitOperations.exportKey("ed-key", TransitKeyType.SIGNING_KEY);

		assertThat(hmacKey.getKeys()).isNotEmpty();
		assertThat(signingKey.getKeys()).isNotEmpty();
	}

	private String createEcdsaP256Key() {

		String keyName = "ecdsa-key";
		VaultTransitKeyCreationRequest keyCreationRequest = VaultTransitKeyCreationRequest.ofKeyType("ecdsa-p256");
		this.transitOperations.createKey(keyName, keyCreationRequest);

		return keyName;
	}

}
