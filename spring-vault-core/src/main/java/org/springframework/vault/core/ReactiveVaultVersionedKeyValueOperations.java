package org.springframework.vault.core;

import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.vault.support.Versioned;
import org.springframework.vault.support.Versioned.Metadata;
import org.springframework.vault.support.Versioned.Version;

/**
 * Interface that specifies a basic set of Vault operations using Vault's
 * versioned Key/Value (kv version 2) secret backend. Paths used in this
 * operations interface are relative and outgoing requests prepend paths with
 * the according operation-specific prefix.
 * <p>Clients using versioned Key/Value must be aware they are reading from a
 * versioned backend as the versioned Key/Value API (kv version 2) is different
 * from the unversioned Key/Value API (kv version 1).
 *
 * @author Timothy R. Weiand
 * @since 3.1
 * @see ReactiveVaultKeyValueOperations
 */
public interface ReactiveVaultVersionedKeyValueOperations extends ReactiveVaultKeyValueOperationsSupport {

	/**
	 * Read the most recent secret at {@code path}.
	 * @param path must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	@Override
	default Mono<Versioned<Map<String, Object>>> get(String path) {
		return get(path, Version.unversioned());
	}

	/**
	 * Read the requested {@link Version} of the secret at {@code path}.
	 * @param path must not be {@literal null}.
	 * @param version must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	<T> Mono<Versioned<T>> get(String path, Version version);

	/**
	 * Read the most recent secret at {@code path} and deserialize the secret to the
	 * given {@link Class responseType}.
	 * @param path must not be {@literal null}.
	 * @param responseType must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	default <T> Mono<Versioned<T>> get(String path, Class<T> responseType) {
		return get(path, Version.unversioned(), responseType);
	}

	/**
	 * Read the requested {@link Version} of the secret at {@code path} and
	 * deserialize the secret to the given {@link Class responseType}.
	 * @param path must not be {@literal null}.
	 * @param version must not be {@literal null}.
	 * @param responseType must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	<T> Mono<Versioned<T>> get(String path, Version version, Class<T> responseType);

	/**
	 * Write the {@link Versioned versioned secret} at {@code path}. {@code body}
	 * may be either plain secrets (e.g. map) or {@link Versioned} objects. Using
	 * {@link Versioned} will apply versioning for Compare-and-Set (CAS).
	 * @param path must not be {@literal null}.
	 * @param body must not be {@literal null}.
	 * @return the resulting {@link Metadata}.
	 */
	Mono<Metadata> put(String path, Object body);

	/**
	 * Delete one or more {@link Version versions} of the secret at {@code path}.
	 * @param path must not be {@literal null}.
	 * @param versionsToDelete must not be {@literal null} or empty.
	 */
	Mono<Void> delete(String path, Version... versionsToDelete);

	/**
	 * Undelete (restore) one or more {@link Version versions} of the secret at
	 * {@code path}.
	 * @param path must not be {@literal null}.
	 * @param versionsToDelete must not be {@literal null} or empty.
	 */
	Mono<Void> undelete(String path, Version... versionsToDelete);

	/**
	 * Permanently remove the specified {@link Version versions} of the secret at
	 * {@code path}.
	 * @param path must not be {@literal null}.
	 * @param versionsToDelete must not be {@literal null} or empty.
	 */
	Mono<Void> destroy(String path, Version... versionsToDelete);

	/**
	 * Return {@link ReactiveVaultKeyValueMetadataOperations}
	 * @return the operations interface to interact with the Vault Key/Value
	 * metadata backend
	 */
	ReactiveVaultKeyValueMetadataOperations opsForKeyValueMetadata();

}
