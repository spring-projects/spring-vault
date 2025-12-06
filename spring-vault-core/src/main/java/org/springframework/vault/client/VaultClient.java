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
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverters.ClientBuilder;
import org.springframework.lang.CheckReturnValue;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.WrappedMetadata;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.ResponseSpec.ErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriBuilderFactory;

/**
 * Client to perform Vault HTTP requests, exposing a fluent, synchronous API over
 * underlying {@link RestClient}.
 *
 * <p>
 * Use static factory methods {@link #create()}, {@link #create(String)}, or
 * {@link VaultClient#builder()} to prepare an instance. To use the same configuration as
 * a {@link RestClient}, use {@link #builder(RestClient)} or
 * {@link #builder(RestTemplate)} respectively.
 * <p>
 * {@code VaultClient} is intended to be used with relative paths requiring a
 * {@link VaultEndpoint} to be {@link Builder#endpoint(VaultEndpoint) configured}. Without
 * an endpoint, callers must provide the absolute URL for each request.
 *
 * <p>
 * For examples with a response body see:
 * <ul>
 * <li>{@link RequestHeadersSpec#retrieve() retrieve()}
 * </ul>
 *
 * <p>
 * For examples with a request body see:
 * <ul>
 * <li>{@link RequestBodySpec#body(Object) body(Object)}
 * <li>{@link RequestBodySpec#body(Object, ParameterizedTypeReference) body(Object,
 * ParameterizedTypeReference)}
 * </ul>
 *
 * @author Mark Paluch
 * @since 4.1
 */
public interface VaultClient {

	/**
	 * Start building an HTTP GET request.
	 * @return a spec for specifying the target path.
	 */
	RequestHeadersPathSpec<?> get();

	/**
	 * Start building an HTTP POST request.
	 * @return a spec for specifying the target path.
	 */
	RequestHeadersBodyPathSpec post();

	/**
	 * Start building an HTTP PUT request.
	 * @return a spec for specifying the target path.
	 */
	RequestHeadersBodyPathSpec put();

	/**
	 * Start building an HTTP DELETE request.
	 * @return a spec for specifying the target path.
	 */
	RequestHeadersPathSpec<?> delete();

