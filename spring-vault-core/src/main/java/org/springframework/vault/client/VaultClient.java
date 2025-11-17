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
package org.springframework.vault.client;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.CheckReturnValue;
import org.springframework.vault.RestClientResponseException;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.client.DefaultVaultClientBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.VaultClientException;
import org.springframework.web.util.UriBuilderFactory;

/**
 * @author Mark Paluch
 */
public interface VaultClient {

	/**
	 * Start building an HTTP GET request.
	 *
	 * @return a spec for specifying the target URL
	 */
	RequestHeadersPathSpec<?> get();

	/**
	 * Start building an HTTP POST request.
	 *
	 * @return a spec for specifying the target URL
	 */
	RequestBodyPathSpec post();

	/**
	 * Start building an HTTP PUT request.
	 *
	 * @return a spec for specifying the target URL
	 */
	RequestBodyPathSpec put();

	/**
	 * Start building an HTTP DELETE request.
	 *
	 * @return a spec for specifying the target URL
	 */
	RequestHeadersPathSpec<?> delete();

	/**
	 * Start building a request for the given {@code HttpMethod}.
	 *
	 * @return a spec for specifying the target URL
	 */
	RequestBodyPathSpec method(HttpMethod method);


	/**
	 * Return a builder to create a new {@code VaultClient} whose settings are
	 * replicated from this {@code VaultClient}.
	 */
	VaultClient.Builder mutate();


	// Static factory methods

	/**
	 * Create a new {@code VaultClient}.
	 *
	 * @see #create(String)
	 * @see #builder()
	 */
	static VaultClient create() {
		return new DefaultVaultClientBuilder().build();
	}

	/**
	 * Variant of {@link #create()} that accepts a default base URL. For more
	 * details see {@link VaultClient.Builder#baseUrl(String) Builder.baseUrl(String)}.
	 *
	 * @param baseUrl the base URI for all requests
	 * @see #builder()
	 */
	static VaultClient create(String baseUrl) {
		return new DefaultVaultClientBuilder().baseUrl(baseUrl).build();
	}

	/**
	 * Variant of {@link #create()} that accepts a default base {@code URI}. For more
	 * details see {@link VaultClient.Builder#baseUrl(URI) Builder.baseUrl(URI)}.
	 *
	 * @param baseUrl the base URI for all requests
	 * @see #builder()
	 * @since 6.2
	 */
	static VaultClient create(VaultEndpoint endpoint) {
		return new DefaultVaultClientBuilder().baseUrl(baseUrl).build();
	}

	/**
	 * Variant of {@link #create()} that accepts a default base {@code URI}. For more
	 * details see {@link VaultClient.Builder#baseUrl(URI) Builder.baseUrl(URI)}.
	 *
	 * @param baseUrl the base URI for all requests
	 * @see #builder()
	 * @since 6.2
	 */
	static VaultClient create(VaultEndpointProvider endpointProvider) {
		return new DefaultVaultClientBuilder().baseUrl(baseUrl).build();
	}

	/**
	 * Obtain a {@code VaultClient} builder.
	 */
	static VaultClient.Builder builder() {
		return new DefaultVaultClientBuilder();
	}

	/**
	 * Obtain a {@code VaultClient} builder based on the configuration of the
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
	 *
	 * @param restTemplate the rest template to base the returned builder's
	 *                     configuration on
	 * @return a {@code VaultClient} builder initialized with {@code restTemplate}'s
	 * configuration
	 */
	static VaultClient.Builder builder(RestTemplate restTemplate) {
		return new DefaultVaultClientBuilder(restTemplate);
	}

	/**
	 * Obtain a {@code VaultClient} builder based on the configuration of the
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
	 *
	 * @param restTemplate the rest template to base the returned builder's
	 *                     configuration on
	 * @return a {@code VaultClient} builder initialized with {@code restTemplate}'s
	 * configuration
	 */
	static VaultClient.Builder builder(RestClient restClient) {
		return new DefaultVaultClientBuilder(restClient);
	}


	/**
	 * A mutable builder for creating a {@link VaultClient}.
	 */
	interface Builder {

		/**
		 * Global option to specify whether absolute paths are allowed.
		 * Disabled by default.
		 *
		 * @param allowAbsolutePath whether to allow absolute paths.
		 * @return this builder.
		 */
		VaultClient.Builder allowAbsolutePath(boolean allowAbsolutePath);


