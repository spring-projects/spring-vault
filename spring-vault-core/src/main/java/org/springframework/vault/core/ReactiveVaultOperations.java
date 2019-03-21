/*
 * Copyright 2017-2019 the original author or authors.
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

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.lang.Nullable;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

/**
 * Interface that specifies a basic set of Vault operations executed on a reactive
 * infrastructure, implemented by {@link ReactiveVaultTemplate}. This is the main entry
 * point to interact with Vault in an authenticated and unauthenticated context.
 * <p>
 * {@link ReactiveVaultOperations} allows execution of callback methods. Callbacks can
 * execute requests within a {@link ReactiveVaultOperations#doWithSession(Function)
 * session context} and the {@link ReactiveVaultOperations#doWithVault(Function) without a
 * session}.
 *
 * @author Mark Paluch
 * @since 2.0
 * @see ReactiveVaultOperations#doWithSession(Function)
 * @see ReactiveVaultOperations#doWithVault(Function)
 * @see WebClient
 * @see VaultTemplate
 * @see VaultTokenOperations
 * @see org.springframework.vault.authentication.VaultTokenSupplier
 */
public interface ReactiveVaultOperations {

	/**
	 * Read from a Vault path. Reading data using this method is suitable for API
	 * calls/secret backends that do not require a request body.
	 *
	 * @param path must not be {@literal null}.
	 * @return the data. May be empty if the path does not exist.
	 */
	Mono<VaultResponse> read(String path);

	/**
	 * Read from a Vault path. Reading data using this method is suitable for API
	 * calls/secret backends that do not require a request body.
	 *
	 * @param path must not be {@literal null}.
	 * @param responseType must not be {@literal null}.
	 * @return the data. May be empty if the path does not exist.
	 */
	<T> Mono<VaultResponseSupport<T>> read(String path, Class<T> responseType);

	/**
	 * Enumerate keys from a Vault path.
	 *
	 * @param path must not be {@literal null}.
	 * @return the data. May be empty if the path does not exist.
	 */
	Flux<String> list(String path);

	/**
	 * Write to a Vault path.
	 *
	 * @param path must not be {@literal null}.
	 * @return the response. May be empty if the response has no body.
	 */
	default Mono<VaultResponse> write(String path) {
		return write(path, null);
	}

	/**
	 * Write to a Vault path.
	 *
	 * @param path must not be {@literal null}.
	 * @param body the body, may be {@literal null} if absent.
	 * @return the response. May be empty if the response has no body.
	 */
	Mono<VaultResponse> write(String path, @Nullable Object body);

	/**
	 * Delete a path.
	 *
	 * @param path must not be {@literal null}.
	 */
	Mono<Void> delete(String path);

	/**
	 * Executes a Vault {@link RestOperationsCallback}. Allows to interact with Vault
	 * using {@link org.springframework.web.client.RestOperations} without requiring a
	 * session.
	 *
	 * @param clientCallback the request.
	 * @return the {@link RestOperationsCallback} return value.
	 * @throws VaultException when a
	 * {@link org.springframework.web.client.HttpStatusCodeException} occurs.
	 * @throws WebClientException exceptions from
	 * {@link org.springframework.web.reactive.function.client.WebClient}.
	 */
	<V, T extends Publisher<V>> T doWithVault(
			Function<WebClient, ? extends T> clientCallback) throws VaultException,
			WebClientException;

	/**
	 * Executes a Vault {@link RestOperationsCallback}. Allows to interact with Vault in
	 * an authenticated session.
	 *
	 * @param sessionCallback the request.
	 * @return the {@link RestOperationsCallback} return value.
	 * @throws VaultException when a
	 * {@link org.springframework.web.client.HttpStatusCodeException} occurs.
	 * @throws WebClientException exceptions from
	 * {@link org.springframework.web.reactive.function.client.WebClient}.
	 */
	<V, T extends Publisher<V>> T doWithSession(
			Function<WebClient, ? extends T> sessionCallback) throws VaultException,
			WebClientException;
}
