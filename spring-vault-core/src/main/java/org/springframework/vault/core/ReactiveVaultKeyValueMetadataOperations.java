package org.springframework.vault.core;

import org.springframework.vault.support.VaultMetadataRequest;
import org.springframework.vault.support.VaultMetadataResponse;
import reactor.core.publisher.Mono;

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
	 */
	Mono<Void> put(String path, VaultMetadataRequest body);

	/**
	 * Permanently delete the key metadata and all version data for the specified key. All
	 * version history will be removed.
	 * @param path the secret path, must not be {@literal null} or empty.
	 */
	Mono<Void> delete(String path);

}
