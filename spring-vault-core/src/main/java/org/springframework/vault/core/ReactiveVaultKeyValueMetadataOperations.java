/*
 * Copyright 2023-2025 the original author or authors.
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

import reactor.core.publisher.Mono;

import org.springframework.vault.support.VaultMetadataRequest;
import org.springframework.vault.support.VaultMetadataResponse;

/**
 * Interface that specifies a basic set of Vault operations using Vault's versioned
 * Key/Value (kv version 2) secret backend. Paths used in this operations interface are
 * relative and outgoing requests prepend paths with the according operation-specific
 * prefix.
 * <p/>
 * Clients using versioned Key/Value must be aware they are reading from a versioned
 * backend as the versioned Key/Value API (kv version 2) is different from the unversioned
 * Key/Value API (kv version 1).
 *
 * @author Timothy R. Weiand
 * @since 3.1
 * @see ReactiveVaultKeyValueOperations
 * @see VaultKeyValue2Template
 */
public interface ReactiveVaultKeyValueMetadataOperations {

	/**
	 * Retrieve the metadata and versions for the secret at the specified path.
	 * @param path the secret path, must not be {@literal null} or empty.
	 * @return {@link VaultMetadataResponse}
	 */
	Mono<VaultMetadataResponse> get(String path);

	/**
	 * Update the secret metadata, or creates new metadata if not present.
	 * @param path the secret path, must not be {@literal null} or empty.
	 * @param body {@link VaultMetadataRequest}
	 * @return a Mono signalling completion or an error.
	 */
	Mono<Void> put(String path, VaultMetadataRequest body);

	/**
	 * Permanently delete the key metadata and all version data for the specified key. All
	 * version history will be removed.
	 * @param path the secret path, must not be {@literal null} or empty.
	 * @return a Mono signalling completion or an error.
	 */
	Mono<Void> delete(String path);

}
