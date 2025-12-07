/*
 * Copyright 2016-2025 the original author or authors.
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

import org.jspecify.annotations.Nullable;

import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.client.RestClientException;

/**
 * Central entrypoint for performing Vault operations on a reactive runtime.
 *
 * <p>Implemented by {@link org.springframework.vault.core.VaultTemplate}, this
 * interface exposes reactive APIs for interacting with Vault backends such as
 * Key/Value, Transit and {@code sys}. It supports callback-style execution for
 * both {@link #doWithSession(RestOperationsCallback) authenticated
 * doWithSession} and {@link #doWithVault(RestOperationsCallback)
 * unauthenticated doWithVault} access.
 *
 * <p>{@link VaultClient.PathSpec#path Paths used} with this and other Template
 * API interfaces are typically relative to the {@link VaultEndpoint} of the
 * underlying {@link VaultClient}. If the client is configured without an
 * endpoint, fully-qualified URIs can be used.
 * <p><b>Note</b> that operations apply authentication and other headers
 * regardless of using relative or absolute URIs. To prevent unwanted access to
 * external endpoints using authentication headers, applications should sanitize
 * paths to avoid unwanted access to external endpoints.
 *
 * @author Mark Paluch
 * @author Lauren Voswinkel
 * @see #doWithSession(RestOperationsCallback)
 * @see #doWithVault(RestOperationsCallback)
 * @see org.springframework.vault.client.VaultClient
 * @see org.springframework.vault.core.VaultTemplate
 * @see org.springframework.vault.core.VaultTokenOperations
 * @see org.springframework.vault.authentication.SessionManager
 */
public interface VaultOperations {

	/**
	 * Return {@link VaultKeyValueOperations}.
	 * @param path the mount path, must not be empty or {@literal null}.
	 * @param apiVersion API version to use, must not be {@literal null}.
	 * @return the operations interface to interact with the Vault Key/Value
	 * backend.
	 * @since 2.1
	 */
	VaultKeyValueOperations opsForKeyValue(String path, KeyValueBackend apiVersion);

	/**
	 * Return {@link VaultVersionedKeyValueOperations}.
	 * @param path the mount path
	 * @return the operations interface to interact with the versioned Vault
	 * Key/Value (version 2) backend.
	 * @since 2.1
	 */
	VaultVersionedKeyValueOperations opsForVersionedKeyValue(String path);

	/**
	 * @return the operations interface to interact with the Vault PKI backend.
	 */
	VaultPkiOperations opsForPki();

	/**
	 * Return {@link VaultPkiOperations} if the PKI backend is mounted on a
	 * different path than {@code pki}.
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
	 * @return the operations interface to interact with the Vault transform
	 * backend.
	 * @since 2.3
	 */
	VaultTransformOperations opsForTransform();

	/**
	 * Return {@link VaultTransformOperations} if the transit backend is mounted on
	 * a different path than {@code transform}.
	 * @param path the mount path
	 * @return the operations interface to interact with the Vault transform
	 * backend.
	 * @since 2.3
	 */
	VaultTransformOperations opsForTransform(String path);

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
	 * Read ({@code GET)} from a Vault path. Reading data using this method is
	 * suitable for API calls/secret backends that do not require a request body.
	 * @param path must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	@Nullable
	VaultResponse read(String path);

	/**
	 * Read ({@code GET)} from a Vault path. Reading data using this method is
	 * suitable for API calls/secret backends that do not require a request body.
	 * @param path must not be {@literal null}.
	 * @return the data.
	 * @throws SecretNotFoundException if the path does not exist.
	 * @since 4.0
	 */
	default VaultResponse readRequired(String path) throws SecretNotFoundException {

		VaultResponse response = read(path);

		if (response == null) {
			throw new SecretNotFoundException("No data found at '%s'".formatted(path), path);
		}

		return response;
	}

	/**
	 * Read ({@code GET)} from a secret backend. Reading data using this method is
	 * suitable for secret backends that do not require a request body.
	 * @param path must not be {@literal null}.
	 * @param responseType must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	<T extends @Nullable Object> @Nullable VaultResponseSupport<T> read(String path, Class<T> responseType);

	/**
	 * Read ({@code GET)} from a secret backend. Reading data using this method is
	 * suitable for secret backends that do not require a request body.
	 * @param path must not be {@literal null}.
	 * @param responseType must not be {@literal null}.
	 * @return the data.
	 * @throws SecretNotFoundException if the path does not exist.
	 * @since 4.0
	 */
	default <T> VaultResponseSupport<T> readRequired(String path, Class<T> responseType) {
		VaultResponseSupport<T> response = read(path, responseType);
		if (response == null || response.getData() == null) {
			throw new SecretNotFoundException("No data found at '%s'".formatted(path), path);
		}
		return response;
	}

	/**
	 * Enumerate keys from a Vault path.
	 * @param path must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	@Nullable
	List<String> list(String path);

	/**
	 * Write ({@code POST)} to a Vault path.
	 * @param path must not be {@literal null}.
	 * @return the response, may be {@literal null}.
	 * @since 2.0
	 */
	@Nullable
	default VaultResponse write(String path) {
		return write(path, null);
	}

	/**
	 * Write ({@code POST)} to a Vault path.
	 * @param path must not be {@literal null}.
	 * @param body the body, may be {@literal null} if absent.
	 * @return the response, may be {@literal null}.
	 */
	@Nullable
	VaultResponse write(String path, @Nullable Object body);

	/**
	 * Invoke an operation on a Vault path, typically a {@code POST} request along
	 * with an optional request body expecting a response.
	 * @param path must not be {@literal null}.
	 * @param body the body, may be {@literal null} if absent.
	 * @return the response.
	 * @throws IllegalStateException if the operation returns without returning a
	 * response.
	 * @since 4.0
	 */
	default VaultResponse invoke(String path, @Nullable Object body) {
		VaultResponse response = write(path, body);
		if (response == null) {
			throw new IllegalStateException("No response received from Vault, writing to '%s'".formatted(path));
		}
		return response;
	}

	/**
	 * Delete a path.
	 * @param path must not be {@literal null}.
	 */
	void delete(String path);

	/**
	 * Executes a Vault {@link RestOperationsCallback}. Allows to interact with
	 * Vault using {@link org.springframework.web.client.RestOperations} without
	 * requiring a session.
	 * @param clientCallback the request.
	 * @return the {@link RestOperationsCallback} return value.
	 * @throws VaultException when a
	 * {@link org.springframework.web.client.HttpStatusCodeException} occurs.
	 * @throws RestClientException exceptions from
	 * {@link org.springframework.web.client.RestOperations}.
	 */
	<T extends @Nullable Object> T doWithVault(RestOperationsCallback<T> clientCallback)
			throws VaultException, RestClientException;

	/**
	 * Executes a Vault {@link RestOperationsCallback}. Allows to interact with
	 * Vault in an authenticated session. Operations without a session manager or
	 * {@code ClientAuthentication} do not attach a session token and behave like
	 * {@link #doWithVault(RestOperationsCallback)}.
	 * @param sessionCallback the request.
	 * @return the {@link RestOperationsCallback} return value.
	 * @throws VaultException when a
	 * {@link org.springframework.web.client.HttpStatusCodeException} occurs.
	 * @throws RestClientException exceptions from
	 * {@link org.springframework.web.client.RestOperations}.
	 */
	<T extends @Nullable Object> T doWithSession(RestOperationsCallback<T> sessionCallback)
			throws VaultException, RestClientException;

}
