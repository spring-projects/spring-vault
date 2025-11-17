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

import java.util.function.Predicate;

import org.springframework.lang.CheckReturnValue;
import org.springframework.vault.client.VaultClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * @author Mark Paluch
 */
public interface VaultLoginClient  extends VaultClient  {


	/**
	 * Start building an HTTP POST request.
	 * @return a spec for specifying the target URL
	 */
	LoginBodyPathSpec login(String path);


	/**
	 * Obtain a {@code RestClient} builder based on the configuration of the
	 * given {@code RestTemplate}.
	 * <p>The returned builder is configured with the following attributes of
	 * the template.
	 * <ul>
	 * <li>{@link RestTemplate#getRequestFactory() ClientHttpRequestFactory}</li>
	 * <li>{@link RestTemplate#getMessageConverters() HttpMessageConverters}</li>
	 * <li>{@link RestTemplate#getInterceptors() ClientHttpRequestInterceptors}</li>
	 * <li>{@link RestTemplate#getClientHttpRequestInitializers() ClientHttpRequestInitializers}</li>
	 * <li>{@link RestTemplate#getUriTemplateHandler() UriBuilderFactory}</li>
	 * <li>{@linkplain RestTemplate#getErrorHandler() error handler}</li>
	 * </ul>
	 * @param restTemplate the rest template to base the returned builder's
	 * configuration on
	 * @return a {@code RestClient} builder initialized with {@code restTemplate}'s
	 * configuration
	 */
	static Builder builder(VaultClient vaultClient) {
		return new DefaultRestClientBuilder(restTemplate);
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
	 * Contract for specifying request headers leading up to the exchange.
	 *
	 * @param <S> a self reference to the spec type
	 */
	interface RequestHeadersSpec<S extends RequestHeadersSpec<S>> extends VaultClient.RequestHeadersSpec<S> {


		/**
		 * Enter the retrieve workflow and use the returned {@link ResponseSpec}
		 * to select from a number of built-in options to extract the response.
		 * For example:
		 *
		 * <pre class="code">
		 * ResponseEntity&lt;Person&gt; entity = client.get()
		 *     .uri("/persons/1")
		 *     .accept(MediaType.APPLICATION_JSON)
		 *     .retrieve()
		 *     .toEntity(Person.class);
		 * </pre>
		 * <p>Or if interested only in the body:
		 * <pre class="code">
		 * Person person = client.get()
		 *     .uri("/persons/1")
		 *     .accept(MediaType.APPLICATION_JSON)
		 *     .retrieve()
		 *     .body(Person.class);
		 * </pre>
		 * Note that this method does not actually execute the request until you
		 * call one of the returned {@link ResponseSpec}. Use the
		 * {@link #exchange(ExchangeFunction)} variants if you need to separate
		 * request execution from response extraction.
		 * <p>By default, 4xx response code result in a
		 * {@link HttpClientErrorException} and 5xx response codes in a
		 * {@link HttpServerErrorException}. To customize error handling, use
		 * {@link ResponseSpec#onStatus(Predicate, ResponseSpec.ErrorHandler) onStatus} handlers.
		 * @return {@code ResponseSpec} to specify how to decode the body
		 */
		@CheckReturnValue
		LoginResponseSpec retrieve();

	}


	/**
	 * Contract for specifying login body leading up to the exchange.
	 */
	interface LoginBodySpec extends RequestHeadersSpec<LoginBodySpec> {

		/**
		 * Set the body of the request to the given {@code Object}.
		 * For example:
		 * <pre class="code">
		 * Person person = ... ;
		 * ResponseEntity&lt;Void&gt; response = client.post()
		 *     .uri("/persons/{id}", id)
		 *     .contentType(MediaType.APPLICATION_JSON)
		 *     .body(person)
		 *     .retrieve()
		 *     .toBodilessEntity();
		 * </pre>
		 * @param body the body of the request
		 * @return this builder
		 */
		LoginBodySpec using(Object body);

	}


	/**
	 * Contract for specifying response operations following the exchange.
	 */
	interface LoginResponseSpec extends ResponseSpec {

		/**
		 * Extract the body as an object of the given type.
		 * @param bodyType the type of return value
		 * @param <T> the body type
		 * @return the body, or {@code null} if no response body was available
		 * @throws RestClientResponseException by default when receiving a
		 * response with a status code of 4xx or 5xx. Use
		 * {@link #onStatus(Predicate, ErrorHandler)} to customize error response
		 * handling.
		 */
		<T> LoginToken loginToken();

	}



	/**
	 * Contract for specifying request headers, body and URI for a request.
	 */
	interface LoginBodyPathSpec extends LoginBodySpec, RequestHeadersSpec<LoginBodySpec> {
	}

}
