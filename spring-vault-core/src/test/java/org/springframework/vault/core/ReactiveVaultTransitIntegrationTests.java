/*
 * Copyright 2023-present the original author or authors.
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
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.*;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Version;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ReactiveVaultTransitTemplate} using the
 * {@code transit} secrets engine.
 *
 * @author James Luke
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
public class ReactiveVaultTransitIntegrationTests extends IntegrationTestSupport {

	@Autowired
	VaultOperations vaultOperations;

	@Autowired
	ReactiveVaultOperations reactiveVaultOperations;

	ReactiveVaultTransitOperations reactiveTransitOperations;

	Version vaultVersion;

	@BeforeEach
	void before() {

		this.reactiveTransitOperations = this.reactiveVaultOperations.opsForTransit();

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

	private Mono<Void> deleteKey(String keyName) {

		return this.reactiveTransitOperations
				.configureKey(keyName, VaultTransitKeyConfiguration.builder().deletionAllowed(true).build())
				.and(this.reactiveTransitOperations.deleteKey(keyName))
				.onErrorResume(e -> Mono.empty());
	}

	private void removeKeys() {
		reactiveTransitOperations.getKeys().flatMap(this::deleteKey).blockLast();
	}

	@Test
	void createKeyShouldCreateKey() {

		this.reactiveTransitOperations.createKey("myKey")
				.then(this.reactiveTransitOperations.getKey("myKey"))
				.as(StepVerifier::create)
				.assertNext(myKey -> {
					assertThat(myKey).isNotNull();
					assertThat(myKey.getType()).startsWith("aes");
					assertThat(myKey.getName()).isEqualTo("myKey");
					assertThat(myKey.isDeletionAllowed()).isFalse();
					assertThat(myKey.isDerived()).isFalse();
					assertThat(myKey.getMinDecryptionVersion()).isEqualTo(1);
					assertThat(myKey.getLatestVersion()).isEqualTo(1);

					if (this.vaultVersion.isGreaterThanOrEqualTo(Version.parse("0.7.0"))) {
						assertThat(myKey.supportsDecryption()).isTrue();
						assertThat(myKey.supportsEncryption()).isTrue();
						assertThat(myKey.supportsDerivation()).isTrue();
						assertThat(myKey.supportsSigning()).isFalse();
					}
				})
				.verifyComplete();
	}

	@Test
	void createKeyShouldCreateEcDsaKey() {

		createEcdsaP256Key().flatMap(keyName -> this.reactiveTransitOperations.getKey(keyName))
				.as(StepVerifier::create)
				.assertNext(myKey -> {
					assertThat(myKey).isNotNull();
					assertThat(myKey.getType()).startsWith("ecdsa");
					assertThat(myKey.getKeys()).isNotEmpty();
				})
				.verifyComplete();
	}

	@Test
	void createKeyShouldCreateEdKey() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.ofKeyType("ed25519");

		this.reactiveTransitOperations.createKey("ed-key", request)
				.then(this.reactiveTransitOperations.getKey("ed-key"))
				.as(StepVerifier::create)
				.assertNext(myKey -> {
					assertThat(myKey).isNotNull();
					assertThat(myKey.getType()).startsWith("ed");
					assertThat(myKey.getKeys()).isNotEmpty();
				})
				.verifyComplete();
	}

	@Test
	void createKeyShouldCreateEcdsaKey() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.ofKeyType("ecdsa-p521");

		this.reactiveTransitOperations.createKey("ecdsa-p521-key", request)
				.then(this.reactiveTransitOperations.getKey("ecdsa-p521-key"))
				.as(StepVerifier::create)
				.assertNext(myKey -> {
					assertThat(myKey.getType()).isEqualTo("ecdsa-p521");
					assertThat(myKey.getKeys()).isNotEmpty();
				})
				.verifyComplete();
	}

	@Test
	void createKeyShouldCreateRsa3072Key() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.ofKeyType("rsa-3072");

		this.reactiveTransitOperations.createKey("rsa-3072-key", request)
				.then(this.reactiveTransitOperations.getKey("rsa-3072-key"))
				.as(StepVerifier::create)
				.assertNext(myKey -> {
					assertThat(myKey.getType()).isEqualTo("rsa-3072");
					assertThat(myKey.getKeys()).isNotEmpty();
				})
				.verifyComplete();
	}

	@Test
	void createKeyShouldCreateAes256Gcm96Key() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.ofKeyType("aes256-gcm96");

		this.reactiveTransitOperations.createKey("aes256-gcm96-key", request)
				.then(this.reactiveTransitOperations.getKey("aes256-gcm96-key"))
				.as(StepVerifier::create)
				.assertNext(myKey -> {
					assertThat(myKey.getType()).isEqualTo("aes256-gcm96");
					assertThat(myKey.getKeys()).isNotEmpty();
				})
				.verifyComplete();
	}

	@Test
	void createKeyShouldCreateKeyWithOptions() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder()
				.convergentEncryption(true)
				.derived(true)
				.build();

		this.reactiveTransitOperations.createKey("myKey", request)
				.then(this.reactiveTransitOperations.getKey("myKey"))
				.as(StepVerifier::create)
				.assertNext(myKey -> {
					assertThat(myKey.getName()).isEqualTo("myKey");
					assertThat(myKey.isDeletionAllowed()).isFalse();
					assertThat(myKey.isDerived()).isTrue();
					assertThat(myKey.getMinDecryptionVersion()).isEqualTo(1);
					assertThat(myKey.getLatestVersion()).isEqualTo(1);
				})
				.verifyComplete();
	}

	@Test
	void shouldConfigureKey() {

		VaultTransitKeyConfiguration configuration = VaultTransitKeyConfiguration.builder()
				.deletionAllowed(true)
				.minDecryptionVersion(1)
				.minEncryptionVersion(2)
				.build();

		this.reactiveTransitOperations.createKey("myKey")
				.then(this.reactiveTransitOperations.rotate("myKey"))
				.then(this.reactiveTransitOperations.rotate("myKey"))
				.then(this.reactiveTransitOperations.configureKey("myKey", configuration))
				.then(this.reactiveTransitOperations.getKey("myKey"))
				.as(StepVerifier::create)
				.assertNext(myKey -> {
					assertThat(myKey.getMinDecryptionVersion()).isEqualTo(1);
					if (this.vaultVersion.isGreaterThanOrEqualTo(Version.parse("0.8.0"))) {
						assertThat(myKey.getMinEncryptionVersion()).isEqualTo(2);
					} else {
						assertThat(myKey.getMinEncryptionVersion()).isEqualTo(0);
					}
				})
				.verifyComplete();
	}

	@Test
	void shouldEnumerateKey() {

		this.reactiveTransitOperations.getKeys().as(StepVerifier::create).verifyComplete();
		this.reactiveTransitOperations.createKey("myKey")
				.thenMany(this.reactiveTransitOperations.getKeys())
				.as(StepVerifier::create)
				.assertNext(keys -> assertThat(keys).contains("myKey"))
				.verifyComplete();
	}

	@Test
	void getKeyShouldReturnEmptyIfKeyNotExists() {
		this.reactiveTransitOperations.getKey("myKey").as(StepVerifier::create).verifyComplete();
	}

	@Test
	void deleteKeyShouldFailIfKeyNotExists() {
		this.reactiveTransitOperations.deleteKey("myKey")
				.as(StepVerifier::create)
				.consumeErrorWith(e -> assertThat(e).hasMessageContaining("Status 400"))
				.verify();
	}

	@Test
	void deleteKeyShouldDeleteKey() {

		VaultTransitKeyConfiguration configuration = VaultTransitKeyConfiguration.builder()
				.deletionAllowed(true)
				.build();

		this.reactiveTransitOperations.createKey("myKey")
				.then(this.reactiveTransitOperations.configureKey("myKey", configuration))
				.then(this.reactiveTransitOperations.deleteKey("myKey"))
				.then(this.reactiveTransitOperations.getKey("myKey"))
				.as(StepVerifier::create)
				.verifyComplete();
	}

	@Test
	void encryptShouldCreateCiphertext() {

		this.reactiveTransitOperations.createKey("myKey")
				.then(this.reactiveTransitOperations.encrypt("myKey", "hello-world"))
				.as(StepVerifier::create)
				.assertNext(ciphertext -> assertThat(ciphertext).startsWith("vault:v"))
				.verifyComplete();
	}

	@Test
	void encryptShouldCreateCiphertextWithContext() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder()
				.convergentEncryption(true)
				.derived(true)
				.build();

		VaultTransitContext context = VaultTransitContext.builder().context("blubb".getBytes()).build();

		this.reactiveTransitOperations.createKey("mykey", request)
				.then(this.reactiveTransitOperations.encrypt("myKey", "hello-world".getBytes(), context))
				.as(StepVerifier::create)
				.assertNext(ciphertext -> assertThat(ciphertext).startsWith("vault:v1:"))
				.verifyComplete();
	}

	@Test
	void encryptShouldEncryptEmptyValues() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder()
				.convergentEncryption(true)
				.derived(true)
				.build();

		VaultTransitContext context = VaultTransitContext.builder().context("blubb".getBytes()).build();

		this.reactiveTransitOperations.createKey("myKey", request)
				.then(this.reactiveTransitOperations.encrypt("myKey", Plaintext.of("").with(context)))
				.as(StepVerifier::create)
				.assertNext(ciphertext -> {
					assertThat(ciphertext.getCiphertext()).startsWith("vault:v1:");
					assertThat(ciphertext.getContext()).isEqualTo(context);
				})
				.verifyComplete();
	}

	@Test
	void encryptShouldCreateWrappedCiphertextWithContext() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder()
				.convergentEncryption(true)
				.derived(true)
				.build();

		VaultTransitContext context = VaultTransitContext.builder().context("blubb".getBytes()).build();

		this.reactiveTransitOperations.createKey("myKey", request)
				.then(this.reactiveTransitOperations.encrypt("myKey", Plaintext.of("hello-world").with(context)))
				.as(StepVerifier::create)
				.assertNext(ciphertext -> {
					assertThat(ciphertext.getCiphertext()).startsWith("vault:v1:");
					assertThat(ciphertext.getContext()).isEqualTo(context);
				})
				.verifyComplete();
	}

	@Test
	void decryptShouldCreatePlaintext() {

		this.reactiveTransitOperations.createKey("myKey")
				.then(this.reactiveTransitOperations.encrypt("myKey", "hello-world"))
				.flatMap(ciphertext -> this.reactiveTransitOperations.decrypt("myKey", ciphertext))
				.as(StepVerifier::create)
				.assertNext(plaintext -> assertThat(plaintext).isEqualTo("hello-world"))
				.verifyComplete();
	}

	@Test
	void decryptShouldCreatePlaintextWithContext() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder()
				.convergentEncryption(true)
				.derived(true)
				.build();

		VaultTransitContext transitRequest = VaultTransitContext.builder().context("blubb".getBytes()).build();

		this.reactiveTransitOperations.createKey("myKey", request)
				.then(this.reactiveTransitOperations.encrypt("myKey", "hello-world".getBytes(), transitRequest))
				.flatMap(ciphertext -> this.reactiveTransitOperations.decrypt("myKey", ciphertext, transitRequest))
				.as(StepVerifier::create)
				.assertNext(plaintext -> assertThat(new String(plaintext)).isEqualTo("hello-world"))
				.verifyComplete();
	}

	@Test
	void decryptShouldCreateWrappedPlaintextWithContext() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder()
				.convergentEncryption(true)
				.derived(true)
				.build();

		VaultTransitContext context = VaultTransitContext.builder().context("blubb".getBytes()).build();

		this.reactiveTransitOperations.createKey("myKey", request)
				.then(this.reactiveTransitOperations.encrypt("myKey", Plaintext.of("hello-world").with(context)))
				.flatMap(ciphertext -> this.reactiveTransitOperations.decrypt("myKey", ciphertext))
				.as(StepVerifier::create)
				.assertNext(plaintext -> {
					assertThat(plaintext.asString()).isEqualTo("hello-world");
					assertThat(plaintext.getContext()).isEqualTo(context);
				})
				.verifyComplete();
	}

	@Test
	void encryptAndRewrapShouldCreateCiphertext() {

		String ciphertext = this.reactiveTransitOperations.createKey("myKey")
				.then(this.reactiveTransitOperations.encrypt("myKey", "hello-world"))
				.block();

		assertThat(ciphertext).isNotNull();

		this.reactiveTransitOperations.rotate("myKey")
				.then(this.reactiveTransitOperations.rewrap("myKey", ciphertext))
				.as(StepVerifier::create)
				.assertNext(rewrapped -> assertThat(rewrapped).startsWith("vault:v2:"))
				.verifyComplete();
	}

	@Test
	void encryptAndRewrapInBatchShouldCreateCiphertext() {

		this.reactiveTransitOperations
				.createKey("mykey",
						VaultTransitKeyCreationRequest.builder().convergentEncryption(true).derived(true).build())
				.as(StepVerifier::create)
				.verifyComplete();

		VaultTransitContext transitRequest = VaultTransitContext.builder() //
				.context("blubb".getBytes()) //
				.build();

		String ciphertext1 = this.reactiveTransitOperations.encrypt("mykey", "hello-world".getBytes(), transitRequest)
				.block();
		String ciphertext2 = this.reactiveTransitOperations.encrypt("mykey", "hello-vault".getBytes(), transitRequest)
				.block();
		this.reactiveTransitOperations.rotate("mykey").as(StepVerifier::create).verifyComplete();

		List<Ciphertext> batchRequest = Stream.of(ciphertext1, ciphertext2)
				.map(ct -> Ciphertext.of(ct).with(transitRequest))
				.toList();
		this.reactiveTransitOperations.rewrap("mykey", batchRequest)
				.as(StepVerifier::create)
				.assertNext(it -> assertThat(it.get().getCiphertext()).startsWith("vault:v2"))
				.expectNextCount(1)
				.verifyComplete();
	}

	@Test
	void shouldEncryptBinaryPlaintext() {

		this.reactiveTransitOperations.createKey("myKey").as(StepVerifier::create).verifyComplete();

		byte[] plaintext = new byte[] {1, 2, 3, 4, 5};

		this.reactiveTransitOperations.encrypt("myKey", plaintext, VaultTransitContext.empty())
				.flatMap(ciphertext -> this.reactiveTransitOperations.decrypt("myKey", ciphertext,
						VaultTransitContext.empty()))
				.as(StepVerifier::create)
				.assertNext(decrypted -> assertThat(decrypted).isEqualTo(plaintext))
				.verifyComplete();
	}

	@Test
	void encryptAndRewrapShouldCreateCiphertextWithContext() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder()
				.convergentEncryption(true)
				.derived(true)
				.build();

		VaultTransitContext transitRequest = VaultTransitContext.builder().context("blubb".getBytes()).build();

		String ciphertext = this.reactiveTransitOperations.createKey("myKey", request)
				.then(this.reactiveTransitOperations.encrypt("myKey", "hello-world".getBytes(), transitRequest))
				.block();

		assertThat(ciphertext).isNotNull();

		this.reactiveTransitOperations.rotate("myKey")
				.then(this.reactiveTransitOperations.rewrap("myKey", ciphertext, transitRequest))
				.as(StepVerifier::create)
				.assertNext(rewrapped -> assertThat(rewrapped).startsWith("vault:v2"))
				.verifyComplete();
	}

	@Test
	void shouldBatchEncrypt() {

		this.reactiveTransitOperations.createKey("myKey")
				.thenMany(this.reactiveTransitOperations.encrypt("myKey",
						Arrays.asList(Plaintext.of("one"), Plaintext.of("two"))))
				.as(StepVerifier::create)
				.assertNext(encrypted -> {
					assertThat(encrypted.get()).isNotNull();
					assertThat(Objects.requireNonNull(encrypted.get()).getCiphertext()).startsWith("vault:");
				})
				.assertNext(encrypted -> {
					assertThat(encrypted.get()).isNotNull();
					assertThat(Objects.requireNonNull(encrypted.get()).getCiphertext()).startsWith("vault:");
				})
				.verifyComplete();
	}

	@Test
	void shouldBatchDecrypt() {

		this.reactiveTransitOperations.createKey("myKey").block();
		Ciphertext one = this.reactiveTransitOperations.encrypt("myKey", Plaintext.of("one")).block();
		Ciphertext two = this.reactiveTransitOperations.encrypt("myKey", Plaintext.of("two")).block();

		assertThat(one).isNotNull();
		assertThat(two).isNotNull();

		this.reactiveTransitOperations.decrypt("myKey", Arrays.asList(one, two))
				.zipWith(Flux.concat(this.reactiveTransitOperations.decrypt("myKey", one),
						this.reactiveTransitOperations.decrypt("myKey", two)))
				.as(StepVerifier::create)
				.assertNext(it -> {
					assertThat(it.getT1().getAsString()).isEqualTo(it.getT2().asString());
					assertThat(it.getT1().getAsString()).isEqualTo("one");
				})
				.assertNext(it -> {
					assertThat(it.getT1().getAsString()).isEqualTo(it.getT2().asString());
					assertThat(it.getT1().getAsString()).isEqualTo("two");
				})
				.verifyComplete();
	}

	@Test
	void shouldBatchEncryptWithContext() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder().derived(true).build();

		VaultTransitContext context1 = VaultTransitContext.builder().context("oneContext".getBytes()).build();

		VaultTransitContext context2 = VaultTransitContext.builder().context("twoContext".getBytes()).build();

		this.reactiveTransitOperations.createKey("myKey", request)
				.thenMany(this.reactiveTransitOperations.encrypt("myKey",
						Arrays.asList(Plaintext.of("one").with(context1), Plaintext.of("two").with(context2))))
				.as(StepVerifier::create)
				.assertNext(it -> assertThat(Objects.requireNonNull(it.get()).getContext()).isEqualTo(context1))
				.assertNext(it -> assertThat(Objects.requireNonNull(it.get()).getContext()).isEqualTo(context2))
				.verifyComplete();
	}

	@Test
	void shouldBatchDecryptWithContext() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder().derived(true).build();

		Plaintext one = Plaintext.of("one")
				.with(VaultTransitContext.builder().context("oneContext".getBytes()).build());

		Plaintext two = Plaintext.of("two")
				.with(VaultTransitContext.builder().context("twoContext".getBytes()).build());

		this.reactiveTransitOperations.createKey("myKey", request)
				.thenMany(this.reactiveTransitOperations.encrypt("myKey", Arrays.asList(one, two)))
				.flatMap(it -> Mono.justOrEmpty(it.get()))
				.collectList()
				.flatMapMany(it -> this.reactiveTransitOperations.decrypt("myKey", it))
				.as(StepVerifier::create)
				.assertNext(it -> assertThat(it.get()).isEqualTo(one))
				.assertNext(it -> assertThat(it.get()).isEqualTo(two))
				.verifyComplete();
	}

	@Test
	void shouldBatchDecryptWithWrongContext() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder().derived(true).build();

		Plaintext one = Plaintext.of("one")
				.with(VaultTransitContext.builder().context("oneContext".getBytes()).build());

		Plaintext two = Plaintext.of("two")
				.with(VaultTransitContext.builder().context("twoContext".getBytes()).build());

		List<Ciphertext> encrypted = this.reactiveTransitOperations.createKey("myKey", request)
				.thenMany(this.reactiveTransitOperations.encrypt("myKey", Arrays.asList(one, two)))
				.flatMap(it -> Mono.justOrEmpty(it.get()))
				.collectList()
				.block();

		assertThat(encrypted).isNotNull();

		Ciphertext encryptedOne = encrypted.get(0);
		Ciphertext decryptedTwo = encrypted.get(1);

		Ciphertext tampered = decryptedTwo.with(encryptedOne.getContext());

		StepVerifier.FirstStep<VaultDecryptionResult> stepVerifier = this.reactiveTransitOperations
				.decrypt("myKey", Arrays.asList(encryptedOne, tampered))
				.as(StepVerifier::create);

		if (this.vaultVersion.isGreaterThanOrEqualTo(Version.parse("1.6.0"))) {
			stepVerifier.consumeErrorWith(e -> assertThat(e).hasMessageContaining("error")).verify();
		} else {
			stepVerifier.assertNext(it -> assertThat(it.get()).isEqualTo(one)).assertNext(it -> {
				assertThat(it.isSuccessful()).isEqualTo(false);
				assertThat(it.getCause()).isInstanceOf(VaultException.class);
			}).verifyComplete();
		}
	}

	@Test
	void shouldBatchDecryptEmptyPlaintext() {

		this.reactiveTransitOperations.createKey("myKey")
				.then(this.reactiveTransitOperations.encrypt("myKey", Plaintext.empty()))
				.flatMapMany(empty -> this.reactiveTransitOperations.decrypt("myKey", Collections.singletonList(empty)))
				.as(StepVerifier::create)
				.assertNext(it -> assertThat(it.getAsString()).isEmpty())
				.verifyComplete();
	}

	@Test
	void shouldBatchDecryptEmptyPlaintextWithContext() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder().derived(true).build();

		Plaintext empty = Plaintext.empty()
				.with(VaultTransitContext.builder().context("oneContext".getBytes()).build());

		this.reactiveTransitOperations.createKey("myKey", request)
				.thenMany(this.reactiveTransitOperations.encrypt("myKey", Collections.singletonList(empty)))
				.flatMap(it -> Mono.justOrEmpty(it.get()))
				.collectList()
				.flatMapMany(it -> this.reactiveTransitOperations.decrypt("myKey", it))
				.as(StepVerifier::create)
				.assertNext(it -> assertThat(it.get()).isEqualTo(empty))
				.verifyComplete();
	}

	@Test
	void generateHmacShouldCreateHmac() {

		createEcdsaP256Key()
				.flatMap(keyName -> this.reactiveTransitOperations.getHmac(keyName, Plaintext.of("hello-world")))
				.as(StepVerifier::create)
				.assertNext(hmac -> assertThat(hmac.getHmac()).isNotEmpty())
				.verifyComplete();
	}

	@Test
	void generateHmacShouldCreateHmacForRotatedKey() {

		VaultHmacRequest request = VaultHmacRequest.builder()
				.plaintext(Plaintext.of("hello-world"))
				.keyVersion(2)
				.build();

		createEcdsaP256Key()
				.flatMap(keyName -> this.reactiveTransitOperations.rotate(keyName)
						.then(this.reactiveTransitOperations.getHmac(keyName, request)))
				.as(StepVerifier::create)
				.assertNext(hmac -> assertThat(hmac.getHmac()).isNotEmpty())
				.verifyComplete();
	}

	@Test
	void generateHmacWithCustomAlgorithmShouldCreateHmac() {

		VaultHmacRequest request = VaultHmacRequest.builder()
				.plaintext(Plaintext.of("hello-world"))
				.algorithm("sha2-512")
				.build();

		createEcdsaP256Key().flatMap(keyName -> this.reactiveTransitOperations.getHmac(keyName, request))
				.as(StepVerifier::create)
				.assertNext(hmac -> assertThat(hmac.getHmac()).isNotEmpty())
				.verifyComplete();
	}

	@Test
	void generateHmacWithInvalidAlgorithmShouldFail() {

		VaultHmacRequest request = VaultHmacRequest.builder()
				.plaintext(Plaintext.of("hello-world"))
				.algorithm("blah-512")
				.build();

		createEcdsaP256Key().flatMap(keyName -> this.reactiveTransitOperations.getHmac("myKey", request))
				.as(StepVerifier::create)
				.consumeErrorWith(e -> assertThat(e).isInstanceOf(VaultException.class))
				.verify();
	}

	@Test
	void signShouldCreateSignature() {

		createEcdsaP256Key()
				.flatMap(keyName -> this.reactiveTransitOperations.sign(keyName, Plaintext.of("hello-world")))
				.as(StepVerifier::create)
				.assertNext(signature -> assertThat(signature.getSignature()).isNotEmpty())
				.verifyComplete();
	}

	@Test
	void signShouldCreateSignatureUsingEd25519() {

		VaultTransitKeyCreationRequest keyCreationRequest = VaultTransitKeyCreationRequest.ofKeyType("ed25519");

		this.reactiveTransitOperations.createKey("ed-key", keyCreationRequest)
				.then(this.reactiveTransitOperations.sign("ed-key", Plaintext.of("hello-world")))
				.as(StepVerifier::create)
				.assertNext(signature -> assertThat(signature.getSignature()).isNotEmpty())
				.verifyComplete();
	}

	@Test
	void signWithInvalidKeyFormatShouldFail() {

		this.reactiveTransitOperations.createKey("myKey")
				.then(this.reactiveTransitOperations.sign("myKey", Plaintext.of("hello-world")))
				.as(StepVerifier::create)
				.consumeErrorWith(e -> assertThat(e).isInstanceOf(VaultException.class))
				.verify();
	}

	@Test
	void signWithCustomAlgorithmShouldCreateSignature() {

		VaultSignRequest request = VaultSignRequest.builder()
				.plaintext(Plaintext.of("hello-world"))
				.signatureAlgorithm("sha2-512")
				.build();

		createEcdsaP256Key().flatMap(keyName -> this.reactiveTransitOperations.sign(keyName, request))
				.as(StepVerifier::create)
				.assertNext(signature -> assertThat(signature.getSignature()).isNotEmpty())
				.verifyComplete();
	}

	@Test
	void shouldVerifyValidSignature() {

		Plaintext plaintext = Plaintext.of("hello-world");

		createEcdsaP256Key()
				.flatMap(keyName -> this.reactiveTransitOperations.sign(keyName, plaintext)
						.flatMap(signature -> this.reactiveTransitOperations.verify(keyName, plaintext, signature)))
				.as(StepVerifier::create)
				.assertNext(valid -> assertThat(valid).isTrue())
				.verifyComplete();
	}

	@Test
	void shouldVerifyValidHmac() {

		Plaintext plaintext = Plaintext.of("hello-world");

		createEcdsaP256Key()
				.flatMap(keyName -> this.reactiveTransitOperations.getHmac(keyName, plaintext)
						.flatMap(hmac -> this.reactiveTransitOperations.verify(keyName,
								VaultSignatureVerificationRequest.create(plaintext, hmac))))
				.as(StepVerifier::create)
				.assertNext(valid -> assertThat(valid).isEqualTo(SignatureValidation.valid()))
				.verifyComplete();
	}

	@Test
	void shouldVerifyValidSignatureWithCustomAlgorithm() {

		Plaintext plaintext = Plaintext.of("hello-world");

		VaultSignRequest request = VaultSignRequest.builder()
				.plaintext(plaintext)
				.signatureAlgorithm("sha2-512")
				.build();

		createEcdsaP256Key()
				.flatMap((keyName) -> this.reactiveTransitOperations.sign(keyName, request)
						.map(signature -> VaultSignatureVerificationRequest.builder()
								.signatureAlgorithm("sha2-512")
								.plaintext(plaintext)
								.signature(signature)
								.build())
						.flatMap(verificationRequest -> this.reactiveTransitOperations.verify(keyName,
								verificationRequest)))
				.as(StepVerifier::create)
				.assertNext(valid -> assertThat(valid).isEqualTo(SignatureValidation.valid()))
				.verifyComplete();
	}

	@Test
	void shouldCreateNewExportableKey() {

		VaultTransitKeyCreationRequest vaultTransitKeyCreationRequest = VaultTransitKeyCreationRequest.builder()
				.exportable(true)
				.derived(true)
				.build();

		reactiveTransitOperations.createKey("myKey", vaultTransitKeyCreationRequest)
				.then(reactiveTransitOperations.getKey("myKey"))
				.as(StepVerifier::create)
				.assertNext(vaultTransitKey -> {
					assertThat(vaultTransitKey.getName()).isEqualTo("myKey");
					assertThat(vaultTransitKey.isExportable()).isTrue();
				})
				.verifyComplete();
	}

	@Test
	void shouldCreateNotExportableKeyByDefault() {

		reactiveTransitOperations.createKey("myKey")
				.then(reactiveTransitOperations.getKey("myKey"))
				.as(StepVerifier::create)
				.assertNext(vaultTransitKey -> {
					assertThat(vaultTransitKey.getName()).isEqualTo("myKey");
					assertThat(vaultTransitKey.isExportable()).isFalse();
				})
				.verifyComplete();
	}

	@Test
	void shouldExportEncryptionKey() {

		VaultTransitKeyCreationRequest vaultTransitKeyCreationRequest = VaultTransitKeyCreationRequest.builder()
				.exportable(true)
				.build();

		reactiveTransitOperations.createKey("myKey", vaultTransitKeyCreationRequest)
				.then(reactiveTransitOperations.exportKey("myKey", TransitKeyType.ENCRYPTION_KEY))
				.as(StepVerifier::create)
				.assertNext(rawTransitKey -> {
					assertThat(rawTransitKey.getName()).isEqualTo("myKey");
					assertThat(rawTransitKey.getKeys()).isNotEmpty();
					assertThat(rawTransitKey.getKeys().get("1")).isNotBlank();
				})
				.verifyComplete();
	}

	@Test
	void shouldNotAllowExportSigningKey() {

		VaultTransitKeyCreationRequest vaultTransitKeyCreationRequest = VaultTransitKeyCreationRequest.builder()
				.exportable(true)
				.build();

		reactiveTransitOperations.createKey("myKey", vaultTransitKeyCreationRequest)
				.then(reactiveTransitOperations.exportKey("myKey", TransitKeyType.SIGNING_KEY))
				.as(StepVerifier::create)
				.consumeErrorWith(e -> assertThat(e).isInstanceOf(VaultException.class))
				.verify();
	}

	@Test
	void shouldExportEcDsaKey() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder()
				.type("ecdsa-p256")
				.exportable(true)
				.build();

		this.reactiveTransitOperations.createKey("myKey", request)
				.thenMany(Flux.concat(this.reactiveTransitOperations.exportKey("myKey", TransitKeyType.HMAC_KEY),
						this.reactiveTransitOperations.exportKey("myKey", TransitKeyType.SIGNING_KEY)))
				.as(StepVerifier::create)
				.assertNext(hmacKey -> assertThat(hmacKey.getKeys()).isNotEmpty())
				.assertNext(signingKey -> assertThat(signingKey.getKeys()).isNotEmpty())
				.verifyComplete();
	}

	@Test
	void shouldExportEdKey() {

		VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder()
				.type("ed25519")
				.exportable(true)
				.build();

		this.reactiveTransitOperations.createKey("myKey", request)
				.thenMany(Flux.concat(this.reactiveTransitOperations.exportKey("myKey", TransitKeyType.HMAC_KEY),
						this.reactiveTransitOperations.exportKey("myKey", TransitKeyType.SIGNING_KEY)))
				.as(StepVerifier::create)
				.assertNext(hmacKey -> assertThat(hmacKey.getKeys()).isNotEmpty())
				.assertNext(signingKey -> assertThat(signingKey.getKeys()).isNotEmpty())
				.verifyComplete();
	}

	private Mono<String> createEcdsaP256Key() {

		String keyName = "ecdsa-key";
		VaultTransitKeyCreationRequest keyCreationRequest = VaultTransitKeyCreationRequest.ofKeyType("ecdsa-p256");
		return this.reactiveTransitOperations.createKey(keyName, keyCreationRequest).thenReturn(keyName);
	}

}
