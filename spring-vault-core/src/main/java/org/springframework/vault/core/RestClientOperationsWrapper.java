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
package org.springframework.vault.core;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

@SuppressWarnings("NullAway")
class RestClientOperationsWrapper implements RestOperations {

	private final RestClient restClient;

	public RestClientOperationsWrapper(RestClient restClient) {
		Assert.notNull(restClient, "RestClient must not be null");
		this.restClient = restClient;
	}

	public RestClient getRestClient() {
		return restClient;
	}

	@Override
	public <T> @Nullable T getForObject(String url, Class<T> responseType, @Nullable Object... uriVariables)
			throws RestClientException {
		return restClient.get().uri(url, uriVariables).retrieve().body(responseType);
	}

	@Override
	public <T> @Nullable T getForObject(String url, Class<T> responseType, Map<String, ?> uriVariables)
			throws RestClientException {
		return restClient.get().uri(url, uriVariables).retrieve().body(responseType);
	}

	@Override
	public <T> @Nullable T getForObject(URI url, Class<T> responseType) throws RestClientException {
		return restClient.get().uri(url).retrieve().body(responseType);
	}

	@Override
	public <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType, @Nullable Object... uriVariables)
			throws RestClientException {
		return restClient.get().uri(url, uriVariables).retrieve().toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType, Map<String, ?> uriVariables)
			throws RestClientException {
		return restClient.get().uri(url, uriVariables).retrieve().toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> getForEntity(URI url, Class<T> responseType) throws RestClientException {
		return restClient.get().uri(url).retrieve().toEntity(responseType);
	}

	@Override
	public HttpHeaders headForHeaders(String url, @Nullable Object... uriVariables) throws RestClientException {
		throw new UnsupportedOperationException();
	}

	@Override
	public HttpHeaders headForHeaders(String url, Map<String, ?> uriVariables) throws RestClientException {
		throw new UnsupportedOperationException();
	}

	@Override
	public HttpHeaders headForHeaders(URI url) throws RestClientException {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> @Nullable T postForObject(String url, @Nullable Object request, Class<T> responseType,
			@Nullable Object... uriVariables) throws RestClientException {
		return restClient.post().uri(url, uriVariables).body(request).retrieve().body(responseType);
	}

	@Override
	public <T> @Nullable T postForObject(String url, @Nullable Object request, Class<T> responseType,
			Map<String, ?> uriVariables) throws RestClientException {
		return restClient.post().uri(url, uriVariables).body(request).retrieve().body(responseType);
	}

	@Override
	public <T> T postForObject(URI url, Object request, Class<T> responseType) throws RestClientException {
		return restClient.post().uri(url).body(request).retrieve().body(responseType);
	}

	@Override
	public @Nullable URI postForLocation(String url, @Nullable Object request, @Nullable Object... uriVariables)
			throws RestClientException {
		return restClient.post()
			.uri(url, uriVariables)
			.body(request)
			.retrieve()
			.toBodilessEntity()
			.getHeaders()
			.getLocation();
	}

	@Override
	public @Nullable URI postForLocation(String url, @Nullable Object request, Map<String, ?> uriVariables)
			throws RestClientException {
		return restClient.post()
			.uri(url, uriVariables)
			.body(request)
			.retrieve()
			.toBodilessEntity()
			.getHeaders()
			.getLocation();
	}

	@Override
	public @Nullable URI postForLocation(URI url, @Nullable Object request) throws RestClientException {
		return restClient.post().uri(url).body(request).retrieve().toBodilessEntity().getHeaders().getLocation();
	}

	@Override
	public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType,
			@Nullable Object... uriVariables) throws RestClientException {
		return restClient.post().uri(url, uriVariables).body(request).retrieve().toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> postForEntity(String url, @Nullable Object request, Class<T> responseType,
			Map<String, ?> uriVariables) throws RestClientException {
		return restClient.post().uri(url, uriVariables).body(request).retrieve().toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> postForEntity(URI url, @Nullable Object request, Class<T> responseType)
			throws RestClientException {
		return restClient.post().uri(url).body(request).retrieve().toEntity(responseType);
	}

	@Override
	public void put(String url, Object request, @Nullable Object... uriVariables) throws RestClientException {
		restClient.put().uri(url, uriVariables).body(request).retrieve().toBodilessEntity();
	}

	@Override
	public void put(String url, Object request, Map<String, ?> uriVariables) throws RestClientException {
		restClient.put().uri(url, uriVariables).body(request).retrieve().toBodilessEntity();
	}

	@Override
	public void put(URI url, Object request) throws RestClientException {
		restClient.put().uri(url).body(request).retrieve().toBodilessEntity();
	}

	@Override
	public void delete(String url, Object... uriVariables) throws RestClientException {
		restClient.delete().uri(url, uriVariables).retrieve().toBodilessEntity();
	}

	@Override
	public void delete(String url, Map<String, ?> uriVariables) throws RestClientException {
		restClient.delete().uri(url, uriVariables).retrieve().toBodilessEntity();
	}

	@Override
	public void delete(URI url) throws RestClientException {
		restClient.delete().uri(url).retrieve().toBodilessEntity();
	}

	@Override
	public Set<HttpMethod> optionsForAllow(String url, @Nullable Object... uriVariables) throws RestClientException {
		return Set.of();
	}

	@Override
	public Set<HttpMethod> optionsForAllow(String url, Map<String, ?> uriVariables) throws RestClientException {
		return Set.of();
	}

	@Override
	public Set<HttpMethod> optionsForAllow(URI url) throws RestClientException {
		return Set.of();
	}

	@Override
	public <T> T patchForObject(String url, Object request, Class<T> responseType, Object... uriVariables)
			throws RestClientException {
		return restClient.method(HttpMethod.PATCH).uri(url, uriVariables).body(request).retrieve().body(responseType);
	}

	@Override
	public <T> T patchForObject(String url, Object request, Class<T> responseType, Map<String, ?> uriVariables)
			throws RestClientException {
		return restClient.method(HttpMethod.PATCH).uri(url, uriVariables).body(request).retrieve().body(responseType);
	}

	@Override
	public <T> T patchForObject(URI url, Object request, Class<T> responseType) throws RestClientException {
		return restClient.method(HttpMethod.PATCH).uri(url).body(request).retrieve().body(responseType);
	}

	// For generic exchange operations
	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity,
			Class<T> responseType, Object... uriVariables) throws RestClientException {
		return restClient.method(method)
			.uri(url, uriVariables)
			.headers(headers -> headers.putAll(requestEntity.getHeaders()))
			.body(requestEntity.getBody())
			.retrieve()
			.toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity,
			Class<T> responseType, Map<String, ?> uriVariables) throws RestClientException {
		return restClient.method(method)
			.uri(url, uriVariables)
			.headers(headers -> headers.putAll(requestEntity.getHeaders()))
			.body(requestEntity.getBody())
			.retrieve()
			.toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> exchange(URI url, HttpMethod method, HttpEntity<?> requestEntity,
			Class<T> responseType) throws RestClientException {
		return restClient.method(method)
			.uri(url)
			.headers(headers -> headers.putAll(requestEntity.getHeaders()))
			.body(requestEntity.getBody())
			.retrieve()
			.toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity,
			ParameterizedTypeReference<T> responseType, Object... uriVariables) throws RestClientException {
		return restClient.method(method)
			.uri(url, uriVariables)
			.headers(headers -> headers.putAll(requestEntity.getHeaders()))
			.body(requestEntity.getBody())
			.retrieve()
			.toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity,
			ParameterizedTypeReference<T> responseType, Map<String, ?> uriVariables) throws RestClientException {
		return restClient.method(method)
			.uri(url, uriVariables)
			.headers(headers -> headers.putAll(requestEntity.getHeaders()))
			.body(requestEntity.getBody())
			.retrieve()
			.toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> exchange(URI url, HttpMethod method, HttpEntity<?> requestEntity,
			ParameterizedTypeReference<T> responseType) throws RestClientException {
		return restClient.method(method)
			.uri(url)
			.headers(headers -> headers.putAll(requestEntity.getHeaders()))
			.body(requestEntity.getBody())
			.retrieve()
			.toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> exchange(RequestEntity<?> requestEntity, Class<T> responseType)
			throws RestClientException {
		return restClient.method(requestEntity.getMethod())
			.uri(requestEntity.getUrl())
			.body(requestEntity.getBody())
			.headers(it -> it.putAll(requestEntity.getHeaders()))
			.retrieve()
			.toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> exchange(RequestEntity<?> requestEntity, ParameterizedTypeReference<T> responseType)
			throws RestClientException {
		return restClient.method(requestEntity.getMethod())
			.uri(requestEntity.getUrl())
			.body(requestEntity.getBody())
			.headers(it -> it.putAll(requestEntity.getHeaders()))
			.retrieve()
			.toEntity(responseType);
	}

	@Override
	public <T> @Nullable T execute(String uriTemplate, HttpMethod method, @Nullable RequestCallback requestCallback,
			@Nullable ResponseExtractor<T> responseExtractor, @Nullable Object... uriVariables)
			throws RestClientException {
		return null;
	}

	@Override
	public <T> @Nullable T execute(String uriTemplate, HttpMethod method, @Nullable RequestCallback requestCallback,
			@Nullable ResponseExtractor<T> responseExtractor, Map<String, ?> uriVariables) throws RestClientException {
		return null;
	}

	@Override
	public <T> @Nullable T execute(URI url, HttpMethod method, @Nullable RequestCallback requestCallback,
			@Nullable ResponseExtractor<T> responseExtractor) throws RestClientException {
		return null;
	}

}
