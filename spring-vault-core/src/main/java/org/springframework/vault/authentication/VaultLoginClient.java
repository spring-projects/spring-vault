/*
 * Copyright 2025 the original author or authors.
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
package org.springframework.vault.authentication;

import java.util.Map;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import org.springframework.lang.CheckReturnValue;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultEndpointProvider;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilderFactory;

/**
 * @author Mark Paluch
 */
public interface VaultLoginClient extends VaultClient {

	/**
	 * Start building an HTTP POST request and accept the authentication mount.
	 * The request path is derived from the authentication mount using the formatting pattern {@code auth/%s/login}.
	 *
	 * @param authMount name of the authentication mount.
	 * @return a spec for specifying the target URL
	 */
	LoginBodyRequestSpec loginAt(String authMount);

	/**
	 * Start building an HTTP POST request.
	 * @return a spec for specifying the target URL
	 */
	LoginBodyPathSpec login();

	/**
	 * Obtain a {@code VaultLoginClient} builder based on the configuration of the
	 * given {@code VaultClient}.
	 * @return a {@code VaultLoginClient} builder initialized with {@code vaultClient}'s
	 * configuration.
	 */
	static Builder builder(VaultClient vaultClient) {
		return new DefaultVaultLoginClientBuilder(vaultClient);
	}

	static VaultLoginClient create(VaultClient vaultClient, String authenticationMechanism) {
		return builder(vaultClient).mechanism(authenticationMechanism).build();
	}

	/**
	 * A mutable builder for creating a {@link RestClient}.
	 */
	interface Builder {

		Builder mechanism(String authenticationMechanism);

		/**
		 * Build the {@link RestClient} instance.
		 */
		VaultLoginClient build();
	}

	/**
	 * Contract for specifying the path for a request.
	 *
	 * @param <S> a self reference to the spec type
	 */
	interface PathSpec<S extends LoginBodySpec<S>> {

		/**
		 * Specify the path for the request using a URI template and URI variables.
		 * <p>If a {@link VaultEndpointProvider} or {@link UriBuilderFactory} was configured for the client (for example,
		 * with a base URI) it will be used to expand the URI template.
		 */
		S path(String path, @Nullable Object... pathVariables);

		/**
		 * Specify the path for the request using a URI template and URI variables.
		 * <p>If a {@link VaultEndpointProvider} or {@link UriBuilderFactory} was configured for the client (for example,
		 * with a base URI) it will be used to expand the URI template.
		 */
		S path(String path, Map<String, ? extends @Nullable Object> pathVariables);

	}

	/**
	 * Contract for specifying request leading up to the exchange.
	 */
	interface RequestSpec {

		/**
		 * Enter the retrieve workflow and use the returned {@link LoginResponseSpec}
		 * to select from a number of built-in options to extract the response.
		 * For example:
		 *
		 * <pre class="code">
		 * LoginToken token = client.loginAt("cert")
		 *     .retrieve()
		 *     .loginToken();
		 * </pre>
		 * Note that this method does not actually execute the request until you
		 * call one of the returned {@link LoginResponseSpec}.
		 * <p>4xx and 5xx response codes result in a {@link VaultLoginException}, 4xx response code result in a
		 * {@link HttpClientErrorException} cause and 5xx response codes in a
		 * {@link HttpServerErrorException} cause.
		 */
		@CheckReturnValue
		LoginResponseSpec retrieve();

	}

	/**
	 * Contract for specifying login body leading up to the exchange.
	 */
	interface LoginBodySpec<S extends LoginBodySpec<S>> extends RequestSpec {

		/**
		 * Set the body of the request to the given {@code Object}.
		 * For example:
		 * <pre class="code">
		 * Person person = ... ;
		 * LoginToken loginToken = client.login()
		 *     .path("/persons/{id}", id)
		 *     .using(person)
		 *     .retrieve()
		 *     .loginToken();
		 * </pre>
		 * @param body the body of the request
		 * @return this builder.
		 */
		S using(Object body);

	}

	/**
	 * Contract for specifying response operations following the exchange.
	 */
	interface LoginResponseSpec {

		/**
		 * Extract the body as {@link LoginToken}.
		 *
		 * @return the body
		 * @throws VaultLoginException when receiving a
		 *                                     response with a status code of 4xx or 5xx.
		 */
		LoginToken loginToken();

		VaultResponseSupport<LoginToken> body();

	}

	/**
	 * Contract for specifying request body for a request.
	 */
	interface LoginBodyRequestSpec extends RequestSpec, LoginBodySpec<LoginBodyRequestSpec> {

	}

	/**
	 * Contract for specifying request path for a request.
	 */
	interface LoginBodyPathSpec extends PathSpec<LoginBodyRequestSpec> {

	}

}
