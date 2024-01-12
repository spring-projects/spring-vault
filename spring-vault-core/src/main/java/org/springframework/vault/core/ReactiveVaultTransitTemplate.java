/*
 * Copyright 2023-2024 the original author or authors.
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

import org.springframework.util.Assert;
import org.springframework.vault.core.VaultTransitTemplate.VaultTransitKeyImpl;
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
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultSignRequest;
import org.springframework.vault.support.VaultSignatureVerificationRequest;
import org.springframework.vault.support.VaultTransitContext;
import org.springframework.vault.support.VaultTransitKey;
import org.springframework.vault.support.VaultTransitKeyConfiguration;
import org.springframework.vault.support.VaultTransitKeyCreationRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.vault.core.VaultTransitTemplate.*;

/**
 * Default implementation of {@link ReactiveVaultTransitOperations}.
 *
 * @author James Luke
 * @since 3.1
 */
public class ReactiveVaultTransitTemplate implements ReactiveVaultTransitOperations {

	private final ReactiveVaultOperations reactiveVaultOperations;

	private final String path;

	public ReactiveVaultTransitTemplate(ReactiveVaultOperations reactiveVaultOperations, String path) {

		Assert.notNull(reactiveVaultOperations, "ReactiveVaultOperations must not be null");
		Assert.hasText(path, "Path must not be empty");

		this.reactiveVaultOperations = reactiveVaultOperations;
		this.path = path;
	}

	@Override
	public Mono<Void> createKey(String keyName) {

		Assert.hasText(keyName, "Key name must not be empty");

		return this.reactiveVaultOperations.write(String.format("%s/keys/%s", this.path, keyName), null).then();
	}

	@Override
	public Mono<Void> createKey(String keyName, VaultTransitKeyCreationRequest createKeyRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(createKeyRequest, "VaultTransitKeyCreationRequest must not be empty");

		return this.reactiveVaultOperations.write(String.format("%s/keys/%s", this.path, keyName), createKeyRequest)
			.then();
	}

	@Override
	public Mono<Void> rotate(String keyName) {

		Assert.hasText(keyName, "Key name must not be empty");

		return this.reactiveVaultOperations.write(String.format("%s/keys/%s/rotate", this.path, keyName), null).then();
	}

	@Override
	public Mono<String> encrypt(String keyName, String plaintext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");

		Map<String, String> request = new LinkedHashMap<>();

		request.put("plaintext", Base64.getEncoder().encodeToString(plaintext.getBytes()));

		return this.reactiveVaultOperations.write(String.format("%s/encrypt/%s", this.path, keyName), request)
			.map(it -> (String) it.getRequiredData().get("ciphertext"));
	}

	@Override
	public Mono<Void> configureKey(String keyName, VaultTransitKeyConfiguration keyConfiguration) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(keyConfiguration, "VaultKeyConfiguration must not be empty");

