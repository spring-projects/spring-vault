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

import java.util.List;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.vault.support.*;

/**
 * Interface that specifies operations using the {@code transit} secrets engine.
 *
 * @author James Luke
 * @since 3.1
 * @see <a href=
 * "https://www.vaultproject.io/docs/secrets/transit/index.html">Transit Secret
 * Engine</a>
 */
public interface ReactiveVaultTransitOperations {

	/**
	 * Create a new named encryption key given a {@code name}.
	 * @param keyName must not be empty or {@literal null}.
	 */
	Mono<Void> createKey(String keyName);

	/**
	 * Create a new named encryption key given a {@code name} and
	 * {@link VaultTransitKeyCreationRequest}. The key options set here cannot be
	 * changed after key creation.
	 * @param keyName must not be empty or {@literal null}.
	 * @param createKeyRequest must not be {@literal null}.
	 */
	Mono<Void> createKey(String keyName, VaultTransitKeyCreationRequest createKeyRequest);

	/**
	 * Get a {@link Flux} of transit key names.
	 * @return {@link Flux} of transit key names.
	 */
	Flux<String> getKeys();

	/**
	 * Create a new named encryption key given a {@code name}.
	 * @param keyName must not be empty or {@literal null}.
	 * @param keyConfiguration must not be {@literal null}.
	 */
	Mono<Void> configureKey(String keyName, VaultTransitKeyConfiguration keyConfiguration);

	/**
	 * Return the value of the named encryption key. Depending on the type of key,
	 * different information may be returned. The key must be exportable to support
	 * this operation.
	 * @param keyName must not be empty or {@literal null}.
	 * @param type must not be {@literal null}.
	 * @return the {@link RawTransitKey}. Empty if key does not exist
	 */
	Mono<RawTransitKey> exportKey(String keyName, TransitKeyType type);

	/**
	 * Return information about a named encryption key.
	 * @param keyName must not be empty or {@literal null}.
	 * @return the {@link VaultTransitKey}. Empty if key does not exist.
	 */
	Mono<VaultTransitKey> getKey(String keyName);

	/**
	 * Deletes a named encryption key. It will no longer be possible to decrypt any
	 * data encrypted with the named key.
	 * @param keyName must not be empty or {@literal null}.
	 */
	Mono<Void> deleteKey(String keyName);

	/**
	 * Rotates the version of the named key. After rotation, new plain text requests
	 * will be encrypted with the new version of the key. To upgrade ciphertext to
	 * be encrypted with the latest version of the key, use
	 * {@link #rewrap(String, String)}.
	 * @param keyName must not be empty or {@literal null}.
	 * @see #rewrap(String, String)
	 */
	Mono<Void> rotate(String keyName);

	/**
	 * Encrypts the provided plain text using the named key. The given
	 * {@code plaintext} is encoded into bytes using the
	 * {@link java.nio.charset.Charset#defaultCharset() default charset}. Use
	 * {@link #encrypt(String, org.springframework.vault.support.Plaintext)} to
	 * construct a {@link org.springframework.vault.support.Plaintext#of(byte[])
	 * Plaintext} object from bytes to avoid {@link java.nio.charset.Charset}
	 * mismatches.
	 * @param keyName must not be empty or {@literal null}.
	 * @param plaintext must not be empty or {@literal null}.
	 * @return cipher text.
	 */
	Mono<String> encrypt(String keyName, String plaintext);

	/**
	 * Encrypts the provided {@code plaintext} using the named key.
	 * @param keyName must not be empty or {@literal null}.
	 * @param plaintext must not be {@literal null}.
	 * @return cipher text.
	 */
	Mono<Ciphertext> encrypt(String keyName, Plaintext plaintext);

	/**
	 * Encrypts the provided {@code plaintext} using the named key.
	 * @param keyName must not be empty or {@literal null}.
	 * @param plaintext must not be empty or {@literal null}.
	 * @param transitRequest must not be {@literal null}. Use
	 * {@link VaultTransitContext#empty()} if no request options provided.
	 * @return cipher text.
	 */
	Mono<String> encrypt(String keyName, byte[] plaintext, VaultTransitContext transitRequest);

	/**
	 * Encrypts the provided batch of {@code plaintext} using the named key and
	 * context. The encryption is done using transit engine's batch operation.
	 * @param keyName must not be empty or {@literal null}.
	 * @param batchRequest a list of {@link Plaintext} which includes plain text and
	 * an optional context.
	 * @return the encrypted result in the order of {@code batchRequest} plaintexts.
	 */
	Flux<VaultEncryptionResult> encrypt(String keyName, List<Plaintext> batchRequest);

	/**
	 * Decrypts the provided plain text using the named key. The decoded
	 * {@code plaintext} is decoded into {@link String} the
	 * {@link java.nio.charset.Charset#defaultCharset() default charset}. Use
	 * {@link #decrypt(String, org.springframework.vault.support.Ciphertext)} to
	 * obtain a {@link org.springframework.vault.support.Ciphertext} object that
	 * allows to control the {@link java.nio.charset.Charset} for later consumption.
	 * @param keyName must not be empty or {@literal null}.
	 * @param ciphertext must not be empty or {@literal null}.
	 * @return plain text.
	 */
	Mono<String> decrypt(String keyName, String ciphertext);

	/**
	 * Decrypts the provided cipher text using the named key.
	 * @param keyName must not be empty or {@literal null}.
	 * @param ciphertext must not be {@literal null}.
	 * @return plain text.
	 */
	Mono<Plaintext> decrypt(String keyName, Ciphertext ciphertext);

