package org.springframework.vault.core;

import java.util.Map;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import reactor.core.publisher.Mono;

/**
 * Interface that specifies a basic set of Vault operations using Vault's Key/Value secret
 * backend. Paths used in this operations interface are relative and outgoing requests
 * prepend paths with the according operation-specific prefix.
 * <p/>
 * This API supports both, versioned and unversioned key-value backends. Versioned usage
 * is limited as updates requiring compare-and-set (CAS) are not possible. Use
 * {@link ReactiveVaultVersionedKeyValueOperations} in such cases instead.
 *
 * TODO: Update JavaDocs
 *
 * @author Timothy R. Weiand
 * @since TBD
 * @see ReactiveVaultVersionedKeyValueOperations
 * @see KeyValueBackend
 */
public interface ReactiveVaultKeyValueOperations extends ReactiveVaultKeyValueOperationsSupport {

	/**
	 * Read the secret at {@code path}.
	 * @param path must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	Mono<VaultResponse> get(String path);

	/**
	 * Read the secret at {@code path}.
	 * @param path must not be {@literal null}.
	 * @param responseType must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	<T> Mono<VaultResponseSupport<T>> get(String path, Class<T> responseType);

	/**
	 * Update the secret at {@code path} without removing the existing secrets. Requires a
	 * Key-Value version 2 mount to ensure an atomic update. TODO: Throw error if false?
	 * @param path must not be {@literal null}.
	 * @param patch must not be {@literal null}.
	 * @return {@code true} if the patch operation is successful, {@code false} otherwise.
	 */
	Mono<Void> patch(String path, Map<String, ?> patch);

	/**
	 * Write the secret at {@code path}.
	 * @param path must not be {@literal null}.
	 * @param body must not be {@literal null}.
	 */
	Mono<Void> put(String path, Object body);

}
