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
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.WrappedMetadata;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Mark Paluch
 */
public class DefaultVaultClient implements VaultClient {

	private final RestClient client;

	private final boolean allowAbsolutePath;

	private final Builder builder;

	DefaultVaultClient(RestClient client, boolean allowAbsolutePath, VaultClient.Builder builder) {
		this.client = client;
		this.allowAbsolutePath = allowAbsolutePath;
		this.builder = builder;
	}

	@Override
	public RequestHeadersPathSpec<?> get() {
		return method(HttpMethod.GET);
	}

	@Override
	public RequestBodyPathSpec post() {
		return method(HttpMethod.POST);
	}

	@Override
	public RequestBodyPathSpec put() {
		return method(HttpMethod.PUT);
	}

	@Override
	public RequestHeadersPathSpec<?> delete() {
		return method(HttpMethod.DELETE);
	}

	@Override
	public RequestBodyPathSpec method(HttpMethod method) {
		Assert.notNull(method, "HttpMethod must not be null");
		return methodInternal(method);
	}

	private RequestBodyPathSpec methodInternal(HttpMethod httpMethod) {
		return new DefaultRequestBodyUriSpec(httpMethod, client.method(httpMethod));
	}

	@Override
	public RestClient getRestClient() {
		return this.client;
	}

	@Override
	public Builder mutate() {
		return builder;
	}

	private class DefaultRequestBodyUriSpec implements RequestBodyPathSpec {

		private final RestClient.RequestBodyUriSpec spec;

		private final HttpMethod httpMethod;

		private @Nullable String path;

		private RestClient.@Nullable RequestBodySpec uriSpec;

		public DefaultRequestBodyUriSpec(HttpMethod httpMethod, RestClient.RequestBodyUriSpec spec) {
			this.httpMethod = httpMethod;
			this.spec = spec;
		}

		@Override
		public RequestBodySpec path(String path, @Nullable Object... pathVariables) {
			assertPath(path);
			this.path = path;
			this.uriSpec = this.spec.uri(path, pathVariables);
			return this;
		}

		private void assertPath(String path) {

			if (allowAbsolutePath) {
				return;
			}

			UriComponents components = UriComponentsBuilder.fromUriString(path)
					.build();

			if (StringUtils.hasText(components.getScheme()) || StringUtils.hasText(components.getHost())) {
				throw new IllegalArgumentException("Absolute URIs are not allowed");
			}
		}

		@Override
		public RequestBodySpec path(String path, Map<String, ?> pathVariables) {
			assertPath(path);
			this.path = path;
			this.uriSpec = this.spec.uri(path, pathVariables);
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

		private final DefaultRequestBodyUriSpec requestHeadersSpec;
		private final RestClient.ResponseSpec retrieve;


		DefaultResponseSpec(DefaultRequestBodyUriSpec requestHeadersSpec, RestClient.ResponseSpec retrieve) {
			this.requestHeadersSpec = requestHeadersSpec;
			this.retrieve = retrieve;
		}

		@Override
		public ResponseSpec onStatus(Predicate<HttpStatusCode> statusPredicate, RestClient.ResponseSpec.ErrorHandler errorHandler) {
			retrieve.onStatus(statusPredicate, errorHandler);
			return this;
		}

		@Override
		public ResponseSpec onStatus(ResponseErrorHandler errorHandler) {
			retrieve.onStatus(errorHandler);
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
			}
			catch (HttpStatusCodeException e) {
				throw VaultResponses.buildException(e, requestHeadersSpec.path);
			}
		}

		@Override
		@SuppressWarnings("NullAway") // See https://github.com/uber/NullAway/issues/1290
		public <T> @Nullable T body(ParameterizedTypeReference<T> bodyType) {
			try {
				return retrieve.body(bodyType);
			}
			catch (HttpStatusCodeException e) {
				throw VaultResponses.buildException(e, requestHeadersSpec.path);
			}
		}

		@Override
		public VaultResponse requiredBody() {
			return requiredBody(VaultResponse.class);
		}

		@SuppressWarnings("NullAway") // See https://github.com/uber/NullAway/issues/1290
		public <T> T requiredBody(Class<T> bodyType) {

			T body = body(bodyType);

			if (body == null) {
				throw new VaultException("No body returned from Vault; %s %s".formatted(requestHeadersSpec.httpMethod.name(), requestHeadersSpec.path));
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
			}
			catch (HttpStatusCodeException e) {
				throw VaultResponses.buildException(e, requestHeadersSpec.path);
			}
		}

		@Override
		public <T> ResponseEntity<T> toEntity(ParameterizedTypeReference<T> bodyType) {
			try {
				return retrieve.toEntity(bodyType);
			}
			catch (HttpStatusCodeException e) {
				throw VaultResponses.buildException(e, requestHeadersSpec.path);
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
			}
			catch (HttpStatusCodeException e) {
				throw VaultResponses.buildException(e, requestHeadersSpec.path);
			}
		}

	}
}