		/**
		 * Global option to specify a namespace header to be added to every request,
		 * if the request does not already contain such a header.
		 *
		 * @param namespace the namespace header value.
		 * @return this builder
		 */
		default VaultClient.Builder defaultNamespace(String namespace) {
			return defaultHeader(VaultHttpHeaders.VAULT_NAMESPACE, namespace);
		}

		/**
		 * Global option to specify a header to be added to every request,
		 * if the request does not already contain such a header.
		 *
		 * @param header the header name
		 * @param values the header values
		 * @return this builder
		 */
		VaultClient.Builder defaultHeader(String header, String... values);

		/**
		 * Provide a consumer to access to every {@linkplain #defaultHeader(String, String...)
		 * default header} declared so far, with the possibility to add, replace, or remove.
		 *
		 * @param headersConsumer the consumer
		 * @return this builder
		 */
		VaultClient.Builder defaultHeaders(Consumer<HttpHeaders> headersConsumer);

		/**
		 * Provide a consumer to customize every request being built.
		 *
		 * @param defaultRequest the consumer to use for modifying requests
		 * @return this builder
		 */
		VaultClient.Builder defaultRequest(Consumer<VaultClient.RequestHeadersSpec<?>> defaultRequest);

		/**
		 * Add the given request interceptor to the end of the interceptor chain.
		 *
		 * @param interceptor the interceptor to be added to the chain
		 * @return this builder
		 */
		VaultClient.Builder requestInterceptor(ClientHttpRequestInterceptor interceptor);

		/**
		 * Configure the {@link ClientHttpRequestFactory} to use. This is useful
		 * for plugging in and/or customizing options of the underlying HTTP
		 * client library (for example, SSL).
		 * <p>If no request factory is specified, {@code VaultClient} uses
		 * {@linkplain org.springframework.http.client.HttpComponentsClientHttpRequestFactory Apache Http Client},
		 * {@linkplain org.springframework.http.client.JettyClientHttpRequestFactory Jetty Http Client}
		 * if available on the classpath, and defaults to the
		 * {@linkplain org.springframework.http.client.JdkClientHttpRequestFactory JDK HttpClient}
		 * if the {@code java.net.http} module is loaded, or to a
		 * {@linkplain org.springframework.http.client.SimpleClientHttpRequestFactory simple default}
		 * otherwise.
		 *
		 * @param requestFactory the request factory to use
		 * @return this builder
		 */
		VaultClient.Builder requestFactory(ClientHttpRequestFactory requestFactory);

		/**
		 * Apply the given {@code Consumer} to this builder instance.
		 * <p>This can be useful for applying pre-packaged customizations.
		 *
		 * @param builderConsumer the consumer to apply
		 * @return this builder
		 */
		VaultClient.Builder apply(Consumer<VaultClient.Builder> builderConsumer);

		/**
		 * Clone this {@code VaultClient.Builder}.
		 */
		VaultClient.Builder clone();

		/**
		 * Build the {@link VaultClient} instance.
		 */
		VaultClient build();
	}


	/**
	 * Contract for specifying the path for a request.
	 *
	 * @param <S> a self reference to the spec type
	 */
	interface PathSpec<S extends VaultClient.RequestHeadersSpec<?>> {

		/**
		 * Specify the URI for the request using a URI template and URI variables.
		 * <p>If a {@link UriBuilderFactory} was configured for the client (for example,
		 * with a base URI) it will be used to expand the URI template.
		 */
		S path(String path, @Nullable Object... pathVariables);

		/**
		 * Specify the URI for the request using a URI template and URI variables.
		 * <p>If a {@link UriBuilderFactory} was configured for the client (for example,
		 * with a base URI) it will be used to expand the URI template.
		 */
		S path(String path, Map<String, ? extends @Nullable Object> pathVariables);

	}


	/**
	 * Contract for specifying request headers leading up to the exchange.
	 *
	 * @param <S> a self reference to the spec type
	 */
	interface RequestHeadersSpec<S extends VaultClient.RequestHeadersSpec<S>> {

		/**
		 * Add the given, single header value under the given name.
		 *
		 * @param headerName   the header name
		 * @param headerValues the header value(s)
		 * @return this builder
		 */
		S header(String headerName, String... headerValues);

		/**
		 * Add or replace the given headers.
		 *
		 * @param httpHeaders the headers to be applied.
		 * @return this builder
		 */
		S headers(HttpHeaders httpHeaders);

		/**
		 * Provides access to every header declared so far with the possibility
		 * to add, replace, or remove values.
		 *
		 * @param headersConsumer the consumer to provide access to
		 * @return this builder
		 */
		S headers(Consumer<HttpHeaders> headersConsumer);