	/**
	 * Start building a request for the given {@code HttpMethod}.
	 * @return a spec for specifying the target path.
	 */
	RequestHeadersBodyPathSpec method(HttpMethod method);


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
	 * details see {@link VaultClient.Builder#endpoint(VaultEndpoint)}.
	 * @param baseUrl the base URI for all requests.
	 * @see #builder()
	 */
	static VaultClient create(String baseUrl) {
		return new DefaultVaultClientBuilder().endpoint(VaultEndpoint.from(baseUrl)).build();
	}

	/**
	 * Variant of {@link #create()} that accepts a default {@code VaultEndpoint}.
	 * For more details see {@link VaultClient.Builder#endpoint(VaultEndpoint)}.
	 * @param endpoint the Vault Endpoint for all relative path requests.
	 * @see #builder()
	 */
	static VaultClient create(VaultEndpoint endpoint) {
		return new DefaultVaultClientBuilder().endpoint(endpoint).build();
	}

	/**
	 * Variant of {@link #create()} that accepts a default
	 * {@code VaultEndpointProvider}. For more details see
	 * {@link VaultClient.Builder#endpoint(VaultEndpointProvider)}.
	 * @param endpointProvider the endpoint provider for all relative path requests.
	 * @see #builder()
	 */
	static VaultClient create(VaultEndpointProvider endpointProvider) {
		return new DefaultVaultClientBuilder().endpoint(endpointProvider).build();
	}

	/**
	 * Obtain a {@code VaultClient} builder.
	 */
	static VaultClient.Builder builder() {
		return new DefaultVaultClientBuilder();
	}

	/**
	 * Obtain a {@code VaultClient} builder based on the configuration of the given
	 * {@code RestTemplate}.
	 * <p>The {@link RestTemplate} must be configured with appropriate
	 * {@link VaultClients#configureConverters(ClientBuilder) HttpMessageConverters}
	 * to support String, byte[], and JSON conversion. Additionally, if the template
	 * is configured to use a base URL, the built {@code VaultClient} can be used
	 * with relative paths. The returned builder is configured with the following
	 * attributes of the template:
	 * <ul>
	 * <li>{@link RestTemplate#getRequestFactory() ClientHttpRequestFactory}</li>
	 * <li>{@link RestTemplate#getMessageConverters() HttpMessageConverters}</li>
	 * <li>{@link RestTemplate#getInterceptors() ClientHttpRequestInterceptors}</li>
	 * <li>{@link RestTemplate#getClientHttpRequestInitializers()
	 * ClientHttpRequestInitializers}</li>
	 * <li>{@link RestTemplate#getUriTemplateHandler() UriBuilderFactory}</li>
	 * <li>{@linkplain RestTemplate#getErrorHandler() error handler}</li>
	 * </ul>
	 * @param restTemplate the rest template to base the returned builder's
	 * configuration on.
	 * @return a {@code VaultClient} builder initialized with {@code restTemplate}'s
	 * configuration.
	 * @see RestClient#builder(RestTemplate)
	 */
	static VaultClient.Builder builder(RestTemplate restTemplate) {
		return new DefaultVaultClientBuilder(restTemplate);
	}

	/**
	 * Obtain a {@code VaultClient} builder based on the configuration of the given
	 * {@link RestClient}.
	 * <p>The {@link RestClient} must be configured with appropriate
	 * {@link VaultClients#configureConverters(ClientBuilder) HttpMessageConverters}
	 * to support String, byte[], and JSON conversion. Additionally, if the client
	 * is configured to use a base URL, the built {@code VaultClient} can be used
	 * with relative paths.
	 * @param restClient the {@link RestClient} to base the returned builder's
	 * configuration on.
	 * @return a {@code VaultClient} builder initialized with {@code restClient}'s
	 * configuration.
	 */
	static VaultClient.Builder builder(RestClient restClient) {
		return new DefaultVaultClientBuilder(restClient);
	}


	/**
	 * A mutable builder for creating a {@link VaultClient}.
	 */
	interface Builder {

		/**
		 * Set the Vault endpoint to use.
		 * @param endpoint the vault endpoint to use.
		 * @return this builder.
		 */
		VaultClient.Builder endpoint(VaultEndpoint endpoint);

		/**
		 * Set the Vault endpoint provider to use.
		 * @param endpointProvider the vault endpoint provider to use.
		 * @return this builder.
		 */
		VaultClient.Builder endpoint(VaultEndpointProvider endpointProvider);

		/**
		 * Provide a pre-configured {@link UriBuilderFactory} instance. This is an
		 * alternative to, and effectively overrides the following shortcut properties:
		 * <ul>
		 * <li>{@link #endpoint(VaultEndpoint)}}
		 * <li>{@link #endpoint(VaultEndpointProvider)}}.</ul>
		 * @param uriBuilderFactory the URI builder factory to use.
		 * @return this builder.
		 * @see #endpoint(VaultEndpoint)
		 * @see #endpoint(VaultEndpointProvider)
		 * @see VaultClients#createUriBuilderFactory(VaultEndpointProvider)
		 */
		Builder uriBuilderFactory(UriBuilderFactory uriBuilderFactory);

		/**
		 * Global option to specify a namespace header to be added to every request, if
		 * the request does not already contain such a header.
		 * @param namespace the namespace header value.
		 * @return this builder.
		 */
		default VaultClient.Builder defaultNamespace(String namespace) {
			return defaultHeader(VaultHttpHeaders.VAULT_NAMESPACE, namespace);
		}

		/**
		 * Global option to specify a header to be added to every request, if the
		 * request does not already contain such a header.
		 * @param header the header name.
		 * @param values the header values.
		 * @return this builder.
		 */
		VaultClient.Builder defaultHeader(String header, String... values);

		/**
		 * Configure the {@link ClientHttpRequestFactory} to use. This is useful for
		 * plugging in and/or customizing options of the underlying HTTP client library
		 * (for example, SSL).
		 * <p>If no request factory is specified, {@code VaultClient} uses
		 * {@linkplain org.springframework.http.client.HttpComponentsClientHttpRequestFactory
		 * Apache Http Client},
		 * {@linkplain org.springframework.http.client.JettyClientHttpRequestFactory
		 * Jetty Http Client} if available on the classpath, and defaults to the
		 * {@linkplain org.springframework.http.client.JdkClientHttpRequestFactory JDK
		 * HttpClient} if the {@code java.net.http} module is loaded, or to a
		 * {@linkplain org.springframework.http.client.SimpleClientHttpRequestFactory
		 * simple default} otherwise.
		 * @param requestFactory the request factory to use.
		 * @return this builder.
		 */
		Builder requestFactory(ClientHttpRequestFactory requestFactory);

		/**
		 * Provide a consumer to access {@link RestClient.Builder} with the possibility
		 * to override or augment its configuration.
		 * @param restClientBuilderConsumer the consumer.
		 * @return this builder.
		 */
		VaultClient.Builder configureRestClient(Consumer<RestClient.Builder> restClientBuilderConsumer);

		/**
		 * Apply the given {@code Consumer} to this builder instance.
		 * <p>This can be useful for applying pre-packaged customizations.
		 * @param builderConsumer the consumer to apply
		 * @return this builder
		 */
		VaultClient.Builder apply(Consumer<VaultClient.Builder> builderConsumer);

		/**
		 * Clone this {@code VaultClient.Builder}.
		 */
		Builder clone();

		/**
		 * Build the {@code VaultClient} instance.
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
		 * Specify the path for the request using a URI template and URI variables.
		 * <p>If a {@link VaultEndpointProvider} or {@link UriBuilderFactory} was
		 * configured for the client (for example, with a base URI) this method will
		 * these to expand the URI template and prevent usage of absolute URIs to avoid
		 * unwanted access to servers other than the {@link VaultEndpoint}.
		 */
		S path(String path, @Nullable Object... pathVariables);

		/**
		 * Specify the path for the request using a URI template and URI variables.
		 * <p>If a {@link VaultEndpointProvider} or {@link UriBuilderFactory} was
		 * configured for the client (for example, with a base URI) this method will
		 * these to expand the URI template and prevent usage of absolute URIs to avoid
		 * unwanted access to servers other than the {@link VaultEndpoint}.
		 */
		S path(String path, Map<String, ? extends @Nullable Object> pathVariables);

		/**
		 * Specify the URI using a fully constructed {@link URI}.
		 * <p>
		 * If the given URI is absolute, it is used as given. If it is a relative
		 * URI, the {@link UriBuilderFactory} configured for the client (for example,
		 * with a base URI) will be used to {@linkplain URI#resolve(URI) resolve} the
		 * given URI against.
		 */
		S uri(URI uri);

	}


	/**
	 * Contract for specifying request headers leading up to the exchange.
	 *
	 * @param <S> a self reference to the spec type
	 */
	interface RequestHeadersSpec<S extends VaultClient.RequestHeadersSpec<S>> {

		/**
		 * Set the namespace for this request.
		 * @param namespace the namespace value.
		 * @return this builder.
		 */
		default S namespace(String namespace) {
			return header(VaultHttpHeaders.VAULT_NAMESPACE, namespace);
		}

		/**
		 * Set the authentication token for this request.
		 * @param token the Vault token.
		 * @return this builder.
		 */
		default S token(VaultToken token) {
			return header(VaultHttpHeaders.VAULT_TOKEN, token.getToken());
		}

		/**
		 * Add the given, single header value under the given name.
		 * @param headerName the header name.
		 * @param headerValues the header value(s).
		 * @return this builder
		 */
		S header(String headerName, String... headerValues);

		/**
		 * Add or replace the given headers.
		 * @param httpHeaders the headers to be applied.
		 * @return this builder.
		 */
		S headers(HttpHeaders httpHeaders);

		/**
		 * Provides access to every header declared so far with the possibility to add,
		 * replace, or remove values.
		 * @param headersConsumer the consumer to provide access to.
		 * @return this builder.
		 */
		S headers(Consumer<HttpHeaders> headersConsumer);

		/**
		 * Enter the retrieve workflow and use the returned
		 * {@link VaultClient.ResponseSpec} to select from a number of built-in options
		 * to extract the response. For example:
		 *
		 * <pre class="code">
		 * ResponseEntity&lt;Person&gt; entity = client.get()
		 *     .path("/persons/1")
		 *     .retrieve()
		 *     .toEntity(Person.class);
		 * </pre>
		 * <p>*Or if interested only in the body: <pre class="code">
		 * Person person = client.get()
		 *     .path("/persons/1")
		 *     .retrieve()
		 *     .body(Person.class);
		 * </pre> Note that this method does not actually execute the request until you
		 * call one of the returned {@link VaultClient.ResponseSpec}.
		 * <p>* By default, 4xx response code result in a {@link HttpClientErrorException} and
		 * 5xx response codes in a {@link HttpServerErrorException}. To customize error
		 * handling, use {@link ResponseSpec#onStatus(Predicate, ErrorHandler)} onStatus}
		 * handlers.
		 * @return {@code ResponseSpec} to specify how to decode the body.
		 */
		@CheckReturnValue
		VaultClient.ResponseSpec retrieve();

	}


	/**
	 * Contract for specifying request headers and body leading up to the exchange.
	 */
	interface RequestBodySpec extends VaultClient.RequestHeadersSpec<VaultClient.RequestBodySpec> {

		/**
		 * Set the body of the request to the given {@code Object}. For example:
		 * <pre class="code">
		 * Person person = ... ;
		 * ResponseEntity&lt;Void&gt; response = client.post()
		 *     .path("/persons/{id}", id)
		 *     .body(person)
		 *     .retrieve()
		 *     .toBodilessEntity();
		 * </pre>
		 * @param body the body of the request.
		 * @return this builder.
		 */
		VaultClient.RequestBodySpec body(Object body);

		/**
		 * Set the body of the request to the given {@code Object}. The parameter
		 * {@code bodyType} is used to capture the generic type.
		 * @param body the body of the request.
		 * @param bodyType the type of the body, used to capture the generic type.
		 * @return this builder.
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
		 * status codes &gt;= 400 will throw a {@link VaultClientResponseException}.
		 * <p>Note that {@link IOException IOExceptions},
		 * {@link java.io.UncheckedIOException UncheckedIOExceptions}, and
		 * {@link org.springframework.http.converter.HttpMessageNotReadableException
		 * HttpMessageNotReadableExceptions} thrown from {@code errorHandler} will be
		 * wrapped in a {@link VaultClientResponseException}.
		 * @param statusPredicate to match responses with
		 * @param errorHandler handler that typically, though not necessarily, throws an
		 * exception
		 * @return this builder
		 */
		ResponseSpec onStatus(Predicate<HttpStatusCode> statusPredicate,
				RestClient.ResponseSpec.ErrorHandler errorHandler);

		/**
		 * Wrap the response in a cubbyhole token with the requested TTL.
		 * @param ttl the time to live for the wrapped response.
		 * @return the cubbyhole {@link WrappedMetadata} providing a token and metadata
		 * for the wrapped response.
		 * @throws VaultClientResponseException when receiving a response with a status
		 * code of 4xx or 5xx.
		 * @throws IllegalStateException if no response body was available.
		 */
		WrappedMetadata wrap(Duration ttl);

		/**
		 * Extract the required body as an object of the given type.
		 * @return the body or {@link IllegalStateException} if no response body was
		 * available.
		 * @throws VaultClientResponseException when receiving a response with a status
		 * code of 4xx or 5xx.
		 * @throws IllegalStateException if no response body was available.
		 */
		VaultResponse requiredBody();

		/**
		 * Extract the body as an object of the given type.
		 * @return the body, or {@code null} if no response body was available.
		 * @throws VaultClientResponseException when receiving a response with a status
		 * code of 4xx or 5xx.
		 */
		@Nullable
		VaultResponse body();

		/**
		 * Extract the required body as an object of the given type.
		 * @return the body or {@link IllegalStateException} if no response body was
		 * available.
		 * @throws VaultClientResponseException when receiving a response with a status
		 * code of 4xx or 5xx.
		 * @throws IllegalStateException if no response body was available.
		 */
		<T> T requiredBody(Class<T> bodyType);

		/**
		 * Extract the body as an object of the given type.
		 * @param bodyType the type of return value.
		 * @param <T> the body type.
		 * @return the body, or {@code null} if no response body was available
		 * @throws VaultClientResponseException when receiving a response with a status
		 * code of 4xx or 5xx.
		 */
		<T> @Nullable T body(Class<T> bodyType);

		/**
		 * Extract the body as an object of the given type.
		 * @param bodyType the type of return value.
		 * @param <T> the body type.
		 * @return the body, or {@code null} if no response body was available.
		 * @throws VaultClientResponseException when receiving a response with a status
		 * code of 4xx or 5xx.
		 */
		<T> @Nullable T body(ParameterizedTypeReference<T> bodyType);

		/**
		 * Return a {@code ResponseEntity} with the body decoded to VaultResponse.
		 * @return the {@code ResponseEntity} with the decoded body.
		 * @throws VaultClientResponseException when receiving a response with a status
		 * code of 4xx or 5xx.
		 */
		default ResponseEntity<VaultResponse> toEntity() {
			return toEntity(VaultResponse.class);
		}

		/**
		 * Return a {@code ResponseEntity} with the body decoded to an Object of the
		 * given type.
		 * @param bodyType the expected response body type.
		 * @param <T> response body type.
		 * @return the {@code ResponseEntity} with the decoded body
		 * @throws VaultClientResponseException when receiving a response with a status
		 * code of 4xx or 5xx.
		 */
		<T> ResponseEntity<T> toEntity(Class<T> bodyType);

		/**
		 * Return a {@code ResponseEntity} with the body decoded to an Object of the
		 * given type.
		 * @param bodyType the expected response body type.
		 * @param <T> response body type.
		 * @return the {@code ResponseEntity} with the decoded body.
		 * @throws VaultClientResponseException when receiving a response with a status
		 * code of 4xx or 5xx.
		 */
		<T> ResponseEntity<T> toEntity(ParameterizedTypeReference<T> bodyType);

		/**
		 * Return a {@code ResponseEntity} without a body.
		 * @return the {@code ResponseEntity}.
		 * @throws VaultClientResponseException when receiving a response with a status
		 * code of 4xx or 5xx.
		 */
		ResponseEntity<Void> toBodilessEntity();

	}


	/**
	 * Contract for specifying request headers and path for a request.
	 *
	 * @param <S> a self reference to the spec type.
	 */
	interface RequestHeadersPathSpec<S extends VaultClient.RequestHeadersSpec<S>>
			extends PathSpec<S>, VaultClient.RequestHeadersSpec<S> {

	}


	/**
	 * Contract for specifying request headers, body and path for a request.
	 */
	interface RequestHeadersBodyPathSpec extends VaultClient.RequestBodySpec, RequestHeadersPathSpec<RequestBodySpec> {

	}

}
