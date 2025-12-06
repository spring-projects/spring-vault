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

import org.jspecify.annotations.Nullable;

import org.springframework.lang.CheckReturnValue;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultEndpointProvider;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilderFactory;

/**
 * Extension to {@link VaultClient} to perform Vault HTTP login requests,
 * exposing a fluent, synchronous API.
 *
 * @author Mark Paluch
 * @since 4.1
 */
interface VaultLoginClient extends VaultClient {

	/**
	 * Start building an HTTP POST request and accept the authentication mount. The
	 * request path is derived from the authentication mount using the formatting
	 * pattern {@code auth/%s/login}.
	 * @param authMount name of the authentication mount.
	 * @return a spec for specifying the target path.
	 */
	LoginBodyRequestSpec loginAt(String authMount);

	/**
	 * Start building an HTTP POST request.
	 * @return a spec for specifying the target path.
	 */
	LoginBodyPathSpec login();


	/**
	 * Obtain a {@code VaultLoginClient} builder based on the configuration of the
	 * given {@code VaultClient}.
	 * @return a {@code VaultLoginClient} builder initialized with
	 * {@code vaultClient}'s configuration.
	 */
	static Builder builder(VaultClient vaultClient) {
		return new DefaultVaultLoginClientBuilder(vaultClient);
	}

	/**
	 * Obtain a {@code VaultLoginClient} based on the configuration of the given
	 * {@code VaultClient} using {@code authenticationMechanism}.
	 * @return a {@code VaultLoginClient} using {@code vaultClient}'s configuration.
	 */
	static VaultLoginClient create(VaultClient vaultClient, String authenticationMechanism) {
		return builder(vaultClient).mechanism(authenticationMechanism).build();
	}


	/**
	 * A mutable builder for creating a {@link RestClient}.
	 */
	interface Builder {

		Builder mechanism(String authenticationMechanism);

		/**
		 * Build the {@link VaultLoginClient} instance.
		 */
		VaultLoginClient build();

	}


	/**
	 * Contract for specifying the path for a request.
	 *
	 * @param <S> a self reference to the spec type.
	 */
	interface PathSpec<S extends LoginBodySpec<S>> {

		/**
		 * Specify the path for the request using a URI template and URI variables.
		 * <p>If a {@link VaultEndpointProvider} or {@link UriBuilderFactory} was
		 * configured for the client (for example, with a base URI) it will be used to
		 * expand the URI template.
		 */
		S path(String path, @Nullable Object... pathVariables);

		/**
		 * Specify the path for the request using a URI template and URI variables.
		 * <p>If a {@link VaultEndpointProvider} or {@link UriBuilderFactory} was
		 * configured for the client (for example, with a base URI) it will be used to
		 * expand the URI template.
		 */
		S path(String path, Map<String, ? extends @Nullable Object> pathVariables);

	}


	/**
	 * Contract for specifying request leading up to the exchange.
	 */
	interface RequestSpec {

		/**
		 * Enter the retrieve workflow and use the returned {@link LoginResponseSpec} to
		 * select from a number of built-in options to extract the response. For
		 * example:
		 *
		 * <pre class="code">
		 * LoginToken token = client.loginAt("cert")
		 *     .retrieve()
		 *     .loginToken();
		 * </pre> Note that this method does not actually execute the request until you
		 * call one of the returned {@link LoginResponseSpec}.
		 * <p>4xx and 5xx response codes result in a {@link VaultLoginException}, 4xx
		 * response code result in a {@link HttpClientErrorException} cause and 5xx
		 * response codes in a {@link HttpServerErrorException} cause.
		 */
		@CheckReturnValue
		LoginResponseSpec retrieve();

	}


	/**
	 * Contract for specifying login body leading up to the exchange.
	 */
	interface LoginBodySpec<S extends LoginBodySpec<S>> extends RequestSpec {

		/**
		 * Set the body of the request to the given {@code Object}. For example:
		 * <pre class="code">
		 * Map&lt;String, Object&gt; body = ... ;
		 * LoginToken loginToken = client.login()
		 *     .path("auth/userpass/login/{userid}", userid)
		 *     .using(person)
		 *     .retrieve()
		 *     .loginToken();
		 * </pre>
		 * @param body the body of the request.
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
		 * @return the body.
		 * @throws VaultLoginException when receiving a response with a status code of
		 * 4xx or 5xx.
		 */
		LoginToken loginToken();

		/**
		 * Extract the body as {@link VaultResponseSupport}.
		 * @return the {@code VaultResponseSupport} with the decoded body.
		 * @throws VaultLoginException by default when receiving a response with a
		 * status code of 4xx or 5xx.
		 */
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
