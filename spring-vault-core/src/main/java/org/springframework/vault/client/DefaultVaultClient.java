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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/**
 * Implementation of the low-level Vault client. This client uses the Vault HTTP API to
 * issue requests using different {@link HttpMethod HTTP methods}.
 * {@link DefaultVaultClient} is configured with an {@link VaultEndpoint} and
 * {@link RestTemplate}. It does not maintain any session or token state. See
 * {@link org.springframework.vault.core.VaultTemplate} and
 * {@link org.springframework.vault.authentication.SessionManager} for authenticated and
 * stateful Vault access. {@link DefaultVaultClient} encapsulates base URI and path
 * construction for request and error handling by returning {@link VaultResponseEntity}
 * for requests.
 *
 * @author Mark Paluch
 * @see VaultResponseEntity
 * @see VaultClient
 * @see VaultRequest
 * @see VaultRequestBody
 */
public class DefaultVaultClient implements VaultClient {

	public static final String VAULT_TOKEN = "X-Vault-Token";

	private static final MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();

	private final VaultEndpoint endpoint;

	private final RestTemplate restTemplate;

	/**
	 * Creates a new {@link PreviousVaultClient} for a {@link RestTemplate} and
	 * {@link VaultEndpoint}.
	 *
	 * @param restTemplate must not be {@literal null}.
	 * @param endpoint must not be {@literal null}.
	 */
	private DefaultVaultClient(RestTemplate restTemplate, VaultEndpoint endpoint) {

		Assert.notNull(endpoint, "VaultEndpoint must not be null");
		Assert.notNull(restTemplate, "RestTemplate must not be null");

		this.endpoint = endpoint;
		this.restTemplate = restTemplate;
	}

	public static VaultClient create() {
		return new DefaultVaultClient(new RestTemplate(), new VaultEndpoint());
	}

	public static VaultClient create(RestTemplate restTemplate,
			VaultEndpoint vaultEndpoint) {
		return new DefaultVaultClient(restTemplate, vaultEndpoint);
	}

	public static VaultClient create(ClientHttpRequestFactory clientHttpRequestFactory,
			VaultEndpoint vaultEndpoint) {
		return new DefaultVaultClient(newRestTemplate(clientHttpRequestFactory),
				vaultEndpoint);
	}

	/**
	 * Create a {@link RestTemplate} using an interceptor given a
	 * {@link ClientHttpRequestFactory}. This forces {@link RestTemplate} to create the
	 * body representation instead of streaming the body to the TCP channel. Streaming the
	 * body without knowing the size in advance will skip the
	 * {@link HttpHeaders#CONTENT_LENGTH} makes Vault upset.
	 * 
	 * @param requestFactory must not be {@literal null}.
	 * @return the {@link RestTemplate}
	 */
	private static RestTemplate newRestTemplate(ClientHttpRequestFactory requestFactory) {

		Assert.notNull(requestFactory, "ClientHttpRequestFactory must not be null");

		RestTemplate restTemplate = new RestTemplate(requestFactory);
		restTemplate.getInterceptors().add(new ClientHttpRequestInterceptor() {

			@Override
			public ClientHttpResponse intercept(HttpRequest request, byte[] body,
					ClientHttpRequestExecution execution) throws IOException {
				return execution.execute(request, body);
			}
		});

		return restTemplate;
	}

	@Override
	public UriSpec get() {
		return method(HttpMethod.GET);
	}

	@Override
	public UriSpec head() {
		return method(HttpMethod.HEAD);
	}

	@Override
	public UriSpec post() {
		return method(HttpMethod.POST);
	}

	@Override
	public UriSpec put() {
		return method(HttpMethod.PUT);
	}

	@Override
	public UriSpec delete() {
		return method(HttpMethod.DELETE);
	}

	@Override
	public UriSpec method(HttpMethod method) {
		return new DefaultUriSpec(method);
	}

	public VaultResponseEntity<Object> exchange(VaultRequest<?> request) {
		return exchange(request, Object.class);
	}

