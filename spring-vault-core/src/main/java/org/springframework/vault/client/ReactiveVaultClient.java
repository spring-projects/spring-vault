/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.vault.client;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.converter.HttpMessageConverters.ClientBuilder;
import org.springframework.lang.CheckReturnValue;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.WrappedMetadata;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilderFactory;

/**
 * Client to perform Vault HTTP requests, exposing a fluent, reactive API over
 * underlying {@link WebClient}.
 * <p>Use static factory methods {@link #create()}, {@link #create(String)}, or
 * {@link ReactiveVaultClient#builder()} to prepare an instance. To use the same
 * configuration as a {@link WebClient}, use {@link #builder(WebClient)}.
 * <p>{@code ReactiveVaultClient} is intended to be used with relative paths
 * requiring a {@link VaultEndpoint} to be
 * {@link Builder#endpoint(VaultEndpoint) configured}. Without an endpoint,
 * callers must provide the absolute URL for each request.
 * <p>For examples with a response body see:
 * <ul>
 * <li>{@link RequestHeadersSpec#retrieve() retrieve()}</ul>
 * <p>For examples with a request body see:
 * <ul>
 * <li>{@link RequestBodySpec#bodyValue(Object) bodyValue(Object)}
 * <li>{@link RequestBodySpec#body(Publisher, Class) body(Publisher,Class)}</ul>
 *
 * @author Mark Paluch
 * @since 4.1
 */
public interface ReactiveVaultClient {

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
	 * Return a builder to create a new {@code ReactiveVaultClient} whose settings
	 * are replicated from this {@code ReactiveVaultClient}.
	 */
	Builder mutate();


	// Static factory methods

	/**
	 * Create a new {@code ReactiveVaultClient}.
	 *
	 * @see #create(String)
	 * @see #builder()
	 */
	static ReactiveVaultClient create() {
		return new DefaultReactiveVaultClientBuilder().build();
	}

	/**
	 * Variant of {@link #create()} that accepts a default base URL. For more
	 * details see {@link Builder#endpoint(VaultEndpoint)}.
	 * @param baseUrl the base URI for all requests.
	 * @see #builder()
	 */
	static ReactiveVaultClient create(String baseUrl) {
		return new DefaultReactiveVaultClientBuilder().endpoint(VaultEndpoint.from(baseUrl)).build();
	}

	/**
	 * Variant of {@link #create()} that accepts a default {@code VaultEndpoint}.
	 * For more details see {@link Builder#endpoint(VaultEndpoint)}.
	 * @param endpoint the Vault Endpoint for all relative path requests.
	 * @see #builder()
	 */
	static ReactiveVaultClient create(VaultEndpoint endpoint) {
		return new DefaultReactiveVaultClientBuilder().endpoint(endpoint).build();
	}

	/**
	 * Variant of {@link #create()} that accepts a default
	 * {@code VaultEndpointProvider}. For more details see
	 * {@link Builder#endpoint(VaultEndpointProvider)}.
	 * @param endpointProvider the endpoint provider for all relative path requests.
	 * @see #builder()
	 */
	static ReactiveVaultClient create(VaultEndpointProvider endpointProvider) {
		return new DefaultReactiveVaultClientBuilder().endpoint(endpointProvider).build();
	}

	/**
	 * Variant of {@link #create()} that accepts a default
	 * {@code ReactiveVaultEndpointProvider}. For more details see
	 * {@link Builder#endpoint(ReactiveVaultEndpointProvider)}.
	 * @param endpointProvider the endpoint provider for all relative path requests.
	 * @see #builder()
	 */
	static ReactiveVaultClient create(ReactiveVaultEndpointProvider endpointProvider) {
		return new DefaultReactiveVaultClientBuilder().endpoint(endpointProvider).build();
	}

	/**
	 * Obtain a {@code ReactiveVaultClient} builder.
	 */
	static Builder builder() {
		return new DefaultReactiveVaultClientBuilder();
	}

	/**
	 * Obtain a {@code ReactiveVaultClient} builder based on the configuration of
	 * the given {@link WebClient}.
	 * <p>The {@link WebClient} must be configured with appropriate
	 * {@link VaultClients#configureConverters(ClientBuilder) HttpMessageConverters}
	 * to support String, byte[], and JSON conversion. Additionally, if the client
	 * is configured to use a base URL, the built {@code ReactiveVaultClient} can be
	 * used with relative paths.
	 * @param webClient the {@link WebClient} to base the returned builder's
	 * configuration on.
	 * @return a {@code ReactiveVaultClient} builder initialized with
	 * {@code webClient}'s configuration
	 */
	static Builder builder(WebClient webClient) {
		return new DefaultReactiveVaultClientBuilder(webClient);
	}


