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
package org.springframework.vault.core;

import java.util.List;

import org.springframework.lang.Nullable;
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
import org.springframework.vault.support.VaultSignRequest;
import org.springframework.vault.support.VaultSignatureVerificationRequest;
import org.springframework.vault.support.VaultTransitContext;
import org.springframework.vault.support.VaultTransitKey;
import org.springframework.vault.support.VaultTransitKeyConfiguration;
import org.springframework.vault.support.VaultTransitKeyCreationRequest;

/**
 * Interface that specifies operations using the {@code transit} backend.
 *
 * @author Mark Paluch
 * @author Sven Schürmann
 * @author Praveendra Singh
 * @author Luander Ribeiro
 * @author Nanne Baars
 * @see <a href="https://www.vaultproject.io/docs/secrets/transit/index.html">Transit
 * Secret Backend</a>
 */
public interface VaultTransitOperations {

	/**
	 * Create a new named encryption key given a {@code name}.
	 * @param keyName must not be empty or {@literal null}.
	 */
	void createKey(String keyName);

	/**
	 * Create a new named encryption key given a {@code name} and
	 * {@link VaultTransitKeyCreationRequest}. The key options set here cannot be changed
	 * after key creation.
	 * @param keyName must not be empty or {@literal null}.
	 * @param createKeyRequest must not be {@literal null}.
	 */
	void createKey(String keyName, VaultTransitKeyCreationRequest createKeyRequest);

	/**
	 * Get a {@link List} of transit key names.
	 * @return {@link List} of transit key names.
	 */
	List<String> getKeys();

	/**
	 * Create a new named encryption key given a {@code name}.
	 * @param keyName must not be empty or {@literal null}.
	 * @param keyConfiguration must not be {@literal null}.
	 */
	void configureKey(String keyName, VaultTransitKeyConfiguration keyConfiguration);

	/**
	 * Returns the value of the named encryption key. Depending on the type of key,
	 * different information may be returned. The key must be exportable to support this
	 * operation.
	 * @param keyName must not be empty or {@literal null}.
	 * @param type must not be {@literal null}.
	 * @return the {@link RawTransitKey}.
	 */
	@Nullable
	RawTransitKey exportKey(String keyName, TransitKeyType type);

	/**
	 * Return information about a named encryption key.
	 * @param keyName must not be empty or {@literal null}.
	 * @return the {@link VaultTransitKey}.
	 */
	@Nullable
	VaultTransitKey getKey(String keyName);

	/**
	 * Deletes a named encryption key. It will no longer be possible to decrypt any data
	 * encrypted with the named key.
	 * @param keyName must not be empty or {@literal null}.
	 */
	void deleteKey(String keyName);

	/**
	 * Rotates the version of the named key. After rotation, new plain text requests will
	 * be encrypted with the new version of the key. To upgrade ciphertext to be encrypted
	 * with the latest version of the key, use {@link #rewrap(String, String)}.
	 * @param keyName must not be empty or {@literal null}.
	 * @see #rewrap(String, String)
	 */
	void rotate(String keyName);

	/**
	 * Encrypts the provided plain text using the named key. The given {@code plaintext}
	 * is encoded into bytes using the {@link java.nio.charset.Charset#defaultCharset()
	 * default charset}. Use
	 * {@link #encrypt(String, org.springframework.vault.support.Plaintext)} to construct
	 * a {@link org.springframework.vault.support.Plaintext#of(byte[]) Plaintext} object
	 * from bytes to avoid {@link java.nio.charset.Charset} mismatches.
	 * @param keyName must not be empty or {@literal null}.
	 * @param plaintext must not be empty or {@literal null}.
	 * @return cipher text.
	 */
	String encrypt(String keyName, String plaintext);

	/**
	 * Encrypts the provided {@code plaintext} using the named key.
	 * @param keyName must not be empty or {@literal null}.
	 * @param plaintext must not be {@literal null}.
	 * @since 1.1
	 * @return cipher text.
	 */
	Ciphertext encrypt(String keyName, Plaintext plaintext);

	/**
	 * Encrypts the provided {@code plaintext} using the named key.
	 * @param keyName must not be empty or {@literal null}.
	 * @param plaintext must not be empty or {@literal null}.
	 * @param transitRequest must not be {@literal null}. Use
	 * {@link VaultTransitContext#empty()} if no request options provided.
	 * @return cipher text.
	 */
	String encrypt(String keyName, byte[] plaintext, VaultTransitContext transitRequest);

	/**
	 * Encrypts the provided batch of {@code plaintext} using the named key and context.
	 * The encryption is done using transit backend's batch operation.
	 * @param keyName must not be empty or {@literal null}.
	 * @param batchRequest a list of {@link Plaintext} which includes plain text and an
	 * optional context.
	 * @return the encrypted result in the order of {@code batchRequest} plaintexts.
	 * @since 1.1
	 */
	List<VaultEncryptionResult> encrypt(String keyName, List<Plaintext> batchRequest);

	/**
	 * Decrypts the provided plain text using the named key. The decoded {@code plaintext}
	 * is decoded into {@link String} the {@link java.nio.charset.Charset#defaultCharset()
	 * default charset}. Use
	 * {@link #decrypt(String, org.springframework.vault.support.Ciphertext)} to obtain a
	 * {@link org.springframework.vault.support.Ciphertext} object that allows to control
	 * the {@link java.nio.charset.Charset} for later consumption.
	 * @param keyName must not be empty or {@literal null}.
	 * @param ciphertext must not be empty or {@literal null}.
	 * @return plain text.
	 */
	String decrypt(String keyName, String ciphertext);