	public <T, S extends T> VaultResponseEntity<S> exchange(VaultRequest<?> request,
			Class<T> returnType) {

		Assert.notNull(request, "VaultRequest must not be null");
		Assert.notNull(returnType, "Return type must not be null");

		HttpEntity<Object> entity = getRequestEntity(request);

		try {
			ResponseEntity<T> response = this.restTemplate.exchange(request.url(),
					request.method(), entity, returnType);

			return new VaultResponseEntity<S>((S) response.getBody(),
					response.getStatusCode(), request.url(), response.getStatusCode()
							.getReasonPhrase());
		}
		catch (HttpStatusCodeException e) {
			return handleCodeException(request.url(), e);
		}
	}

	private HttpEntity<Object> getRequestEntity(VaultRequest<?> request) {
		HttpEntity<Object> entity;

		if (request.body() != VaultRequestBody.empty()) {
			entity = new HttpEntity<Object>(request.body().getBody(), request.headers());
		}
		else {
			entity = new HttpEntity<Object>(request.headers());
		}
		return entity;
	}

	public <T, S extends T> VaultResponseEntity<S> exchange(VaultRequest<?> request,
			ParameterizedTypeReference<T> returnType) {

		Assert.notNull(request, "VaultRequest must not be null");
		Assert.notNull(returnType, "Return type must not be null");

		HttpEntity<Object> entity = getRequestEntity(request);

		try {
			ResponseEntity<T> response = this.restTemplate.exchange(request.url(),
					request.method(), entity, returnType);

			return new VaultResponseEntity<S>((S) response.getBody(),
					response.getStatusCode(), request.url(), response.getStatusCode()
							.getReasonPhrase());
		}
		catch (HttpStatusCodeException e) {
			return handleCodeException(request.url(), e);
		}
	}

	private <T, S extends T> VaultResponseEntity<S> handleCodeException(URI uri,
			HttpStatusCodeException e) {

		String message = e.getResponseBodyAsString();

		if (MediaType.APPLICATION_JSON.includes(e.getResponseHeaders().getContentType())) {
			message = VaultErrorMessage.getError(message);
		}

		return new VaultResponseEntity<S>(null, e.getStatusCode(), uri, message);
	}

	/**
	 * @return the configured {@link VaultEndpoint}.
	 */
	public VaultEndpoint getEndpoint() {
		return endpoint;
	}

	/**
	 * Build the Vault {@link URI} based on the given {@link VaultEndpoint} and
	 * {@code pathTemplate}. URI template variables will be expanded using
	 * {@code uriVariables}.
	 *
	 * @param pathTemplate must not be empty or {@literal null}.
	 * @param uriVariables must not be {@literal null}.
	 * @return the resolved {@link URI}.
	 * @see org.springframework.web.util.UriComponentsBuilder
	 */
	protected URI buildUri(String pathTemplate, Map<String, ?> uriVariables) {

		Assert.hasText(pathTemplate, "Path must not be empty");

		return restTemplate.getUriTemplateHandler().expand(
				getEndpoint().createUriString(pathTemplate), uriVariables);
	}

	/**
	 * Build the Vault {@link URI} based on the given {@link VaultEndpoint} and
	 * {@code pathTemplate}. URI template variables will be expanded using
	 * {@code uriVariables}.
	 *
	 * @param pathTemplate must not be empty or {@literal null}.
	 * @param uriVariables must not be {@literal null}.
	 * @return the resolved {@link URI}.
	 * @see org.springframework.web.util.UriComponentsBuilder
	 */
	protected URI buildUri(String pathTemplate, Object... uriVariables) {

		Assert.hasText(pathTemplate, "Path must not be empty");

		return restTemplate.getUriTemplateHandler().expand(
				getEndpoint().createUriString(pathTemplate), uriVariables);
	}

	/**
	 * Create {@link HttpHeaders} for a {@link VaultToken}.
	 *
	 * @param vaultToken must not be {@literal null}.
	 * @return {@link HttpHeaders} for a {@link VaultToken}.
	 */
	static HttpHeaders createHeaders(VaultToken vaultToken) {

		Assert.notNull(vaultToken, "Vault Token must not be null");

		HttpHeaders headers = new HttpHeaders();
		headers.add(VAULT_TOKEN, vaultToken.getToken());
		return headers;
	}

