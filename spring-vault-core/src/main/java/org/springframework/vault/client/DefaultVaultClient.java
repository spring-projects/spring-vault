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

import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.WrappedMetadata;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilderFactory;

/**
 * The default implementation of {@link VaultClient}, as created by the static factory
 * methods.
 *
 * @author Mark Paluch
 * @see VaultClient#create()
 * @see VaultClient#create(String)
 * @see VaultClient#builder()
 * @since 4.1
 */
class DefaultVaultClient implements VaultClient {

	private final RestClient client;

	private final @Nullable UriBuilderFactory uriBuilderFactory;

	private final Builder builder;

	DefaultVaultClient(RestClient client, @Nullable UriBuilderFactory uriBuilderFactory, VaultClient.Builder builder) {
		this.client = client;
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

		private final RestClient.RequestBodyUriSpec spec;

		private final HttpMethod httpMethod;

		private @Nullable String path;

		private RestClient.@Nullable RequestBodySpec uriSpec;

		public DefaultRequestHeadersBodyUriSpec(HttpMethod httpMethod, RestClient.RequestBodyUriSpec spec) {
			this.httpMethod = httpMethod;
			this.spec = spec;
		}

		@Override
		public RequestBodySpec path(String path, @Nullable Object... pathVariables) {
			this.path = path;
			this.uriSpec = uriBuilderFactory != null ? this.spec.uri(uriBuilderFactory.expand(path, pathVariables))
					: this.spec.uri(path, pathVariables);
			return this;
		}

		@Override
		public RequestBodySpec path(String path, Map<String, ?> pathVariables) {
			this.path = path;
			this.uriSpec = uriBuilderFactory != null ? this.spec.uri(uriBuilderFactory.expand(path, pathVariables))
					: this.spec.uri(path, pathVariables);
			return this;
		}

		@Override
		public RequestBodySpec uri(URI uri) {
			this.path = uri.toString();

			if (uri.isAbsolute() || uriBuilderFactory == null) {
				this.uriSpec = this.spec.uri(uri);
			} else {
				URI baseUri = uriBuilderFactory.expand("");
				this.uriSpec = this.spec.uri(baseUri.resolve(uri));
			}

			return this;
		}

		@Override
		public RequestBodySpec body(Object body) {
			this.uriSpec.body(body);
			return this;
		}

		@Override
		public <T> RequestBodySpec body(T body, ParameterizedTypeReference<T> bodyType) {
			this.uriSpec.body(body, bodyType);
			return this;
		}

		@Override
		public RequestBodySpec header(String headerName, String... headerValues) {
			this.uriSpec.header(headerName, headerValues);
			return this;
		}

		@Override
		public RequestBodySpec headers(HttpHeaders httpHeaders) {
			this.uriSpec.headers(it -> it.putAll(httpHeaders));
			return this;
		}

		@Override
		public RequestBodySpec headers(Consumer<HttpHeaders> headersConsumer) {
			this.uriSpec.headers(headersConsumer);
			return this;
		}

		@Override
		public ResponseSpec retrieve() {
			return new DefaultResponseSpec(this, this.uriSpec.retrieve());
		}
	}

	private class DefaultResponseSpec implements ResponseSpec {

		private final DefaultRequestHeadersBodyUriSpec requestHeadersSpec;

		private final RestClient.ResponseSpec retrieve;

		DefaultResponseSpec(DefaultRequestHeadersBodyUriSpec requestHeadersSpec, RestClient.ResponseSpec retrieve) {
			this.requestHeadersSpec = requestHeadersSpec;
			this.retrieve = retrieve;
		}

		@Override
		public ResponseSpec onStatus(Predicate<HttpStatusCode> statusPredicate,
									 RestClient.ResponseSpec.ErrorHandler errorHandler) {
			retrieve.onStatus(statusPredicate, errorHandler);
			return this;
		}

		@Override
		public WrappedMetadata wrap(Duration ttl) {
			requestHeadersSpec.header(VaultHttpHeaders.VAULT_WRAP_TTL, ttl.toSeconds() + "s");
			return WrappedMetadata.from(requiredBody());
		}