	/**
	 * Decrypts the provided cipher text using the named key.
	 * @param keyName must not be empty or {@literal null}.
	 * @param ciphertext must not be {@literal null}.
	 * @return plain text.
	 * @since 1.1
	 */
	Plaintext decrypt(String keyName, Ciphertext ciphertext);

	/**
	 * Decrypts the provided {@code ciphertext} using the named key.
	 * @param keyName must not be empty or {@literal null}.
	 * @param ciphertext must not be empty or {@literal null}.
	 * @param transitContext must not be {@literal null}. Use
	 * {@link VaultTransitContext#empty()} if no request options provided.
	 * @return cipher text.
	 * @return plain text.
	 */
	byte[] decrypt(String keyName, String ciphertext, VaultTransitContext transitContext);

	/**
	 * Decrypts the provided batch of cipher text using the named key and context. The*
	 * decryption is done using transit backend's batch operation.
	 * @param keyName must not be empty or {@literal null}.
	 * @param batchRequest a list of {@link Ciphertext} which includes plain text and an
	 * optional context.
	 * @return the decrypted result in the order of {@code batchRequest} ciphertexts.
	 * @since 1.1
	 */
	List<VaultDecryptionResult> decrypt(String keyName, List<Ciphertext> batchRequest);

	/**
	 * Rewrap the provided cipher text using the latest version of the named key. Because
	 * this never returns plain text, it is possible to delegate this functionality to
	 * untrusted users or scripts.
	 * @param keyName must not be empty or {@literal null}.
	 * @param ciphertext must not be empty or {@literal null}.
	 * @return cipher text.
	 * @see #rotate(String)
	 */
	String rewrap(String keyName, String ciphertext);

	/**
	 * Rewrap the provided cipher text using the latest version of the named key. Because
	 * this never returns plain text, it is possible to delegate this functionality to
	 * untrusted users or scripts.
	 * @param keyName must not be empty or {@literal null}.
	 * @param ciphertext must not be empty or {@literal null}.
	 * @param transitContext must not be {@literal null}. Use
	 * {@link VaultTransitContext#empty()} if no request options provided.
	 * @return cipher text.
	 * @see #rotate(String)
	 */
	String rewrap(String keyName, String ciphertext, VaultTransitContext transitContext);

	/**
	 * Rewrap the provided batch of cipher text using the latest version of the named key.
	 * @param batchRequest a list of {@link Ciphertext} which includes cipher text and a
	 * context
	 * @return the rewrapped result in the order of {@code batchRequest} ciphertexts.
	 * @see #rewrap(String, String)
	 * @since 3.1
	 */
	List<VaultEncryptionResult> rewrap(String keyName, List<Ciphertext> batchRequest);

	/**
	 * Create a HMAC using {@code keyName} of given {@link Plaintext} using the default
	 * hash algorithm. The key can be of any type supported by transit; the raw key will
	 * be marshaled into bytes to be used for the HMAC function. If the key is of a type
	 * that supports rotation, the latest (current) version will be used.
	 * @param keyName must not be empty or {@literal null}.
	 * @param plaintext must not be {@literal null}.
	 * @return the digest of given data the default hash algorithm and the named key.
	 * @since 2.0
	 */
	Hmac getHmac(String keyName, Plaintext plaintext);

	/**
	 * Create a HMAC using {@code keyName} of given {@link VaultHmacRequest} using the
	 * default hash algorithm. The key can be of any type supported by transit; the raw
	 * key will be marshaled into bytes to be used for the HMAC function. If the key is of
	 * a type that supports rotation, configured {@link VaultHmacRequest#getKeyVersion()}
	 * will be used.
	 * @param keyName must not be empty or {@literal null}.
	 * @param request the {@link VaultHmacRequest}, must not be {@literal null}.
	 * @return the digest of given data the default hash algorithm and the named key.
	 * @since 2.0
	 */
	Hmac getHmac(String keyName, VaultHmacRequest request);

	/**
	 * Create a cryptographic signature using {@code keyName} of the given
	 * {@link Plaintext} and the default hash algorithm. The key must be of a type that
	 * supports signing.
	 * @param keyName must not be empty or {@literal null}.
	 * @param plaintext must not be empty or {@literal null}.
	 * @return Signature for {@link Plaintext}.
	 * @since 2.0
	 */
	Signature sign(String keyName, Plaintext plaintext);

	/**
	 * Create a cryptographic signature using {@code keyName} of the given
	 * {@link VaultSignRequest} and the specified hash algorithm. The key must be of a
	 * type that supports signing.
	 * @param keyName must not be empty or {@literal null}.
	 * @param request {@link VaultSignRequest} must not be empty or {@literal null}.
	 * @return Signature for {@link VaultSignRequest}.
	 * @since 2.0
	 */
	Signature sign(String keyName, VaultSignRequest request);

	/**
	 * Verify the cryptographic signature using {@code keyName} of the given
	 * {@link Plaintext} and {@link Signature}.
	 * @param keyName must not be empty or {@literal null}.
	 * @param plaintext must not be {@literal null}.
	 * @param signature Signature to be verified, must not be {@literal null}.
	 * @return {@literal true} if the signature is valid, {@literal false} otherwise.
	 * @since 2.0
	 */
	boolean verify(String keyName, Plaintext plaintext, Signature signature);

	/**
	 * Verify the cryptographic signature using {@code keyName} of the given
	 * {@link VaultSignRequest}.
	 * @param keyName must not be empty or {@literal null}.
	 * @param request {@link VaultSignatureVerificationRequest} must not be
	 * {@literal null}.
	 * @return the resulting {@link SignatureValidation}.
	 * @since 2.0
	 */
	SignatureValidation verify(String keyName, VaultSignatureVerificationRequest request);

}