		/**
		 * Enter the retrieve workflow and use the returned {@link VaultClient.ResponseSpec}
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
		 * call one of the returned {@link VaultClient.ResponseSpec}. Use the
		 * {@link #exchange(VaultClient.RequestHeadersSpec.ExchangeFunction)} variants if you need to separate
		 * request execution from response extraction.
		 * <p>By default, 4xx response code result in a
		 * {@link HttpClientErrorException} and 5xx response codes in a
		 * {@link HttpServerErrorException}. To customize error handling, use
		 * {@link VaultClient.ResponseSpec#onStatus(Predicate, VaultClient.ResponseSpec.ErrorHandler) onStatus} handlers.
		 *
		 * @return {@code ResponseSpec} to specify how to decode the body
		 */
		@CheckReturnValue
		VaultClient.ResponseSpec retrieve();

	}


	/**
	 * Contract for specifying request headers and body leading up to the exchange.
	 */
	interface RequestBodySpec extends VaultClient.RequestHeadersSpec<VaultClient.RequestBodySpec> {

		/**
		 * Set the length of the body in bytes, as specified by the
		 * {@code Content-Length} header.
		 *
		 * @param contentLength the content length
		 * @return this builder
		 * @see HttpHeaders#setContentLength(long)
		 */
		VaultClient.RequestBodySpec contentLength(long contentLength);

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
		 *
		 * @param body the body of the request
		 * @return this builder
		 */
		VaultClient.RequestBodySpec body(Object body);

		/**
		 * Set the body of the request to the given {@code Object}.
		 * The parameter {@code bodyType} is used to capture the generic type.
		 *
		 * @param body     the body of the request
		 * @param bodyType the type of the body, used to capture the generic type
		 * @return this builder
		 */
		<T> VaultClient.RequestBodySpec body(T body, ParameterizedTypeReference<T> bodyType);

	}


	/**
	 * Contract for specifying response operations following the exchange.
	 */
	interface ResponseSpec {

		/**
		 * Provide a function to map specific error status codes to an error handler.
		 * <p>By default, if there are no matching status handlers, responses with
		 * status codes &gt;= 400 will throw a {@link RestClientResponseException}.
		 * <p>Note that {@link IOException IOExceptions},
		 * {@link java.io.UncheckedIOException UncheckedIOExceptions}, and
		 * {@link org.springframework.http.converter.HttpMessageNotReadableException HttpMessageNotReadableExceptions}
		 * thrown from {@code errorHandler} will be wrapped in a
		 * {@link VaultClientException}.
		 *
		 * @param statusPredicate to match responses with
		 * @param errorHandler    handler that typically, though not necessarily,
		 *                        throws an exception
		 * @return this builder
		 */
		VaultClient.ResponseSpec onStatus(Predicate<HttpStatusCode> statusPredicate,
				VaultClient.ResponseSpec.ErrorHandler errorHandler);

		/**
		 * Provide a function to map specific error status codes to an error handler.
		 * <p>By default, if there are no matching status handlers, responses with
		 * status codes &gt;= 400 will throw a {@link RestClientResponseException}.
		 * <p>Note that {@link IOException IOExceptions},
		 * {@link java.io.UncheckedIOException UncheckedIOExceptions}, and
		 * {@link org.springframework.http.converter.HttpMessageNotReadableException HttpMessageNotReadableExceptions}
		 * thrown from {@code errorHandler} will be wrapped in a
		 * {@link VaultClientException}.
		 *
		 * @param errorHandler the error handler
		 * @return this builder
		 */
		VaultClient.ResponseSpec onStatus(ResponseErrorHandler errorHandler);

		/**
		 * Extract the body as an object of the given type.
		 *
		 * @param bodyType the type of return value
		 * @param <T>      the body type
		 * @return the body, or {@code null} if no response body was available
		 * @throws RestClientResponseException by default when receiving a
		 *                                     response with a status code of 4xx or 5xx. Use
		 *                                     {@link #onStatus(Predicate, VaultClient.ResponseSpec.ErrorHandler)} to customize error response
		 *                                     handling.
		 */
		VaultResponse requiredBody();

		/**
		 * Extract the body as an object of the given type.
		 *
		 * @param bodyType the type of return value
		 * @param <T>      the body type
		 * @return the body, or {@code null} if no response body was available
		 * @throws RestClientResponseException by default when receiving a
		 *                                     response with a status code of 4xx or 5xx. Use
		 *                                     {@link #onStatus(Predicate, VaultClient.ResponseSpec.ErrorHandler)} to customize error response
		 *                                     handling.
		 */
		@Nullable VaultResponse body();