		return this.reactiveVaultOperations
			.write(String.format("%s/keys/%s/config", this.path, keyName), keyConfiguration)
			.then();
	}

	@Override
	public Mono<Void> deleteKey(String keyName) {

		Assert.hasText(keyName, "Key name must not be empty");

		return this.reactiveVaultOperations.delete(String.format("%s/keys/%s", this.path, keyName));
	}

	@Override
	@SuppressWarnings("unchecked")
	public Flux<String> getKeys() {
		return this.reactiveVaultOperations.read(String.format("%s/keys?list=true", this.path))
			.flatMapIterable(it -> (List<String>) it.getRequiredData().get("keys"));
	}

	@Override
	public Mono<String> encrypt(String keyName, byte[] plaintext, VaultTransitContext transitContext) {

		Assert.notNull(plaintext, "Plaintext must not be null");
		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(transitContext, "VaultTransitContext must not be null");

		Map<String, String> request = new LinkedHashMap<>();

		request.put("plaintext", Base64.getEncoder().encodeToString(plaintext));

		applyTransitOptions(transitContext, request);

		return this.reactiveVaultOperations.write(String.format("%s/encrypt/%s", this.path, keyName), request)
			.map(it -> (String) it.getRequiredData().get("ciphertext"));
	}

	@Override
	public Mono<Ciphertext> encrypt(String keyName, Plaintext plaintext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");

		return encrypt(keyName, plaintext.getPlaintext(), plaintext.getContext())
			.map(ciphertext -> toCiphertext(ciphertext, plaintext.getContext()));
	}

	@Override
	public Mono<String> decrypt(String keyName, String ciphertext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.hasText(ciphertext, "Ciphertext must not be empty");

		Map<String, String> request = new LinkedHashMap<>();

		request.put("ciphertext", ciphertext);

		return this.reactiveVaultOperations.write(String.format("%s/decrypt/%s", this.path, keyName), request)
			.map(it -> (String) it.getRequiredData().get("plaintext"))
			.map(plaintext -> new String(Base64.getDecoder().decode(plaintext)));
	}

	@Override
	public Mono<Plaintext> decrypt(String keyName, Ciphertext ciphertext) {

		Assert.hasText(keyName, "Key name must not be null");
		Assert.notNull(ciphertext, "Ciphertext must not be null");

		return decrypt(keyName, ciphertext.getCiphertext(), ciphertext.getContext())
			.map(plaintext -> Plaintext.of(plaintext).with(ciphertext.getContext()));
	}

	@Override
	public Mono<byte[]> decrypt(String keyName, String ciphertext, VaultTransitContext transitContext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.hasText(ciphertext, "Ciphertext must not be empty");
		Assert.notNull(transitContext, "VaultTransitContext must not be null");

		Map<String, String> request = new LinkedHashMap<>();

		request.put("ciphertext", ciphertext);

		applyTransitOptions(transitContext, request);

		return this.reactiveVaultOperations.write(String.format("%s/decrypt/%s", this.path, keyName), request)
			.map(it -> (String) it.getRequiredData().get("plaintext"))
			.map(Base64.getDecoder()::decode);
	}

	@Override
	public Mono<String> rewrap(String keyName, String ciphertext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.hasText(ciphertext, "Ciphertext must not be empty");

		Map<String, String> request = new LinkedHashMap<>();
		request.put("ciphertext", ciphertext);

		return this.reactiveVaultOperations.write(String.format("%s/rewrap/%s", this.path, keyName), request)
			.map(response -> (String) response.getRequiredData().get("ciphertext"));
	}

	@Override
	public Mono<String> rewrap(String keyName, String ciphertext, VaultTransitContext transitContext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.hasText(ciphertext, "Ciphertext must not be empty");
		Assert.notNull(transitContext, "VaultTransitContext must not be null");

		Map<String, String> request = createRewrapRequest(toCiphertext(ciphertext, transitContext));

		applyTransitOptions(transitContext, request);

		return this.reactiveVaultOperations.write(String.format("%s/rewrap/%s", this.path, keyName), request)
			.map(response -> (String) response.getRequiredData().get("ciphertext"));
	}

	@Override
	public Flux<VaultEncryptionResult> rewrap(String keyName, List<Ciphertext> batchRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notEmpty(batchRequest, "BatchRequest must not be null and must have at least one entry");

		return Flux.fromIterable(batchRequest)
			.map(VaultTransitTemplate::createRewrapRequest)
			.collectList()
			.flatMap(batch -> this.reactiveVaultOperations.write(String.format("%s/rewrap/%s", this.path, keyName),
					Collections.singletonMap("batch_input", batch)))
			.flatMapIterable(vaultResponse -> toBatchResults(vaultResponse, batchRequest, Ciphertext::getContext));
	}

	@Override
	public Flux<VaultEncryptionResult> encrypt(String keyName, List<Plaintext> batchRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notEmpty(batchRequest, "BatchRequest must not be null and must have at least one entry");

		return Flux.fromIterable(batchRequest).map(request -> {
			Map<String, String> vaultRequest = new LinkedHashMap<>(2);
			vaultRequest.put("plaintext", Base64.getEncoder().encodeToString(request.getPlaintext()));
			applyTransitOptions(request.getContext(), vaultRequest);
			return vaultRequest;
		})
			.collectList()
			.flatMap(batch -> this.reactiveVaultOperations.write(String.format("%s/encrypt/%s", this.path, keyName),
					Collections.singletonMap("batch_input", batch)))
			.flatMapIterable(vaultResponse -> toBatchResults(vaultResponse, batchRequest, Plaintext::getContext));
	}

	@Override
	public Flux<VaultDecryptionResult> decrypt(String keyName, List<Ciphertext> batchRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notEmpty(batchRequest, "BatchRequest must not be null and must have at least one entry");

		return Flux.fromIterable(batchRequest).map(request -> {
			Map<String, String> vaultRequest = new LinkedHashMap<>(2);
			vaultRequest.put("ciphertext", request.getCiphertext());
			applyTransitOptions(request.getContext(), vaultRequest);
			return vaultRequest;
		})
			.collectList()
			.flatMap(batch -> this.reactiveVaultOperations.write(String.format("%s/decrypt/%s", this.path, keyName),
					Collections.singletonMap("batch_input", batch)))
			.flatMapIterable(vaultResponse -> toDecryptionResults(vaultResponse, batchRequest));
	}

	@Override
	public Mono<Hmac> getHmac(String keyName, Plaintext plaintext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");

		VaultHmacRequest request = VaultHmacRequest.create(plaintext);

		return getHmac(keyName, request);
	}

	@Override
	public Mono<Hmac> getHmac(String keyName, VaultHmacRequest hmacRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(hmacRequest, "HMAC request must not be null");

		return this.reactiveVaultOperations
			.write(String.format("%s/hmac/%s", this.path, keyName), toRequestBody(hmacRequest))
			.map(vaultResponse -> (String) vaultResponse.getRequiredData().get("hmac"))
			.map(Hmac::of);
	}

	@Override
	public Mono<Signature> sign(String keyName, Plaintext plaintext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");

		VaultSignRequest request = VaultSignRequest.create(plaintext);

		return sign(keyName, request);
	}

	@Override
	public Mono<Signature> sign(String keyName, VaultSignRequest signRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(signRequest, "Sign request must not be null");

		return this.reactiveVaultOperations
			.write(String.format("%s/sign/%s", this.path, keyName), toRequestBody(signRequest))
			.map(vaultResponse -> (String) vaultResponse.getRequiredData().get("signature"))
			.map(Signature::of);
	}

	@Override
	public Mono<Boolean> verify(String keyName, Plaintext plaintext, Signature signature) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");
		Assert.notNull(signature, "Signature must not be null");

		VaultSignatureVerificationRequest request = VaultSignatureVerificationRequest.create(plaintext, signature);

		return verify(keyName, request).map(SignatureValidation::isValid);
	}

	@Override
	public Mono<SignatureValidation> verify(String keyName, VaultSignatureVerificationRequest verificationRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(verificationRequest, "Signature verification request must not be null");

		return this.reactiveVaultOperations
			.write(String.format("%s/verify/%s", this.path, keyName), toRequestBody(verificationRequest))
			.map(VaultResponse::getRequiredData)
			.map(vaultResponse -> {
				if (vaultResponse.containsKey("valid") && (Boolean) vaultResponse.get("valid")) {
					return SignatureValidation.valid();
				}
				return SignatureValidation.invalid();
			});
	}

	@Override
	public Mono<RawTransitKey> exportKey(String keyName, TransitKeyType type) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(type, "Key type must not be null");

		return this.reactiveVaultOperations
			.read(String.format("%s/export/%s/%s", this.path, type.getValue(), keyName),
					VaultTransitTemplate.RawTransitKeyImpl.class)
			.flatMap(vaultResponse -> Mono.justOrEmpty(vaultResponse.getRequiredData()));
	}

	@Override
	public Mono<VaultTransitKey> getKey(String keyName) {

		Assert.hasText(keyName, "Key name must not be empty");

		return this.reactiveVaultOperations
			.read(String.format("%s/keys/%s", this.path, keyName), VaultTransitKeyImpl.class)
			.map(VaultResponseSupport::getRequiredData);
	}

}