	/**
	 * A mutable builder for creating a {@link ReactiveVaultClient}.
	 */
	interface Builder {

		/**
		 * Set the Vault endpoint to use.
		 * @param endpoint the vault endpoint to use.
		 * @return this builder.
		 */
		Builder endpoint(VaultEndpoint endpoint);

		/**
		 * Set the Vault endpoint provider to use.
		 * @param endpointProvider the vault endpoint provider to use.
		 * @return this builder.
		 */
		Builder endpoint(VaultEndpointProvider endpointProvider);

		/**
		 * Set the Vault endpoint provider to use.
		 * @param endpointProvider the vault endpoint provider to use.
		 * @return this builder.
		 */
		Builder endpoint(ReactiveVaultEndpointProvider endpointProvider);

		/**
		 * Provide a pre-configured {@link UriBuilderFactory} instance. This is an
		 * alternative to, and effectively overrides the following shortcut properties:
		 * <ul>
		 * <li>{@link #endpoint(VaultEndpoint)}}
		 * <li>{@link #endpoint(ReactiveVaultEndpointProvider)}}.</ul>
		 * @param uriBuilderFactory the URI builder factory to use.
		 * @return this builder.
		 * @see #endpoint(VaultEndpoint)
		 * @see #endpoint(ReactiveVaultEndpointProvider)
		 * @see VaultClients#createUriBuilderFactory(VaultEndpointProvider)
		 */
		Builder uriBuilderFactory(UriBuilderFactory uriBuilderFactory);

		/**
		 * Global option to specify a namespace header to be added to every request, if
		 * the request does not already contain such a header.
		 * @param namespace the namespace header value.
		 * @return this builder.
		 */
		default Builder defaultNamespace(String namespace) {
			return defaultHeader(VaultHttpHeaders.VAULT_NAMESPACE, namespace);
		}

		/**
		 * Global option to specify a header to be added to every request, if the
		 * request does not already contain such a header.
		 * @param header the header name.
		 * @param values the header values.
		 * @return this builder.
		 */
		Builder defaultHeader(String header, String... values);

		/**
		 * Configure the {@link ClientHttpConnector} to use. This is useful for plugging
		 * in and/or customizing options of the underlying HTTP client library (for
		 * example, SSL).
		 * <p>By default this is set to
		 * {@link org.springframework.http.client.reactive.ReactorClientHttpConnector
		 * ReactorClientHttpConnector}.
		 * @param connector the connector to use.
		 */
		Builder clientConnector(ClientHttpConnector connector);

		/**
		 * Provide a consumer to access {@link WebClient.Builder} with the possibility
		 * to override or augment its configuration.
		 * @param WebClientBuilderConsumer the consumer.
		 * @return this builder.
		 */
		Builder configureWebClient(Consumer<WebClient.Builder> WebClientBuilderConsumer);

		/**
		 * Apply the given {@code Consumer} to this builder instance.
		 * <p>This can be useful for applying pre-packaged customizations.
		 * @param builderConsumer the consumer to apply
		 * @return this builder
		 */
		Builder apply(Consumer<Builder> builderConsumer);

		/**
		 * Clone this {@code ReactiveVaultClient.Builder}.
		 */
		Builder clone();

		/**
		 * Build the {@code ReactiveVaultClient} instance.
		 */
		ReactiveVaultClient build();

	}


	/**
	 * Contract for specifying the path for a request.
	 *
	 * @param <S> a self reference to the spec type
	 */
	interface PathSpec<S extends ReactiveVaultClient.RequestHeadersSpec<?>> {

		/**
		 * Specify the path for the request using a URI template and URI variables.
		 * <p>If a {@link VaultEndpointProvider} or {@link UriBuilderFactory} was configured
		 * for the client (for example, with a base URI) this method will these to expand the URI
		 * template and prevent usage of absolute URIs to avoid unwanted access to
		 * servers other than the {@link VaultEndpoint}.
		 */
		S path(String path, @Nullable Object... pathVariables);

