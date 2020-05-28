/*
 * Copyright 2016-2020 the original author or authors.
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

import java.util.List;

import org.springframework.lang.Nullable;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.client.RestClientException;

/**
 * Interface that specifies a basic set of Vault operations, implemented by
 * {@link VaultTemplate}. This is the main entry point to interact with Vault in an
 * authenticated and unauthenticated context.
 * <p>
 * {@link VaultOperations} allows execution of callback methods. Callbacks can execute
 * requests within a {@link VaultOperations#doWithSession(RestOperationsCallback) session
 * context} and the {@link VaultOperations#doWithVault(RestOperationsCallback) without a
 * session}.
 *
 * @author Mark Paluch
 * @see VaultOperations#doWithSession(RestOperationsCallback)
 * @see VaultOperations#doWithVault(RestOperationsCallback)
 * @see org.springframework.web.client.RestOperations
 * @see VaultTemplate
 * @see VaultTokenOperations
 * @see org.springframework.vault.authentication.SessionManager
 */
public interface VaultOperations {

	/**
	 * Return {@link VaultKeyValueOperations}.
	 * @param path the mount path, must not be empty or {@literal null}.
	 * @param apiVersion API version to use, must not be {@literal null}.
	 * @return the operations interface to interact with the Vault Key/Value backend.
	 * @since 2.1
	 */
	VaultKeyValueOperations opsForKeyValue(String path, KeyValueBackend apiVersion);

	/**
	 * Return {@link VaultVersionedKeyValueOperations}.
	 * @param path the mount path
	 * @return the operations interface to interact with the versioned Vault Key/Value
	 * (version 2) backend.
	 * @since 2.1
	 */
	VaultVersionedKeyValueOperations opsForVersionedKeyValue(String path);

	/**
	 * @return the operations interface to interact with the Vault PKI backend.
	 */
	VaultPkiOperations opsForPki();

	/**
	 * Return {@link VaultPkiOperations} if the PKI backend is mounted on a different path
	 * than {@code pki}.
	 * @param path the mount path
	 * @return the operations interface to interact with the Vault PKI backend.
	 */
	VaultPkiOperations opsForPki(String path);

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
	 * Return {@link VaultTransitOperations} if the transit backend is mounted on a
	 * different path than {@code transit}.
	 * @param path the mount path
	 * @return the operations interface to interact with the Vault transit backend.
	 */
	VaultTransitOperations opsForTransit(String path);

	/**
	 * @return the operations interface to interact with the Vault system/wrapping
	 * endpoints.
	 * @since 2.1
	 */
	VaultWrappingOperations opsForWrapping();

	/**
	 * Read from a Vault path. Reading data using this method is suitable for API
	 * calls/secret backends that do not require a request body.
	 * @param path must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	@Nullable
	VaultResponse read(String path);

	/**
	 * Read from a secret backend. Reading data using this method is suitable for secret
	 * backends that do not require a request body.
	 * @param path must not be {@literal null}.
	 * @param responseType must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	@Nullable
	<T> VaultResponseSupport<T> read(String path, Class<T> responseType);

	/**
	 * Enumerate keys from a Vault path.
	 * @param path must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	@Nullable
	List<String> list(String path);

	/**
	 * Write to a Vault path.
	 * @param path must not be {@literal null}.
	 * @return the response, may be {@literal null}.
	 * @since 2.0
	 */
	@Nullable
	default VaultResponse write(String path) {
		return write(path, null);
	}

	/**
	 * Write to a Vault path.
	 * @param path must not be {@literal null}.
	 * @param body the body, may be {@literal null} if absent.
	 * @return the response, may be {@literal null}.
	 */
	@Nullable
	VaultResponse write(String path, @Nullable Object body);

	/**
	 * Delete a path.
	 * @param path must not be {@literal null}.
	 */
	void delete(String path);

	/**
	 * Executes a Vault {@link RestOperationsCallback}. Allows to interact with Vault
	 * using {@link org.springframework.web.client.RestOperations} without requiring a
	 * session.
	 * @param clientCallback the request.
	 * @return the {@link RestOperationsCallback} return value.
	 * @throws VaultException when a
	 * {@link org.springframework.web.client.HttpStatusCodeException} occurs.
	 * @throws RestClientException exceptions from
	 * {@link org.springframework.web.client.RestOperations}.
	 */
	@Nullable
	<T> T doWithVault(RestOperationsCallback<T> clientCallback) throws VaultException, RestClientException;

	/**
	 * Executes a Vault {@link RestOperationsCallback}. Allows to interact with Vault in
	 * an authenticated session.
	 * @param sessionCallback the request.
	 * @return the {@link RestOperationsCallback} return value.
	 * @throws VaultException when a
	 * {@link org.springframework.web.client.HttpStatusCodeException} occurs.
	 * @throws RestClientException exceptions from
	 * {@link org.springframework.web.client.RestOperations}.
	 */
	@Nullable
	<T> T doWithSession(RestOperationsCallback<T> sessionCallback) throws VaultException, RestClientException;

}
