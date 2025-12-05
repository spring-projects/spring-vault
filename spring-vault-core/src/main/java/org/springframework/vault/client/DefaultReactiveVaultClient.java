package org.springframework.vault.client;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.WrappedMetadata;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilderFactory;

/**
 * The default implementation of {@link ReactiveVaultClient}, as created by the static factory
 * methods.
 *
 * @author Mark Paluch
 * @see ReactiveVaultClient#create()
 * @see ReactiveVaultClient#create(String)
 * @see ReactiveVaultClient#builder()
 * @since 4.1
 */
class DefaultReactiveVaultClient implements ReactiveVaultClient {

	private final WebClient client;

	private final @Nullable ReactiveVaultEndpointProvider endpointProvider;

	private final @Nullable UriBuilderFactory uriBuilderFactory;

	private final ReactiveVaultClient.Builder builder;

	DefaultReactiveVaultClient(WebClient client, @Nullable ReactiveVaultEndpointProvider endpointProvider, @Nullable UriBuilderFactory uriBuilderFactory, ReactiveVaultClient.Builder builder) {
		this.client = client;
		this.endpointProvider = endpointProvider;
		this.uriBuilderFactory = uriBuilderFactory;
		this.builder = builder;
	}

	@Override
	public RequestHeadersPathSpec<?> get() {
		return method(HttpMethod.GET);
	}

	@Override
	public RequestHeadersBodyPathSpec post() {
		return method(HttpMethod.POST);
	}

	@Override
	public RequestHeadersBodyPathSpec put() {
		return method(HttpMethod.PUT);
	}

	@Override
	public RequestHeadersPathSpec<?> delete() {
		return method(HttpMethod.DELETE);
	}

	@Override
	public RequestHeadersBodyPathSpec method(HttpMethod method) {
		Assert.notNull(method, "HttpMethod must not be null");
		return methodInternal(method);
	}

	private RequestHeadersBodyPathSpec methodInternal(HttpMethod httpMethod) {
		return new DefaultRequestHeadersBodyUriSpec(httpMethod, client.method(httpMethod));
	}

	@Override
	public Builder mutate() {
		return builder;
	}

	@SuppressWarnings("NullAway")
	private class DefaultRequestHeadersBodyUriSpec implements RequestHeadersBodyPathSpec {

		private final WebClient.RequestBodyUriSpec spec;

		private final HttpMethod httpMethod;

		private @Nullable String path;

		private @Nullable Supplier<Mono<WebClient.RequestHeadersSpec<?>>> responseMono;

		public DefaultRequestHeadersBodyUriSpec(HttpMethod httpMethod, WebClient.RequestBodyUriSpec spec) {
			this.httpMethod = httpMethod;
			this.spec = spec;
		}

		@Override
		public RequestBodySpec path(String path, @Nullable Object... pathVariables) {
			this.path = path;

			if (uriBuilderFactory == null && endpointProvider == null) {
				this.spec.uri(path, pathVariables);
			} else if (endpointProvider != null) {
				responseMono = () -> {
					return endpointProvider.getVaultEndpoint().map(it -> {
						return this.spec.uri(VaultClients.getUriComponents(it, path).build().toUri());
					});
				};
			} else {
				this.spec.uri(uriBuilderFactory.expand(path, pathVariables));
			}

			return this;
		}

		@Override
		public RequestBodySpec path(String path, Map<String, ?> pathVariables) {
			this.path = path;

			if (uriBuilderFactory == null && endpointProvider == null) {
				this.spec.uri(path, pathVariables);
			} else if (endpointProvider != null) {
				responseMono = () -> {
					return endpointProvider.getVaultEndpoint().map(it -> {
						return this.spec.uri(VaultClients.getUriComponents(it, path).build().toUri());
					});
				};
			} else {
				this.spec.uri(uriBuilderFactory.expand(path, pathVariables));
			}

			return this;
		}

		@Override
		public RequestBodySpec uri(URI uri) {
			this.path = uri.toString();

			if (uri.isAbsolute() && uriBuilderFactory == null && endpointProvider == null) {
				this.spec.uri(uri);
			} else if (endpointProvider != null) {
				responseMono = () -> {
					return endpointProvider.getVaultEndpoint().map(it -> {
						return this.spec.uri(VaultClients.getUriComponents(it, "").uri(uri).build().toUri());
					});
				};
			} else {
				URI baseUri = uriBuilderFactory.expand("");
				this.spec.uri(baseUri.resolve(uri));
			}

			return this;
		}

