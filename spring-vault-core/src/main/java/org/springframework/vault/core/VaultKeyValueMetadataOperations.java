/*
 * Copyright 2020-2021 the original author or authors.
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

import org.springframework.lang.Nullable;
import org.springframework.vault.support.VaultMetadataRequest;
import org.springframework.vault.support.VaultMetadataResponse;

/**
 * Interface that specifies kv metadata related operations.
 *
 * @author Zakaria Amine
 * @see <a href=
 * "https://www.vaultproject.io/api-docs/secret/kv/kv-v2#update-metadata">Key-Value
 * Metadata API</a>
 * @since 2.3
 */
public interface VaultKeyValueMetadataOperations {

	/**
	 * Retrieve the metadata and versions for the secret at the specified path.
	 * @param path the secret path, must not be {@literal null} or empty.
	 * @return {@link VaultMetadataResponse}
	 */
	@Nullable
	VaultMetadataResponse get(String path);

	/**
	 * Update the secret metadata, or creates new metadata if not present.
	 * @param path the secret path, must not be {@literal null} or empty.
	 * @param body {@link VaultMetadataRequest}
	 */
	void put(String path, VaultMetadataRequest body);

	/**
	 * Permanently delete the key metadata and all version data for the specified key. All
	 * version history will be removed.
	 * @param path the secret path, must not be {@literal null} or empty.
	 */
	void delete(String path);

}
