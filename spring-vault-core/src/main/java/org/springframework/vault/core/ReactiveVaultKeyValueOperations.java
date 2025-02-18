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

import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * Interface that specifies a basic set of Vault operations using Vault's Key/Value secret
 * backend. Paths used in this operations interface are relative and outgoing requests
 * prepend paths with the according operation-specific prefix.
 * <p/>
 * This API supports both, versioned and unversioned key-value backends. Versioned usage
 * is limited as updates requiring compare-and-set (CAS) are not possible. Use
 * {@link ReactiveVaultVersionedKeyValueOperations} in such cases instead.
 *
 * @author Timothy R. Weiand
 * @author Mark Paluch
 * @since 3.1
 * @see ReactiveVaultVersionedKeyValueOperations
 * @see KeyValueBackend
 */
public interface ReactiveVaultKeyValueOperations extends ReactiveVaultKeyValueOperationsSupport {

	/**
	 * Read the secret at {@code path}.
	 * @param path must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	@Override
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
	 * Key-Value version 2 mount to ensure an atomic update.
	 * @param path must not be {@literal null}.
	 * @param patch must not be {@literal null}.
	 * @return {@code true} if the patch operation is successful, {@code false} otherwise.
	 */
	Mono<Boolean> patch(String path, Map<String, ?> patch);

	/**
	 * Write the secret at {@code path}.
	 * @param path must not be {@literal null}.
	 * @param body must not be {@literal null}.
	 * @return a Mono signalling completion or an error.
	 */
	Mono<Void> put(String path, Object body);

}