		@Override
		public RequestHeadersSpec<?> bodyValue(Object body) {
			this.spec.bodyValue(body);
			return this;
		}

		@Override
		public <T> RequestHeadersSpec<?> bodyValue(T body, ParameterizedTypeReference<T> bodyType) {
			this.spec.bodyValue(body, bodyType);
			return this;
		}

		@Override
		public <T, P extends Publisher<T>> RequestHeadersSpec<?> body(P publisher, Class<T> elementClass) {
			this.spec.body(publisher, elementClass);
			return this;
		}

		@Override
		public <T, P extends Publisher<T>> RequestHeadersSpec<?> body(P publisher, ParameterizedTypeReference<T> elementTypeRef) {
			this.spec.body(publisher, elementTypeRef);
			return this;
		}

		@Override
		public RequestHeadersSpec<?> body(Object producer, Class<?> elementClass) {
			this.spec.body(producer, elementClass);
			return this;
		}

		@Override
		public RequestHeadersSpec<?> body(Object producer, ParameterizedTypeReference<?> elementTypeRef) {
			this.spec.body(producer, elementTypeRef);
			return this;
		}

		@Override
		public RequestHeadersSpec<?> body(BodyInserter<?, ? super ClientHttpRequest> inserter) {
			this.spec.body(inserter);
			return this;
		}


		@Override
		public RequestBodySpec header(String headerName, String... headerValues) {
			this.spec.header(headerName, headerValues);
			return this;
		}

		@Override
		public RequestBodySpec headers(HttpHeaders httpHeaders) {
			this.spec.headers(it -> it.putAll(httpHeaders));
			return this;
		}

		@Override
		public RequestBodySpec headers(Consumer<HttpHeaders> headersConsumer) {
			this.spec.headers(headersConsumer);
			return this;
		}

		@Override
		public ResponseSpec retrieve() {

			if (this.responseMono == null) {
				return new DefaultResponseSpec(this, this.spec);
			}

			return new DefaultResponseSpec(this, this.responseMono.get());
		}
	}

	private class DefaultResponseSpec implements ResponseSpec {

		private final DefaultRequestHeadersBodyUriSpec requestHeadersSpec;

		private Mono<WebClient.RequestHeadersSpec<?>> retrieve;
		private Map<Predicate<HttpStatusCode>, Function<ClientResponse, Mono<? extends Throwable>>> statusHandlers = new LinkedHashMap<>();


		DefaultResponseSpec(DefaultRequestHeadersBodyUriSpec requestHeadersSpec, WebClient.RequestHeadersSpec<?> retrieve) {
			this.requestHeadersSpec = requestHeadersSpec;
			this.retrieve = Mono.just(retrieve);
		}

		DefaultResponseSpec(DefaultRequestHeadersBodyUriSpec requestHeadersSpec, Mono<WebClient.RequestHeadersSpec<?>> retrieve) {
			this.requestHeadersSpec = requestHeadersSpec;
			this.retrieve = retrieve;
		}

		@Override
		public ResponseSpec onStatus(Predicate<HttpStatusCode> statusPredicate, Function<ClientResponse, Mono<? extends Throwable>> exceptionFunction) {
			statusHandlers.put(statusPredicate, exceptionFunction);
			return this;
		}

		@Override
		public Mono<WrappedMetadata> wrap(Duration ttl) {
			requestHeadersSpec.header(VaultHttpHeaders.VAULT_WRAP_TTL, ttl.toSeconds() + "s");
			return body().map(WrappedMetadata::from);
		}

		@Override
		public Mono<VaultResponse> body() {
			return bodyToMono(VaultResponse.class);
		}

		@Override
		public <T> Mono<T> bodyToMono(Class<T> elementClass) {
			return toMono(it -> it.bodyToMono(elementClass));
		}

		@Override
		public <T> Mono<T> bodyToMono(ParameterizedTypeReference<T> elementTypeRef) {
			return toMono(it -> it.bodyToMono(elementTypeRef));
		}

		@Override
		public <T> Flux<T> bodyToFlux(Class<T> elementClass) {
			return toFlux(it -> it.bodyToFlux(elementClass));
		}

		@Override
		public <T> Flux<T> bodyToFlux(ParameterizedTypeReference<T> elementTypeRef) {
			return toFlux(it -> it.bodyToFlux(elementTypeRef));
		}

		public Mono<ResponseEntity<VaultResponse>> toEntity() {
			return toEntity(VaultResponse.class);
		}