		@Override
		@SuppressWarnings("NullAway") // See https://github.com/uber/NullAway/issues/1290
		public <T> @Nullable T body(Class<T> bodyType) {
			try {
				return retrieve.body(bodyType);
			} catch (HttpStatusCodeException e) {
				throw buildException(e, requestHeadersSpec.path);
			}
		}

		@Override
		@SuppressWarnings("NullAway") // See https://github.com/uber/NullAway/issues/1290
		public <T> @Nullable T body(ParameterizedTypeReference<T> bodyType) {
			try {
				return retrieve.body(bodyType);
			} catch (HttpStatusCodeException e) {
				throw buildException(e, requestHeadersSpec.path);
			}
		}

		@Override
		public VaultResponse requiredBody() {
			return requiredBody(VaultResponse.class);
		}

		@SuppressWarnings("NullAway") // See https://github.com/uber/NullAway/issues/1290
		@Override
		public <T> T requiredBody(Class<T> bodyType) {

			T body = body(bodyType);

			if (body == null) {
				throw new VaultException("No body returned from Vault; %s %s"
						.formatted(requestHeadersSpec.httpMethod.name(), requestHeadersSpec.path));
			}

			return body;
		}

		@Override
		public @Nullable VaultResponse body() {
			return body(VaultResponse.class);
		}

		@Override
		public <T> ResponseEntity<T> toEntity(Class<T> bodyType) {
			try {
				return retrieve.toEntity(bodyType);
			} catch (HttpStatusCodeException e) {
				throw buildException(e, requestHeadersSpec.path);
			}
		}

		@Override
		public <T> ResponseEntity<T> toEntity(ParameterizedTypeReference<T> bodyType) {
			try {
				return retrieve.toEntity(bodyType);
			} catch (HttpStatusCodeException e) {
				throw buildException(e, requestHeadersSpec.path);
			}
		}

		@Override
		public ResponseEntity<VaultResponse> toEntity() {
			return toEntity(VaultResponse.class);
		}

		@Override
		public ResponseEntity<Void> toBodilessEntity() {
			try {
				return retrieve.toBodilessEntity();
			} catch (HttpStatusCodeException e) {
				throw buildException(e, requestHeadersSpec.path);
			}
		}


		/**
		 * Build a {@link VaultException} given {@link HttpStatusCodeException} and request
		 * {@code path}.
		 *
		 * @param e    must not be {@literal null}.
		 * @param path must not be {@literal null}.
		 * @return the {@link VaultException}.
		 */
		public static VaultClientResponseException buildException(HttpStatusCodeException e, @Nullable String path) {

			Assert.notNull(e, "HttpStatusCodeException must not be null");

			String message = VaultResponses.getError(e.getResponseBodyAsString());

			if (StringUtils.hasText(message)) {
				return new VaultRestClientResponseException("Status %s %s [%s]: %s".formatted(VaultResponses.renderStatus(e.getStatusCode()),
						e.getStatusText(), path, message), e);
			}

			return new VaultRestClientResponseException(
					"Status %s %s [%s]".formatted(VaultResponses.renderStatus(e.getStatusCode()), e.getStatusText(), path), e);
		}

		public static VaultException buildException(HttpStatusCode statusCode, String path, String message) {

			if (StringUtils.hasText(message)) {
				return new VaultException("Status %s [%s]: %s".formatted(VaultResponses.renderStatus(statusCode), path, message));
			}

			return new VaultException("Status %s [%s]".formatted(VaultResponses.renderStatus(statusCode), path));
		}
	}

	static class VaultRestClientResponseException extends VaultClientResponseException {

		public VaultRestClientResponseException(String msg, RestClientResponseException cause) {
			super(msg, cause);
		}

		@Override
		@SuppressWarnings("NullAway")
		public synchronized RestClientResponseException getCause() {
			return (RestClientResponseException) super.getCause();
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
