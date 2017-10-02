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
package org.springframework.vault.support;

import java.util.Map;

/**
 * A key inside Vault's {@code transit} backend.
 *
 * @author Mark Paluch
 * @author Sven Schürmann
 */
public interface VaultTransitKey {

	/**
	 * @return {@literal true} if deletion of the key is allowed. Key deletion must be
	 * turned on to make keys deletable.
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
	 * @return {@literal true} if the key represents the latest version.
	 */
	boolean isLatestVersion();

	/**
	 * @return required key version to still be able to decrypt data.
	 */
	int getMinDecryptionVersion();

	/**
	 * @return name of the key
	 */
	String getName();

	/**
	 * @return the key type ({@code aes-gcm}, {@code ecdsa-p256}, ...).
	 */
	String getType();
}
