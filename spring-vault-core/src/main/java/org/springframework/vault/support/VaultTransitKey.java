/*
 * Copyright 2016-2025 the original author or authors.
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

package org.springframework.vault.support;

import java.util.Map;

/**
 * A key inside Vault's {@code transit} backend.
 *
 * @author Mark Paluch
 * @author Sven Schürmann
 * @author Nanne Baars
 */
public interface VaultTransitKey {

	/**
	 * @return name of the key
	 */
	String getName();

	/**
	 * @return the key type ({@code aes-gcm}, {@code ecdsa-p256}, ...).
	 */
	String getType();

	/**
	 * @return whether the key can be backed up in the plaintext format. Once set,
	 * this cannot be disabled.
	 * @since 3.0.3
	 */
	boolean allowPlaintextBackup();

	/**
	 * @return the version of the convergent nonce to use. Note: since version 3 the
	 * algorithm used in {@code transit} convergent encryption returns {@code -1} as
	 * the version is stored with the key. For backwards compatability this field
	 * might be useful.
	 * @since 3.0.3
	 */
	int getConvergentVersion();

	/**
	 * @return {@literal true} if deletion of the key is allowed. Key deletion must
	 * be turned on to make keys deletable.
	 */
	boolean isDeletionAllowed();

	/**
	 * @return {@literal true} if key derivation MUST be used.
	 */
	boolean isDerived();

	/**
	 * @return {@literal true} if the raw key is exportable.
	 */
	boolean isExportable();

	/**
	 * @return a {@link Map} of key version to its Vault-specific representation.
	 */
	Map<String, Object> getKeys();

	/**
	 * @return the latest key version.
	 */
	int getLatestVersion();

	/**
	 * @return required key version to still be able to decrypt data.
	 */
	int getMinDecryptionVersion();

	/**
	 * @return required key version to encrypt data.
	 * @since 1.1
	 */
	int getMinEncryptionVersion();

	/**
	 * @return whether the key supports convergent encryption (i.e where the same
	 * plaintext creates the same ciphertext). Requires {@link #isDerived()} to be
	 * set to {@code true}.
	 * @since 3.0.3
	 */
	boolean supportsConvergentEncryption();

	/**
	 * @return whether the key supports decryption.
	 * @since 1.1
	 */
	boolean supportsDecryption();

	/**
	 * @return whether the key supports derivation.
	 * @since 1.1
	 */
	boolean supportsDerivation();

	/**
	 * @return whether the key supports encryption.
	 * @since 1.1
	 */
	boolean supportsEncryption();

	/**
	 * @return whether the key supports signing.
	 * @since 1.1
	 */
	boolean supportsSigning();

}