		/**
		 * Specify the path for the request using a URI template and URI variables.
		 * <p>If a {@link VaultEndpointProvider} or {@link UriBuilderFactory} was configured
		 * for the client (for example, with a base URI) this method will these to expand the URI
		 * template and prevent usage of absolute URIs to avoid unwanted access to
		 * servers other than the {@link VaultEndpoint}.
		 */
		S path(String path, Map<String, ? extends @Nullable Object> pathVariables);

		/**
		 * Specify the URI using a fully constructed {@link URI}.
		 * <p>If the given URI is absolute, it is used as given. If it is a relative
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
	interface RequestHeadersSpec<S extends ReactiveVaultClient.RequestHeadersSpec<S>> {

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
		 * {@link ReactiveVaultClient.ResponseSpec} to select from a number of built-in
		 * options to extract the response. For example:
		 * <p><pre class="code">
		 * Mono&lt;ResponseEntity&lt;Person&gt;&gt; entityMono = client.get()
		 *     .uri("/persons/1")
		 *     .accept(MediaType.APPLICATION_JSON)
		 *     .retrieve()
		 *     .toEntity(Person.class);
		 * </pre>
		 * <p>Or if interested only in the body:
		 * <p><pre class="code">
		 * Mono&lt;Person&gt; entityMono = client.get()
		 *     .uri("/persons/1")
		 *     .accept(MediaType.APPLICATION_JSON)
		 *     .retrieve()
		 *     .bodyToMono(Person.class);
		 * </pre>
		 * <p>By default, 4xx and 5xx responses result in a
		 * {@link VaultClientResponseException}. To customize error handling, use
		 * {@link ResponseSpec#onStatus(Predicate, Function) onStatus} handlers.
		 * @return {@code ResponseSpec} to specify how to decode the body.
		 */
		@CheckReturnValue
		ReactiveVaultClient.ResponseSpec retrieve();

		/**
		 * An alternative to {@link #retrieve()} that provides more control via access
		 * to the {@link ClientResponse}. This can be useful for advanced scenarios, for
		 * example to decode the response differently depending on the response status:
		 * <p><pre class="code">
		 * Mono&lt;Person&gt; entityMono = client.get()
		 *     .path("/persons/1")
		 *     .accept(MediaType.APPLICATION_JSON)
		 *     .exchangeToMono(response -&gt; {
		 *         if (response.statusCode().equals(HttpStatus.OK)) {
		 *             return response.bodyToMono(Person.class);
		 *         }
		 *         else {
		 *             return response.createError();
		 *         }
		 *     });
		 * </pre>
		 * <p><strong>Note:</strong> After the returned {@code Mono} completes, the
		 * response body is automatically released if it hasn't been consumed. If the
		 * response content is needed, the provided function must declare how to decode
		 * it.
		 * @param responseHandler the function to handle the response with.
		 * @param <V> the type of Object the response will be transformed to.
		 * @return a {@code Mono} produced from the response.
		 */
		<V> Mono<V> exchangeToMono(Function<ClientResponse, ? extends Mono<V>> responseHandler);

		/**
		 * An alternative to {@link #retrieve()} that provides more control via access
		 * to the {@link ClientResponse}. This can be useful for advanced scenarios, for
		 * example to decode the response differently depending on the response status:
		 * <p><pre class="code">
		 * Flux&lt;Person&gt; entityMono = client.get()
		 *     .path("/persons")
		 *     .accept(MediaType.APPLICATION_JSON)
		 *     .exchangeToFlux(response -&gt; {
		 *         if (response.statusCode().equals(HttpStatus.OK)) {
		 *             return response.bodyToFlux(Person.class);
		 *         }
		 *         else {
		 *             return response.createError().flux();
		 *         }
		 *     });
		 * </pre>
		 * <p><strong>Note:</strong> After the returned {@code Flux} completes, the
		 * response body is automatically released if it hasn't been consumed. If the
		 * response content is needed, the provided function must declare how to decode
		 * it.
		 * @param responseHandler the function to handle the response with.
		 * @param <V> the type of Objects the response will be transformed to.
		 * @return a {@code Flux} of Objects produced from the response.
		 */
		<V> Flux<V> exchangeToFlux(Function<ClientResponse, ? extends Flux<V>> responseHandler);

	}


	/**
	 * Contract for specifying request headers and body leading up to the exchange.
	 */
	interface RequestBodySpec extends ReactiveVaultClient.RequestHeadersSpec<ReactiveVaultClient.RequestBodySpec> {

