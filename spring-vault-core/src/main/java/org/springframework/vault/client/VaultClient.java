/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.vault.client;

import java.net.URI;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.util.UriTemplateHandler;

/**
 * Interface defining the low-level Vault client. This client uses the Vault HTTP API to
 * exchange requests and responses synchronously.
 * <p>
 * Implementing classes do not maintain any session or token state. See
 * {@link org.springframework.vault.core.VaultTemplate} and
 * {@link org.springframework.vault.authentication.SessionManager} for authenticated and
 * stateful Vault access.
 * 
 * <p>
 * For example:
 * 
 * <pre class="code">
 * DefaultVaultClient client = DefaultVaultClient.create(new RestTemplate());
 * VaultResponseEntity&lt;Void&gt; request = client.get().uri(&quot;http://example.com/resource&quot;)
 * 		.exchange();
 * </pre>
 *
 * @author Mark Paluch
 * @see VaultResponseEntity
 * @see VaultClient
 * @see VaultRequest
 * @see VaultRequestBody
 */
public interface VaultClient {

	/**
	 * Prepare an HTTP GET request.
	 * @return a spec for specifying the target URL
	 */
	UriSpec get();

	/**
	 * Prepare an HTTP HEAD request.
	 * @return a spec for specifying the target URL
	 */
	UriSpec head();

	/**
	 * Prepare an HTTP POST request.
	 * @return a spec for specifying the target URL
	 */
	UriSpec post();

	/**
	 * Prepare an HTTP PUT request.
	 * @return a spec for specifying the target URL
	 */
	UriSpec put();

	/**
	 * Prepare an HTTP DELETE request.
	 * @return a spec for specifying the target URL
	 */
	UriSpec delete();

	/**
	 * Prepare an HTTP request.
	 * @return a spec for specifying the target URL
	 */
	UriSpec method(HttpMethod method);

	/**
	 * Contract for specifying the URI for a request.
	 */
	interface UriSpec {

		/**
		 * Specify the URI using an absolute, fully constructed {@link URI}.
		 */
		HeaderSpec uri(URI uri);

		/**
		 * Specify the URI for the {@link UriTemplateHandler} using a URI template and URI
		 * variables. If a {@link UriTemplateHandler} was configured for the client (e.g.
		 * with a base URI) it will be used to expand the URI template.
		 * 
		 * @param uriTemplate the URI template to expand, must not be {@literal null}.
		 * @param uriVariables the URI template variables. Must not be {@literal null},
		 * may be empty.
		 */
		HeaderSpec uri(String uriTemplate, Object... uriVariables);

		/**
		 * Specify the URI for the {@link UriTemplateHandler} using a URI template and URI
		 * variables. If a {@link UriTemplateHandler} was configured for the client (e.g.
		 * with a base URI) it will be used to expand the URI template.
		 * 
		 * @param uriTemplate the URI template to expand, must not be {@literal null}.
		 * @param uriVariables the URI template variables. Must not be {@literal null},
		 * may be empty.
		 */
		HeaderSpec uri(String uriTemplate, Map<String, ?> uriVariables);
	}

	/**
	 * Contract for specifying request headers leading up to the exchange.
	 */
	interface HeaderSpec extends ReturnTypeSpec {

		/**
		 * Add the given, single header value under the given name.
		 * @param headerName the header name.
		 * @param headerValues the header value(s).
		 * @return {@code this} builder.
		 */
		HeaderSpec header(String headerName, String... headerValues);

		/**
		 * Copy the given headers into the entity's headers map.
		 * @param headers the existing headers to copy from.
		 * @return {@code this} builder.
		 */
		HeaderSpec headers(HttpHeaders headers);

		/**
		 * Copy the given headers into the entity's headers map.
		 *
		 * @param vaultToken the {@link VaultToken}.
		 * @return {@code this} builder.
		 */
		HeaderSpec token(VaultToken vaultToken);

		/**
		 * Add the given {@link VaultRequestBody}.
		 */
		ReturnTypeSpec body(VaultRequestBody<?> body);

		/**
		 * Exchange the given request for a synchronous response. Invoking this method
		 * performs the actual HTTP request/response exchange with a
		 * {@link VaultRequestBody request body} without expecting a response body.
		 * 
		 * @return {@link VaultResponseEntity}.
		 */
		VaultResponseEntity<Void> exchange(VaultRequestBody<?> body);
	}

	/**
	 * Contract for specifying the return type leading up to the exchange.
	 */
	interface ReturnTypeSpec {

		/**
		 * Exchange the given request for a synchronous response. Invoking this method
		 * performs the actual HTTP request/response exchange without expecting a response
		 * body.
		 * 
		 * @return {@link VaultResponseEntity}.
		 */
		VaultResponseEntity<Void> exchange();

		/**
		 * Exchange the given request for a synchronous response. Invoking this method
		 * performs the actual HTTP request/response exchange expecting a response typed
		 * to {@code returnType}.
		 * 
		 * @param returnType the expected return type.
		 * @return {@link VaultResponseEntity}.
		 */
		<T, S extends T> VaultResponseEntity<S> exchange(Class<T> returnType);

		/**
		 * Exchange the given request for a synchronous response. Invoking this method
		 * performs the actual HTTP request/response exchange expecting a response typed
		 * to {@code returnType}.
		 * 
		 * @param returnType the expected return type.
		 * @return {@link VaultResponseEntity}.
		 */
		<T, S extends T> VaultResponseEntity<S> exchange(
				ParameterizedTypeReference<T> returnType);
	}
}
