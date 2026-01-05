/*
 * Copyright 2017-present the original author or authors.
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

import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.vault.VaultException;
import org.springframework.vault.client.ReactiveVaultClient;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import static org.springframework.vault.core.VaultKeyValueOperationsSupport.*;

/**
 * Central entrypoint for performing Vault operations on a reactive runtime.
 *
 * <p>Implemented by
 * {@link org.springframework.vault.core.ReactiveVaultTemplate}, this interface
 * exposes reactive APIs for interacting with Vault backends such as Key/Value,
 * Transit and {@code sys}. It supports callback-style execution for both
 * {@link #doWithSession(Function) authenticated doWithSession} and
 * {@link #doWithVault(Function) unauthenticated doWithVault} access, returning
 * Reactor {@link Mono} and {@link Flux} types for composition in reactive
 * applications.
 *
 * <p>{@link ReactiveVaultClient.PathSpec#path Paths used} with this and other
 * Template API interfaces are typically relative to the {@link VaultEndpoint}
 * of the underlying {@link ReactiveVaultClient}. If the client is configured
 * without an endpoint, fully-qualified URIs can be used.
 * <p><b>Note</b> that operations apply authentication and other headers
 * regardless of using relative or absolute URIs. To prevent unwanted access to
 * external endpoints using authentication headers, applications should sanitize
 * paths to avoid unwanted access to external endpoints.
 *
 * @author Mark Paluch
 * @author James Luke
 * @author Timothy R. Weiand
 * @since 2.0
 * @see #doWithSession(Function)
 * @see #doWithVault(Function)
 * @see org.springframework.vault.client.ReactiveVaultClient
 * @see org.springframework.web.reactive.function.client.WebClient
 * @see org.springframework.vault.core.ReactiveVaultTemplate
 */

public interface ReactiveVaultOperations {

	/**
	 * Return {@link VaultKeyValueOperations}.
	 * @param path the mount path, must not be empty or {@literal null}.
	 * @param apiVersion API version to use, must not be {@literal null}.
	 * @return the operations interface to interact with the Vault Key/Value secrets
	 * engine.
	 * @since 3.1
	 */
	ReactiveVaultKeyValueOperations opsForKeyValue(String path, KeyValueBackend apiVersion);

	/**
	 * Return {@link ReactiveVaultVersionedKeyValueOperations}.
	 * @param path the mount path
	 * @return the operations interface to interact with the versioned Vault
	 * Key/Value (version 2) secrets engine.
	 * @since 3.1
	 */
	ReactiveVaultVersionedKeyValueOperations opsForVersionedKeyValue(String path);

	/**
	 * @return the operations interface to interact with the Vault transit engine.
	 * @since 3.1
	 */
	ReactiveVaultTransitOperations opsForTransit();

	/**
	 * Return {@link ReactiveVaultTransitOperations} if the transit engine is
	 * mounted on a different path than {@code transit}.
	 * @param path the mount path
	 * @return the operations interface to interact with the Vault transit engine.
	 * @since 3.1
	 */
	ReactiveVaultTransitOperations opsForTransit(String path);

	/**
	 * @return the operations interface administrative Vault access.
	 * @since 3.1
	 */
	ReactiveVaultSysOperations opsForSys();


	/**
	 * Read from a Vault path. Reading data using this method is suitable for API
	 * calls/secrets engines that do not require a request body.
	 * @param path must not be {@literal null}.
	 * @return the data. May be empty if the path does not exist.
	 */
	Mono<VaultResponse> read(String path);

	/**
	 * Read from a Vault path. Reading data using this method is suitable for API
	 * calls/secrets engines that do not require a request body.
	 * @param path must not be {@literal null}.
	 * @param responseType must not be {@literal null}.
	 * @return the data. May be empty if the path does not exist.
	 */
	<T> Mono<VaultResponseSupport<T>> read(String path, Class<T> responseType);

	/**
	 * Enumerate keys from a Vault path.
	 * @param path must not be {@literal null}.
	 * @return the data. May be empty if the path does not exist.
	 */
	Flux<String> list(String path);

	/**
	 * Write to a Vault path.
	 * @param path must not be {@literal null}.
	 * @return the response. May be empty if the response has no body.
	 */
	default Mono<VaultResponse> write(String path) {
		return write(path, null);
	}

	/**
	 * Write to a Vault path.
	 * @param path must not be {@literal null}.
	 * @param body the body, may be {@literal null} if absent.
	 * @return the response. May be empty if the response has no body.
	 */
	Mono<VaultResponse> write(String path, @Nullable Object body);

	/**
	 * Delete a path.
	 * @param path must not be {@literal null}.
	 */
	Mono<Void> delete(String path);

	/**
	 * Executes a Vault {@link RestOperationsCallback}. Allows to interact with
	 * Vault using {@link org.springframework.web.client.RestOperations} without
	 * requiring a session.
	 * @param clientCallback the request.
	 * @return the {@link RestOperationsCallback} return value.
	 * @throws VaultException when a
	 * {@link org.springframework.web.client.HttpStatusCodeException} occurs.
	 * @throws WebClientException exceptions from
	 * {@link org.springframework.web.reactive.function.client.WebClient}.
	 */
	<V, T extends Publisher<V>> T doWithVault(Function<WebClient, ? extends T> clientCallback)
			throws VaultException, WebClientException;

	/**
	 * Executes a Vault {@link RestOperationsCallback}. Allows to interact with
	 * Vault in an authenticated session.
	 * @param sessionCallback the request.
	 * @return the {@link RestOperationsCallback} return value.
	 * @throws VaultException when a
	 * {@link org.springframework.web.client.HttpStatusCodeException} occurs.
	 * @throws WebClientException exceptions from
	 * {@link org.springframework.web.reactive.function.client.WebClient}.
	 */
	<V, T extends Publisher<V>> T doWithSession(Function<WebClient, ? extends T> sessionCallback)
			throws VaultException, WebClientException;

}