		/**
		 * Shortcut for {@link #body(BodyInserter)} with a
		 * {@linkplain BodyInserters#fromValue value inserter}. For example:
		 * <p><pre class="code">
		 * Person person = ... ;
		 *
		 * Mono&lt;Void&gt; result = client.post()
		 *   .uri("/persons/{id}", id)
		 *   .contentType(MediaType.APPLICATION_JSON)
		 *   .bodyValue(person)
		 *   .retrieve()
		 *   .bodyToMono(Void.class);
		 * </pre>
		 * <p>For multipart requests consider providing
		 * {@link org.springframework.util.MultiValueMap MultiValueMap} prepared with
		 * {@link org.springframework.http.client.MultipartBodyBuilder
		 * MultipartBodyBuilder}.
		 * @param body the value to write to the request body.
		 * @return this builder.
		 * @throws IllegalArgumentException if {@code body} is a {@link Publisher} or
		 * producer known to {@link ReactiveAdapterRegistry}
		 * @see #bodyValue(Object, ParameterizedTypeReference)
		 */
		RequestHeadersSpec<?> bodyValue(Object body);

		/**
		 * Shortcut for {@link #body(BodyInserter)} with a
		 * {@linkplain BodyInserters#fromValue value inserter}. For example:
		 * <p><pre class="code">
		 * List&lt;Person&gt; list = ... ;
		 *
		 * Mono&lt;Void&gt; result = client.post()
		 *   .uri("/persons/{id}", id)
		 *   .contentType(MediaType.APPLICATION_JSON)
		 *   .bodyValue(list, new ParameterizedTypeReference&lt;List&lt;Person&gt;&gt;() {};)
		 *   .retrieve()
		 *   .bodyToMono(Void.class);
		 * </pre>
		 * <p>For multipart requests consider providing
		 * {@link org.springframework.util.MultiValueMap MultiValueMap} prepared with
		 * {@link org.springframework.http.client.MultipartBodyBuilder
		 * MultipartBodyBuilder}.
		 * @param body the value to write to the request body.
		 * @param bodyType the type of the body, used to capture the generic type.
		 * @param <T> the type of the body.
		 * @return this builder.
		 * @throws IllegalArgumentException if {@code body} is a {@link Publisher} or
		 * producer known to {@link ReactiveAdapterRegistry}
		 */
		<T> RequestHeadersSpec<?> bodyValue(T body, ParameterizedTypeReference<T> bodyType);

		/**
		 * Shortcut for {@link #body(BodyInserter)} with a
		 * {@linkplain BodyInserters#fromPublisher Publisher inserter}. For example:
		 * <p><pre class="code">
		 * Mono&lt;Person&gt; personMono = ... ;
		 *
		 * Mono&lt;Void&gt; result = client.post()
		 *   .uri("/persons/{id}", id)
		 *   .contentType(MediaType.APPLICATION_JSON)
		 *   .body(personMono, Person.class)
		 *   .retrieve()
		 *   .bodyToMono(Void.class);
		 * </pre>
		 * @param publisher the {@code Publisher} to write to the request
		 * @param elementClass the type of elements published
		 * @param <T> the type of the elements contained in the publisher.
		 * @param <P> the type of the {@code Publisher}.
		 * @return this builder.
		 */
		<T, P extends Publisher<T>> RequestHeadersSpec<?> body(P publisher, Class<T> elementClass);

		/**
		 * Variant of {@link #body(Publisher, Class)} that allows providing element type
		 * information with generics.
		 * @param publisher the {@code Publisher} to write to the request.
		 * @param elementTypeRef the type of elements published.
		 * @param <T> the type of the elements contained in the publisher.
		 * @param <P> the type of the {@code Publisher}.
		 * @return this builder.
		 */
		<T, P extends Publisher<T>> RequestHeadersSpec<?> body(P publisher,
				ParameterizedTypeReference<T> elementTypeRef);

		/**
		 * Variant of {@link #body(Publisher, Class)} that allows using any producer
		 * that can be resolved to {@link Publisher} via
		 * {@link ReactiveAdapterRegistry}.
		 * @param producer the producer to write to the request
		 * @param elementClass the type of elements produced
		 * @return this builder
		 */
		RequestHeadersSpec<?> body(Object producer, Class<?> elementClass);