	/**
	 * Unwrap a wrapped response created by Vault Response Wrapping
	 * 
	 * @param wrappedResponse the wrapped response , must not be empty or {@literal null}.
	 * @param responseType the type of the return value.
	 * @return the unwrapped response.
	 */
	@SuppressWarnings("unchecked")
	public <T> T unwrap(final String wrappedResponse, Class<T> responseType) {

		Assert.hasText(wrappedResponse, "Wrapped response must not be empty");

		try {
			return (T) converter.read(responseType, new HttpInputMessage() {
				@Override
				public InputStream getBody() throws IOException {
					return new ByteArrayInputStream(wrappedResponse.getBytes());
				}

				@Override
				public HttpHeaders getHeaders() {
					return new HttpHeaders();
				}
			});
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private class DefaultUriSpec implements UriSpec {

		private final HttpMethod httpMethod;

		DefaultUriSpec(HttpMethod httpMethod) {
			this.httpMethod = httpMethod;
		}

		@Override
		public HeaderSpec uri(URI uri) {
			return new DefaultHeaderSpec(DefaultVaultRequest.method(this.httpMethod, uri));
		}

		@Override
		public HeaderSpec uri(String uriTemplate, Object... uriVariables) {
			return uri(buildUri(uriTemplate, uriVariables));
		}

		@Override
		public HeaderSpec uri(String uriTemplate, Map<String, ?> uriVariables) {
			return uri(buildUri(uriTemplate, uriVariables));
		}
	}

	private class DefaultHeaderSpec implements HeaderSpec {

		private final VaultRequest.Builder requestBuilder;

		private final HttpHeaders headers = new HttpHeaders();

		DefaultHeaderSpec(VaultRequest.Builder requestBuilder) {
			this.requestBuilder = requestBuilder;
		}

		@Override
		public HeaderSpec header(String headerName, String... headerValues) {
			for (String headerValue : headerValues) {
				this.headers.add(headerName, headerValue);
			}
			return this;
		}

		@Override
		public HeaderSpec headers(HttpHeaders headers) {
			this.headers.putAll(headers);
			return this;
		}

		@Override
		public HeaderSpec token(VaultToken vaultToken) {

			Assert.notNull(vaultToken, "VaultToken must not be null");
			header(VAULT_TOKEN, vaultToken.getToken());

			return this;
		}

		@Override
		public ReturnTypeSpec body(VaultRequestBody<?> body) {
			return new DefaultReturnTypeSpec(this.requestBuilder.headers(this.headers)
					.body(body));
		}

		@Override
		public VaultResponseEntity<Void> exchange(VaultRequestBody<?> body) {
			return body(body).exchange();
		}

		@Override
		public VaultResponseEntity<Void> exchange() {
			return exchange(Void.class);
		}

		@Override
		public <T, S extends T> VaultResponseEntity<S> exchange(Class<T> returnType) {
			return body(VaultRequestBody.empty()).exchange(returnType);
		}

		@Override
		public <T, S extends T> VaultResponseEntity<S> exchange(
				ParameterizedTypeReference<T> returnType) {
			return body(VaultRequestBody.empty()).exchange(returnType);
		}
	}

	private class DefaultReturnTypeSpec implements ReturnTypeSpec {

		private final VaultRequest<?> vaultRequest;

		public DefaultReturnTypeSpec(VaultRequest<?> vaultRequest) {
			this.vaultRequest = vaultRequest;
		}

		@Override
		public VaultResponseEntity<Void> exchange() {
			return exchange(Void.class);
		}

		@Override
		public <T, S extends T> VaultResponseEntity<S> exchange(Class<T> returnType) {
			return DefaultVaultClient.this.exchange(vaultRequest, returnType);
		}

		@Override
		public <T, S extends T> VaultResponseEntity<S> exchange(
				ParameterizedTypeReference<T> returnType) {
			return DefaultVaultClient.this.exchange(vaultRequest, returnType);
		}
	}
}