		@Override
		public <T> Mono<ResponseEntity<T>> toEntity(Class<T> bodyClass) {
			return toMono(it -> it.toEntity(bodyClass));
		}

		@Override
		public <T> Mono<ResponseEntity<T>> toEntity(ParameterizedTypeReference<T> bodyTypeReference) {
			return toMono(it -> it.toEntity(bodyTypeReference));
		}

		@Override
		public <T> Mono<ResponseEntity<List<T>>> toEntityList(Class<T> elementClass) {
			return toMono(it -> it.toEntityList(elementClass));
		}

		@Override
		public <T> Mono<ResponseEntity<List<T>>> toEntityList(ParameterizedTypeReference<T> elementTypeRef) {
			return toMono(it -> it.toEntityList(elementTypeRef));
		}

		@Override
		public Mono<ResponseEntity<Void>> toBodilessEntity() {
			return toMono(ClientResponse::toBodilessEntity);
		}

		private <T> Mono<T> toMono(Function<ClientResponse, ? extends Mono<T>> bodyExtractor) {
			return retrieve.flatMap(it -> {
				return it.exchangeToMono(resp -> {
					for (Predicate<HttpStatusCode> httpStatusCodePredicate : statusHandlers.keySet()) {
						if (httpStatusCodePredicate.test(resp.statusCode())) {
							Function<ClientResponse, Mono<? extends Throwable>> f = statusHandlers.get(httpStatusCodePredicate);
							return f.apply(resp).then(Mono.empty());
						}
					}
					return bodyExtractor.apply(resp);
				});
			}).onErrorMap(WebClientResponseException.class, it -> buildException(it, requestHeadersSpec.path));
		}

		private <T> Flux<T> toFlux(Function<ClientResponse, ? extends Flux<T>> bodyExtractor) {
			return retrieve.flatMapMany(it -> {
				return it.exchangeToFlux(resp -> {
					for (Predicate<HttpStatusCode> httpStatusCodePredicate : statusHandlers.keySet()) {
						if (httpStatusCodePredicate.test(resp.statusCode())) {
							Function<ClientResponse, Mono<? extends Throwable>> f = statusHandlers.get(httpStatusCodePredicate);
							return f.apply(resp).thenMany(Mono.empty());
						}
					}
					return bodyExtractor.apply(resp);
				});
			}).onErrorMap(WebClientResponseException.class, it -> buildException(it, requestHeadersSpec.path));
		}

		/**
		 * Build a {@link VaultException} given {@link HttpStatusCodeException} and request
		 * {@code path}.
		 *
		 * @param e    must not be {@literal null}.
		 * @param path must not be {@literal null}.
		 * @return the {@link VaultException}.
		 */
		private static VaultClientResponseException buildException(WebClientResponseException e, @Nullable String path) {

			Assert.notNull(e, "HttpStatusCodeException must not be null");

			String message = VaultResponses.getError(e.getResponseBodyAsString());

			if (StringUtils.hasText(message)) {
				return new VaultReactiveClientResponseException("Status %s %s [%s]: %s".formatted(VaultResponses.renderStatus(e.getStatusCode()),
						e.getStatusText(), path, message), e);
			}

			return new VaultReactiveClientResponseException(
					"Status %s %s [%s]".formatted(VaultResponses.renderStatus(e.getStatusCode()), e.getStatusText(), path), e);
		}
	}

	static class VaultReactiveClientResponseException extends VaultClientResponseException {

		public VaultReactiveClientResponseException(String msg, WebClientResponseException cause) {
			super(msg, cause);
		}

		@Override
		@SuppressWarnings("NullAway")
		public synchronized WebClientResponseException getCause() {
			return (WebClientResponseException) super.getCause();
		}

		@Override
		public HttpStatusCode getStatusCode() {
			return getCause().getStatusCode();
		}

		@Override
		public String getStatusText() {
			return getCause().getStatusText();
		}

		@Override
		public byte[] getResponseBodyAsByteArray() {
			return getCause().getResponseBodyAsByteArray();
		}

		@Override
		public String getResponseBodyAsString(Charset fallbackCharset) {
			return getCause().getResponseBodyAsString(fallbackCharset);
		}

		@Override
		public <E> @Nullable E getResponseBodyAs(Class<E> targetType) {
			return getCause().getResponseBodyAs(targetType);
		}

		@Override
		public <E> @Nullable E getResponseBodyAs(ParameterizedTypeReference<E> targetType) {
			return getCause().getResponseBodyAs(targetType);
		}
	}
}