		/**
		 * Variant of {@link #body(Publisher, ParameterizedTypeReference)} that allows
		 * using any producer that can be resolved to {@link Publisher} via
		 * {@link ReactiveAdapterRegistry}.
		 * @param producer the producer to write to the request
		 * @param elementTypeRef the type of elements produced
		 * @return this builder
		 */
		RequestHeadersSpec<?> body(Object producer, ParameterizedTypeReference<?> elementTypeRef);

		/**
		 * Set the body of the request using the given body inserter. See
		 * {@link BodyInserters} for built-in {@link BodyInserter} implementations.
		 * @param inserter the body inserter to use for the request body
		 * @return this builder
		 * @see org.springframework.web.reactive.function.BodyInserters
		 */
		RequestHeadersSpec<?> body(BodyInserter<?, ? super ClientHttpRequest> inserter);

	}


	/**
	 * Contract for specifying response operations following the exchange.
	 */
	interface ResponseSpec {

		/**
		 * Provide a function to map specific error status codes to an error signal to
		 * be propagated downstream instead of the response.
		 * <p>By default, if there are no matching status handlers, responses with
		 * status codes &gt;= 400 are mapped to {@link VaultClientResponseException}
		 * which is created with {@link ClientResponse#createException()}.
		 * <p>To suppress the treatment of a status code as an error and process it as a
		 * normal response, return {@code Mono.empty()} from the function. The response
		 * will then propagate downstream to be processed.
		 * <p>To ignore an error response completely, and propagate neither response nor
		 * error, use a {@link ExchangeFilterFunction filter}, or add
		 * {@code onErrorResume} downstream, for example: <pre class="code">
		 * webClient.get()
		 *   .path("https://abc.com/account/123")
		 *   .retrieve()
		 *   .bodyToMono(Account.class)
		 *   .onErrorResume(VaultClientResponseException.class,
		 *     ex -&gt; ex.getStatusCode().value() == 404 ? Mono.empty() : Mono.error(ex));
		 * </pre>
		 * @param statusPredicate to match responses with
		 * @param exceptionFunction to map the response to an error signal
		 * @return this builder
		 * @see ClientResponse#createException()
		 */
		ResponseSpec onStatus(Predicate<HttpStatusCode> statusPredicate,
				Function<ClientResponse, Mono<? extends Throwable>> exceptionFunction);

		/**
		 * Wrap the response in a cubbyhole token with the requested TTL.
		 * @param ttl the time to live for the wrapped response.
		 * @return the cubbyhole {@link WrappedMetadata} providing a token and metadata
		 * for the wrapped response.
		 * @throws VaultClientResponseException when receiving a response with a status
		 * code of 4xx or 5xx.
		 * @throws IllegalStateException if no response body was available.
		 */
		Mono<WrappedMetadata> wrap(Duration ttl);

		/**
		 * Decode the body to {@link VaultResponse}. For an error response (status code
		 * of 4xx or 5xx), the {@code Mono} emits a {@link VaultException}. Use
		 * {@link #onStatus(Predicate, Function)} to customize error response handling.
		 * @return the body, or {@code null} if no response body was available.
		 * @throws VaultClientResponseException when receiving a response with a status
		 * code of 4xx or 5xx.
		 */
		Mono<VaultResponse> body();

		/**
		 * Decode the body to the given target type. For an error response (status code
		 * of 4xx or 5xx), the {@code Mono} emits a
		 * {@link VaultClientResponseException}. Use
		 * {@link #onStatus(Predicate, Function)} to customize error response handling.
		 * @param elementClass the type to decode to.
		 * @param <T> the target body type.
		 * @return the decoded body.
		 */
		<T> Mono<T> bodyToMono(Class<T> elementClass);

		/**
		 * Variant of {@link #bodyToMono(Class)} with a
		 * {@link ParameterizedTypeReference}.
		 * @param elementTypeRef the type to decode to
		 * @param <T> the target body type
		 * @return the decoded body
		 */
		<T> Mono<T> bodyToMono(ParameterizedTypeReference<T> elementTypeRef);

		/**
		 * Decode the body to a {@link Flux} with elements of the given type. For an
		 * error response (status code of 4xx or 5xx), the {@code Mono} emits a
		 * {@link VaultClientResponseException}. Use
		 * {@link #onStatus(Predicate, Function)} to customize error response handling.
		 * @param elementClass the type of element to decode to.
		 * @param <T> the body element type.
		 * @return the decoded body.
		 */
		<T> Flux<T> bodyToFlux(Class<T> elementClass);