	/**
	 * Decrypts the provided {@code ciphertext} using the named key.
	 * @param keyName must not be empty or {@literal null}.
	 * @param ciphertext must not be empty or {@literal null}.
	 * @param transitContext must not be {@literal null}. Use
	 * {@link VaultTransitContext#empty()} if no request options provided.
	 * @return cipher text.
	 * @return plain text.
	 */
	Mono<byte[]> decrypt(String keyName, String ciphertext, VaultTransitContext transitContext);

	/**
	 * Decrypts the provided batch of cipher text using the named key and context.
	 * The* decryption is done using transit engine's batch operation.
	 * @param keyName must not be empty or {@literal null}.
	 * @param batchRequest a list of {@link Ciphertext} which includes plain text
	 * and an optional context.
	 * @return the decrypted result in the order of {@code batchRequest}
	 * ciphertexts.
	 */
	Flux<VaultDecryptionResult> decrypt(String keyName, List<Ciphertext> batchRequest);

	/**
	 * Rewrap the provided cipher text using the latest version of the named key.
	 * Because this never returns plain text, it is possible to delegate this
	 * functionality to untrusted users or scripts.
	 * @param keyName must not be empty or {@literal null}.
	 * @param ciphertext must not be empty or {@literal null}.
	 * @return cipher text.
	 * @see #rotate(String)
	 */
	Mono<String> rewrap(String keyName, String ciphertext);

	/**
	 * Rewrap the provided cipher text using the latest version of the named key.
	 * Because this never returns plain text, it is possible to delegate this
	 * functionality to untrusted users or scripts.
	 * @param keyName must not be empty or {@literal null}.
	 * @param ciphertext must not be empty or {@literal null}.
	 * @param transitContext must not be {@literal null}. Use
	 * {@link VaultTransitContext#empty()} if no request options provided.
	 * @return cipher text.
	 * @see #rotate(String)
	 */
	Mono<String> rewrap(String keyName, String ciphertext, VaultTransitContext transitContext);

	/**
	 * Rewrap the provided batch of cipher text using the latest version of the
	 * named key.
	 * @param batchRequest a list of {@link Ciphertext} which includes cipher text
	 * and a context
	 * @return the rewrapped result in the order of {@code batchRequest}
	 * ciphertexts.
	 * @see #rewrap(String, String)
	 */
	Flux<VaultEncryptionResult> rewrap(String keyName, List<Ciphertext> batchRequest);

	/**
	 * Create a HMAC using {@code keyName} of given {@link Plaintext} using the
	 * default hash algorithm. The key can be of any type supported by transit; the
	 * raw key will be marshaled into bytes to be used for the HMAC function. If the
	 * key is of a type that supports rotation, the latest (current) version will be
	 * used.
	 * @param keyName must not be empty or {@literal null}.
	 * @param plaintext must not be {@literal null}.
	 * @return the digest of given data the default hash algorithm and the named
	 * key.
	 */
	Mono<Hmac> getHmac(String keyName, Plaintext plaintext);

	/**
	 * Create a HMAC using {@code keyName} of given {@link VaultHmacRequest} using
	 * the default hash algorithm. The key can be of any type supported by transit;
	 * the raw key will be marshaled into bytes to be used for the HMAC function. If
	 * the key is of a type that supports rotation, configured
	 * {@link VaultHmacRequest#getKeyVersion()} will be used.
	 * @param keyName must not be empty or {@literal null}.
	 * @param request the {@link VaultHmacRequest}, must not be {@literal null}.
	 * @return the digest of given data the default hash algorithm and the named
	 * key.
	 */
	Mono<Hmac> getHmac(String keyName, VaultHmacRequest request);

	/**
	 * Create a cryptographic signature using {@code keyName} of the given
	 * {@link Plaintext} and the default hash algorithm. The key must be of a type
	 * that supports signing.
	 * @param keyName must not be empty or {@literal null}.
	 * @param plaintext must not be empty or {@literal null}.
	 * @return Signature for {@link Plaintext}.
	 */
	Mono<Signature> sign(String keyName, Plaintext plaintext);

	/**
	 * Create a cryptographic signature using {@code keyName} of the given
	 * {@link VaultSignRequest} and the specified hash algorithm. The key must be of
	 * a type that supports signing.
	 * @param keyName must not be empty or {@literal null}.
	 * @param request {@link VaultSignRequest} must not be empty or {@literal null}.
	 * @return Signature for {@link VaultSignRequest}.
	 */
	Mono<Signature> sign(String keyName, VaultSignRequest request);

	/**
	 * Verify the cryptographic signature using {@code keyName} of the given
	 * {@link Plaintext} and {@link Signature}.
	 * @param keyName must not be empty or {@literal null}.
	 * @param plaintext must not be {@literal null}.
	 * @param signature Signature to be verified, must not be {@literal null}.
	 * @return {@literal true} if the signature is valid, {@literal false}
	 * otherwise.
	 */
	Mono<Boolean> verify(String keyName, Plaintext plaintext, Signature signature);

	/**
	 * Verify the cryptographic signature using {@code keyName} of the given
	 * {@link VaultSignRequest}.
	 * @param keyName must not be empty or {@literal null}.
	 * @param request {@link VaultSignatureVerificationRequest} must not be
	 * {@literal null}.
	 * @return the resulting {@link SignatureValidation}.
	 */
	Mono<SignatureValidation> verify(String keyName, VaultSignatureVerificationRequest request);

}
