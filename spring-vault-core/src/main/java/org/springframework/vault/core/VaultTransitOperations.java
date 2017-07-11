/*
 * Copyright 2016-2017 the original author or authors.
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

import java.util.List;

import org.springframework.lang.Nullable;
import org.springframework.vault.support.RawTransitKey;
import org.springframework.vault.support.TransitKeyType;
import org.springframework.vault.support.VaultTransitContext;
import org.springframework.vault.support.VaultTransitKey;
import org.springframework.vault.support.VaultTransitKeyConfiguration;
import org.springframework.vault.support.VaultTransitKeyCreationRequest;

/**
 * Interface that specifies operations using the {@code transit} backend.
 *
 * @author Mark Paluch
 * @author Sven Schürmann
 * @see <a href="https://www.vaultproject.io/docs/secrets/transit/index.html">Transit
 * Secret Backend</a>
 */
public interface VaultTransitOperations {

	/**
	 * Create a new named encryption key given a {@code name}.
	 *
	 * @param keyName must not be empty or {@literal null}.
	 */
	void createKey(String keyName);

	/**
	 * Create a new named encryption key given a {@code name} and
	 * {@link VaultTransitKeyCreationRequest}. The key options set here cannot be changed
	 * after key creation.
	 *
	 * @param keyName must not be empty or {@literal null}.
	 * @param createKeyRequest must not be {@literal null}.
	 */
	void createKey(String keyName, VaultTransitKeyCreationRequest createKeyRequest);

	/**
	 * Get a {@link List} of transit key names.
	 *
	 * @return {@link List} of transit key names.
	 */
	List<String> getKeys();

	/**
	 * Create a new named encryption key given a {@code name}.
	 *
	 * @param keyName must not be empty or {@literal null}.
	 * @param keyConfiguration must not be {@literal null}.
	 */
	void configureKey(String keyName, VaultTransitKeyConfiguration keyConfiguration);

	/**
	 * Returns the value of the named encryption key. Depending on the type of key,
	 * different information may be returned. The key must be exportable to support this
	 * operation.
	 *
	 * @param keyName must not be empty or {@literal null}.
	 * @param type must not be {@literal null}.
	 * @return the {@link RawTransitKey}.
	 */
	@Nullable
	RawTransitKey exportKey(String keyName, TransitKeyType type);

	/**
	 * Return information about a named encryption key.
	 *
	 * @param keyName must not be empty or {@literal null}.
	 * @return the {@link VaultTransitKey}.
	 */
	@Nullable
	VaultTransitKey getKey(String keyName);

	/**
	 * Deletes a named encryption key. It will no longer be possible to decrypt any data
	 * encrypted with the named key.
	 *
	 * @param keyName must not be empty or {@literal null}.
	 */
	void deleteKey(String keyName);

	/**
	 * Rotates the version of the named key. After rotation, new plaintext requests will
	 * be encrypted with the new version of the key. To upgrade ciphertext to be encrypted
	 * with the latest version of the key, use {@link #rewrap(String, String)}.
	 *
	 * @param keyName must not be empty or {@literal null}.
	 * @see #rewrap(String, String)
	 */
	void rotate(String keyName);

	/**
	 * Encrypts the provided plaintext using the named key.
	 *
	 * @param keyName must not be empty or {@literal null}.
	 * @param plaintext must not be empty or {@literal null}.
	 * @return cipher text.
	 */
	String encrypt(String keyName, String plaintext);

	/**
	 * Encrypts the provided plaintext using the named key.
	 *
	 * @param keyName must not be empty or {@literal null}.
	 * @param plaintext must not be empty or {@literal null}.
	 * @param transitRequest must not be {@literal null}. Use
	 * {@link VaultTransitContext#empty()} if no request options provided.
	 * @return cipher text.
	 */
	String encrypt(String keyName, byte[] plaintext, VaultTransitContext transitRequest);

	/**
	 * Decrypts the provided plaintext using the named key.
	 *
	 * @param keyName must not be empty or {@literal null}.
	 * @param ciphertext must not be empty or {@literal null}.
	 * @return plain text.
	 */
	String decrypt(String keyName, String ciphertext);

	/**
	 * Decrypts the provided plaintext using the named key.
	 *
	 * @param keyName must not be empty or {@literal null}.
	 * @param ciphertext must not be empty or {@literal null}.
	 * @param transitContext must not be {@literal null}. Use
	 * {@link VaultTransitContext#empty()} if no request options provided.
	 * @return cipher text.
	 * @return plain text.
	 */
	byte[] decrypt(String keyName, String ciphertext, VaultTransitContext transitContext);

	/**
	 * Rewrap the provided ciphertext using the latest version of the named key. Because
	 * this never returns plaintext, it is possible to delegate this functionality to
	 * untrusted users or scripts.
	 *
	 * @param keyName must not be empty or {@literal null}.
	 * @param ciphertext must not be empty or {@literal null}.
	 * @return cipher text.
	 * @see #rotate(String)
	 */
	String rewrap(String keyName, String ciphertext);

	/**
	 * Rewrap the provided ciphertext using the latest version of the named key. Because
	 * this never returns plaintext, it is possible to delegate this functionality to
	 * untrusted users or scripts.
	 *
	 * @param keyName must not be empty or {@literal null}.
	 * @param ciphertext must not be empty or {@literal null}.
	 * @param transitContext must not be {@literal null}. Use
	 * {@link VaultTransitContext#empty()} if no request options provided.
	 * @return cipher text.
	 * @see #rotate(String)
	 */
	String rewrap(String keyName, String ciphertext, VaultTransitContext transitContext);
}