		/**
		 * Variant of {@link #bodyToFlux(Class)} with a
		 * {@link ParameterizedTypeReference}.
		 * @param elementTypeRef the type of element to decode to.
		 * @param <T> the body element type.
		 * @return the decoded body.
		 */
		<T> Flux<T> bodyToFlux(ParameterizedTypeReference<T> elementTypeRef);

		/**
		 * Return a {@code ResponseEntity} with the body decoded to an Object of the
		 * given type. For an error response (status code of 4xx or 5xx), the
		 * {@code Mono} emits a {@link VaultClientResponseException}. Use
		 * {@link #onStatus(Predicate, Function)} to customize error response handling.
		 * @param bodyClass the expected response body type.
		 * @param <T> response body type.
		 * @return the {@code ResponseEntity} with the decoded body.
		 */
		<T> Mono<ResponseEntity<T>> toEntity(Class<T> bodyClass);

		/**
		 * Variant of {@link #toEntity(Class)} with a
		 * {@link ParameterizedTypeReference}.
		 * @param bodyTypeRef the expected response body type.
		 * @param <T> the response body type.
		 * @return the {@code ResponseEntity} with the decoded body.
		 */
		<T> Mono<ResponseEntity<T>> toEntity(ParameterizedTypeReference<T> bodyTypeRef);

		/**
		 * Return a {@code ResponseEntity} with the body decoded to a {@code List} of
		 * elements of the given type. For an error response (status code of 4xx or
		 * 5xx), the {@code Mono} emits a {@link VaultClientResponseException}. Use
		 * {@link #onStatus(Predicate, Function)} to customize error response handling.
		 * @param elementClass the type of element to decode the target Flux to.
		 * @param <T> the body element type.
		 * @return the {@code ResponseEntity}.
		 */
		<T> Mono<ResponseEntity<List<T>>> toEntityList(Class<T> elementClass);

		/**
		 * Variant of {@link #toEntityList(Class)} with a
		 * {@link ParameterizedTypeReference}.
		 * @param elementTypeRef the type of element to decode the target Flux to
		 * @param <T> the body element type.
		 * @return the {@code ResponseEntity}.
		 */
		<T> Mono<ResponseEntity<List<T>>> toEntityList(ParameterizedTypeReference<T> elementTypeRef);

		/**
		 * Return a {@code ResponseEntity} with the body decoded to a {@code Flux}
		 * of elements of the given type. For an error response (status code of
		 * 4xx or 5xx), the {@code Mono} emits a {@link VaultClientResponseException}.
		 * Use {@link #onStatus(Predicate, Function)} to customize error response
		 * handling.
		 * <p><strong>Note:</strong> The {@code Flux} representing the body must
		 * be subscribed to or else associated resources will not be released.
		 * @param elementType the type of element to decode the target Flux to.
		 * @param <T> the body element type.
		 * @return the {@code ResponseEntity}
		 */
		<T> Mono<ResponseEntity<Flux<T>>> toEntityFlux(Class<T> elementType);

		/**
		 * Variant of {@link #toEntityFlux(Class)} with a {@link ParameterizedTypeReference}.
		 * @param elementTypeRef the type of element to decode the target Flux to.
		 * @param <T> the body element type.
		 * @return the {@code ResponseEntity}
		 */
		<T> Mono<ResponseEntity<Flux<T>>> toEntityFlux(ParameterizedTypeReference<T> elementTypeRef);

		/**
		 * Return a {@code ResponseEntity} without a body. For an error response (status
		 * code of 4xx or 5xx), the {@code Mono} emits a
		 * {@link VaultClientResponseException}. Use
		 * {@link #onStatus(Predicate, Function)} to customize error response handling.
		 * @return the {@code ResponseEntity}.
		 */
		Mono<ResponseEntity<Void>> toBodilessEntity();

	}


	/**
	 * Contract for specifying request headers and path for a request.
	 *
	 * @param <S> a self reference to the spec type.
	 */
	interface RequestHeadersPathSpec<S extends ReactiveVaultClient.RequestHeadersSpec<S>>
			extends PathSpec<S>, ReactiveVaultClient.RequestHeadersSpec<S> {

	}


	/**
	 * Contract for specifying request headers, body and path for a request.
	 */
	interface RequestHeadersBodyPathSpec
			extends ReactiveVaultClient.RequestBodySpec, RequestHeadersPathSpec<RequestBodySpec> {

	}

}
