/*
 * Copyright 2018 the original author or authors.
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

import org.springframework.lang.Nullable;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * Interface that specifies a basic set of Vault operations using Vault's Key/Value secret
 * backend. Paths used in this operations interface are relative and outgoing requests
 * prepend paths with the according operation-specific prefix.
 * <p/>
 * This API supports both, versioned and unversioned key-value backends. Versioned usage
 * is limited as updates requiring compare-and-set (CAS) are not possible. Use
 * {@link VaultVersionedKeyValueOperations} in such cases instead.
 *
 * @author Mark Paluch
 * @since 2.1
 * @see VaultVersionedKeyValueOperations
 * @see KeyValueBackend
 */
public interface VaultKeyValueOperations extends VaultKeyValueOperationsSupport {

	/**
	 * Read the secret at {@code path}.
	 *
	 * @param path must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	@Nullable
	VaultResponse get(String path);

	/**
	 * Read the secret at {@code path}.
	 *
	 * @param path must not be {@literal null}.
	 * @param responseType must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	@Nullable
	<T> VaultResponseSupport<T> get(String path, Class<T> responseType);

	/**
	 * Write the secret at {@code path}.
	 *
	 * @param path must not be {@literal null}.
	 * @param body must not be {@literal null}.
	 * @return the resulting {@link VaultResponse}.
	 */
	void put(String path, Object body);
}