		/**
		 * Extract the body as an object of the given type.
		 *
		 * @param bodyType the type of return value
		 * @param <T>      the body type
		 * @return the body, or {@code null} if no response body was available
		 * @throws RestClientResponseException by default when receiving a
		 *                                     response with a status code of 4xx or 5xx. Use
		 *                                     {@link #onStatus(Predicate, VaultClient.ResponseSpec.ErrorHandler)} to customize error response
		 *                                     handling.
		 */
		<T> @Nullable T body(Class<T> bodyType);

		/**
		 * Extract the body as an object of the given type.
		 *
		 * @param bodyType the type of return value
		 * @param <T>      the body type
		 * @return the body, or {@code null} if no response body was available
		 * @throws RestClientResponseException by default when receiving a
		 *                                     response with a status code of 4xx or 5xx. Use
		 *                                     {@link #onStatus(Predicate, VaultClient.ResponseSpec.ErrorHandler)} to customize error response
		 *                                     handling.
		 */
		<T> @Nullable T body(ParameterizedTypeReference<T> bodyType);

		/**
		 * Return a {@code ResponseEntity} with the body decoded to VaultResponse.
		 *
		 * @return the {@code ResponseEntity} with the decoded body.
		 * @throws RestClientResponseException by default when receiving a
		 *                                     response with a status code of 4xx or 5xx. Use
		 *                                     {@link #onStatus(Predicate, VaultClient.ResponseSpec.ErrorHandler)} to customize error response
		 *                                     handling.
		 */
		default ResponseEntity<VaultResponse> toEntity() {
			return toEntity(VaultResponse.class);
		}

		/**
		 * Return a {@code ResponseEntity} with the body decoded to an Object of
		 * the given type.
		 *
		 * @param bodyType the expected response body type
		 * @param <T>      response body type
		 * @return the {@code ResponseEntity} with the decoded body
		 * @throws RestClientResponseException by default when receiving a
		 *                                     response with a status code of 4xx or 5xx. Use
		 *                                     {@link #onStatus(Predicate, VaultClient.ResponseSpec.ErrorHandler)} to customize error response
		 *                                     handling.
		 */
		<T> ResponseEntity<T> toEntity(Class<T> bodyType);

		/**
		 * Return a {@code ResponseEntity} with the body decoded to an Object of
		 * the given type.
		 *
		 * @param bodyType the expected response body type
		 * @param <T>      response body type
		 * @return the {@code ResponseEntity} with the decoded body
		 * @throws RestClientResponseException by default when receiving a
		 *                                     response with a status code of 4xx or 5xx. Use
		 *                                     {@link #onStatus(Predicate, VaultClient.ResponseSpec.ErrorHandler)} to customize error response
		 *                                     handling.
		 */
		<T> ResponseEntity<T> toEntity(ParameterizedTypeReference<T> bodyType);

		/**
		 * Return a {@code ResponseEntity} without a body.
		 *
		 * @return the {@code ResponseEntity}
		 * @throws RestClientResponseException by default when receiving a
		 *                                     response with a status code of 4xx or 5xx. Use
		 *                                     {@link #onStatus(Predicate, VaultClient.ResponseSpec.ErrorHandler)} to customize error response
		 *                                     handling.
		 */
		ResponseEntity<Void> toBodilessEntity();

		/**
		 * Used in {@link #onStatus(Predicate, VaultClient.ResponseSpec.ErrorHandler)}.
		 */
		@FunctionalInterface
		interface ErrorHandler {

			/**
			 * Handle the error in the given response.
			 *
			 * @param response the response with the error
			 * @throws IOException in case of I/O errors
			 */
			void handle(HttpRequest request, ClientHttpResponse response) throws IOException;
		}
	}


	/**
	 * Contract for specifying request headers and URI for a request.
	 *
	 * @param <S> a self reference to the spec type
	 */
	interface RequestHeadersPathSpec<S extends VaultClient.RequestHeadersSpec<S>> extends PathSpec<S>, VaultClient.RequestHeadersSpec<S> {
	}


	/**
	 * Contract for specifying request headers, body and URI for a request.
	 */
	interface RequestBodyPathSpec extends VaultClient.RequestBodySpec, RequestHeadersPathSpec<RequestBodySpec> {
	}

}
