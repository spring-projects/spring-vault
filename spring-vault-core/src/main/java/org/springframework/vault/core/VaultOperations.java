/*
 * Copyright 2016 the original author or authors.
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

import org.springframework.vault.client.VaultClient;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * Interface that specifies a basic set of Vault operations, implemented by
 * {@link VaultTemplate}. This is the main entry point to interact with Vault in an
 * authenticated and unauthenticated context.
 * <p>
 * {@link VaultOperations} allows execution of callback methods on various levels.
 * Callbacks can execute requests within a
 * {@link VaultOperations#doWithSession(RestOperationsCallback) session} and the
 * {@link VaultOperations#doWithVault(RestOperationsCallback)} client} (without requiring
 * a session) level.
 *
 * @author Mark Paluch
 * @see VaultOperations#doWithSession(RestOperationsCallback)
 * @see VaultOperations#doWithVault(RestOperationsCallback)
 * @see VaultClient
 * @see VaultTemplate
 * @see VaultTokenOperations
 * @see org.springframework.vault.authentication.SessionManager
 */
public interface VaultOperations {

	/**
	 * @return the operations interface administrative Vault access.
	 */
	VaultSysOperations opsForSys();

	/**
	 * @return the operations interface to interact with Vault token.
	 */
	VaultTokenOperations opsForToken();

	/**
	 * @return the operations interface to interact with the Vault transit backend.
	 */
	VaultTransitOperations opsForTransit();

	/**
	 * Returns {@link VaultTransitOperations} if the transit backend is mounted on a
	 * different path than {@code transit}.
	 * 
	 * @param path the mount path
	 * @return the operations interface to interact with the Vault transit backend.
	 */
	VaultTransitOperations opsForTransit(String path);

	/**
	 * @return the operations interface to interact with the Vault PKI backend.
	 */
	VaultPkiOperations opsForPki();

	/**
	 * Returns {@link VaultPkiOperations} if the PKI backend is mounted on a different
	 * path than {@code pki}.
	 *
	 * @param path the mount path
	 * @return the operations interface to interact with the Vault PKI backend.
	 */
	VaultPkiOperations opsForPki(String path);

	/**
	 * Read from a secret backend. Reading data using this method is suitable for secret
	 * backends that do not require a request body.
	 *
	 * @param path must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	VaultResponse read(String path);

	/**
	 * Read from a secret backend. Reading data using this method is suitable for secret
	 * backends that do not require a request body.
	 *
	 * @param path must not be {@literal null}.
	 * @param responseType must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	<T> VaultResponseSupport<T> read(String path, Class<T> responseType);

	/**
	 * Enumerate keys from a secret backend.
	 *
	 * @param path must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	List<String> list(String path);

	/**
	 * Write to a secret backend.
	 *
	 * @param path must not be {@literal null}.
	 * @param body the body, may be {@literal null} if absent.
	 * @return the configuration data. May be empty but never {@literal null}.
	 */
	VaultResponse write(String path, Object body);

	/**
	 * Delete a path in the secret backend.
	 *
	 * @param path must not be {@literal null}.
	 */
	void delete(String path);

	/**
	 * Executes a Vault {@link RestOperationsCallback}. Allows to interact with Vault
	 * using {@link VaultClient} without requiring a session.
	 *
	 * @param clientCallback the request.
	 * @return the {@link RestOperationsCallback} return value.
	 */
	<T> T doWithVault(RestOperationsCallback<T> clientCallback);

	/**
	 * Executes a Vault {@link RestOperationsCallback}. Allows to interact with Vault in
	 * an authenticated session.
	 *
	 * @param sessionCallback the request.
	 * @return the {@link RestOperationsCallback} return value.
	 */
	<T> T doWithSession(RestOperationsCallback<T> sessionCallback);

}
