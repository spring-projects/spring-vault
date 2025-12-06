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
import org.springframework.vault.client.VaultClient;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

@SuppressWarnings("NullAway")
record RestClientOperationsWrapper(VaultClient vaultClient) implements RestOperations {

	RestClientOperationsWrapper {
		Assert.notNull(vaultClient, "RestClient must not be null");
	}


	@Override
	public <T> @Nullable T getForObject(String url, Class<T> responseType, @Nullable Object... uriVariables)
			throws RestClientException {
		return vaultClient.method(HttpMethod.GET).path(url, uriVariables).retrieve().body(responseType);
	}

	@Override
	public <T> @Nullable T getForObject(String url, Class<T> responseType, Map<String, ?> uriVariables)
			throws RestClientException {
		return vaultClient.get().path(url, uriVariables).retrieve().body(responseType);
	}

	@Override
	public <T> @Nullable T getForObject(URI url, Class<T> responseType) throws RestClientException {
		return vaultClient.get().uri(url).retrieve().body(responseType);
	}

	@Override
	public <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType, @Nullable Object... uriVariables)
			throws RestClientException {
		return vaultClient.get().path(url, uriVariables).retrieve().toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType, Map<String, ?> uriVariables)
			throws RestClientException {
		return vaultClient.get().path(url, uriVariables).retrieve().toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> getForEntity(URI url, Class<T> responseType) throws RestClientException {
		return vaultClient.get().uri(url).retrieve().toEntity(responseType);
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
		return withBody(vaultClient.post().path(url, uriVariables), request).retrieve().body(responseType);
	}

	@Override
	public <T> @Nullable T postForObject(String url, @Nullable Object request, Class<T> responseType,
			Map<String, ?> uriVariables) throws RestClientException {
		return withBody(vaultClient.post().path(url, uriVariables), request).retrieve().body(responseType);
	}

	@Override
	public <T> T postForObject(URI url, @Nullable Object request, Class<T> responseType) throws RestClientException {
		return withBody(vaultClient.post().uri(url), request).retrieve().body(responseType);
	}

	@Override
	public @Nullable URI postForLocation(String url, @Nullable Object request, @Nullable Object... uriVariables)
			throws RestClientException {
		return withBody(vaultClient.post().path(url, uriVariables), request).retrieve()
				.toBodilessEntity()
				.getHeaders()
				.getLocation();
	}

	@Override
	public @Nullable URI postForLocation(String url, @Nullable Object request, Map<String, ?> uriVariables)
			throws RestClientException {
		return withBody(vaultClient.post().path(url, uriVariables), request).retrieve()
				.toBodilessEntity()
				.getHeaders()
				.getLocation();
	}

	@Override
	public @Nullable URI postForLocation(URI url, @Nullable Object request) throws RestClientException {
		return withBody(vaultClient.post().uri(url), request).retrieve().toBodilessEntity().getHeaders().getLocation();
	}

	@Override
	public <T> ResponseEntity<T> postForEntity(String url, @Nullable Object request, Class<T> responseType,
			@Nullable Object... uriVariables) throws RestClientException {
		return withBody(vaultClient.post().path(url, uriVariables), request).retrieve().toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> postForEntity(String url, @Nullable Object request, Class<T> responseType,
			Map<String, ?> uriVariables) throws RestClientException {
		return withBody(vaultClient.post().path(url, uriVariables), request).retrieve().toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> postForEntity(URI url, @Nullable Object request, Class<T> responseType)
			throws RestClientException {
		return withBody(vaultClient.post().uri(url), request).retrieve().toEntity(responseType);
	}

	@Override
	public void put(String url, @Nullable Object request, @Nullable Object... uriVariables) throws RestClientException {
		withBody(vaultClient.put().path(url, uriVariables), request).retrieve().toBodilessEntity();
	}

	@Override
	public void put(String url, @Nullable Object request, Map<String, ?> uriVariables) throws RestClientException {
		withBody(vaultClient.put().path(url, uriVariables), request).retrieve().toBodilessEntity();
	}

	@Override
	public void put(URI url, @Nullable Object request) throws RestClientException {
		withBody(vaultClient.put().uri(url), request).retrieve().toBodilessEntity();
	}

	@Override
	public void delete(String url, @Nullable Object... uriVariables) throws RestClientException {
		vaultClient.delete().path(url, uriVariables).retrieve().toBodilessEntity();
	}

	@Override
	public void delete(String url, Map<String, ?> uriVariables) throws RestClientException {
		vaultClient.delete().path(url, uriVariables).retrieve().toBodilessEntity();
	}

	@Override
	public void delete(URI url) throws RestClientException {
		vaultClient.delete().uri(url).retrieve().toBodilessEntity();
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
	public <T> T patchForObject(String url, @Nullable Object request, Class<T> responseType,
			@Nullable Object... uriVariables) throws RestClientException {
		return withBody(vaultClient.method(HttpMethod.PATCH).path(url, uriVariables), request).retrieve()
				.body(responseType);
	}

	@Override
	public <T> T patchForObject(String url, @Nullable Object request, Class<T> responseType,
			Map<String, ?> uriVariables) throws RestClientException {
		return withBody(vaultClient.method(HttpMethod.PATCH).path(url, uriVariables), request).retrieve()
				.body(responseType);
	}

	@Override
	public <T> T patchForObject(URI url, @Nullable Object request, Class<T> responseType) throws RestClientException {
		return withBody(vaultClient.method(HttpMethod.PATCH).uri(url), request).retrieve().body(responseType);
	}

	// For generic exchange operations
	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			Class<T> responseType, @Nullable Object... uriVariables) throws RestClientException {
		return withHeadersAndBody(vaultClient.method(method).path(url, uriVariables), requestEntity).retrieve()
				.toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			Class<T> responseType, Map<String, ?> uriVariables) throws RestClientException {
		return withHeadersAndBody(vaultClient.method(method).path(url, uriVariables), requestEntity).retrieve()
				.toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> exchange(URI url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			Class<T> responseType) throws RestClientException {
		return withHeadersAndBody(vaultClient.method(method).uri(url), requestEntity).retrieve().toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			ParameterizedTypeReference<T> responseType, @Nullable Object... uriVariables) throws RestClientException {
		return withHeadersAndBody(vaultClient.method(method).path(url, uriVariables), requestEntity).retrieve()
				.toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			ParameterizedTypeReference<T> responseType, Map<String, ?> uriVariables) throws RestClientException {
		return withHeadersAndBody(vaultClient.method(method).path(url, uriVariables), requestEntity).retrieve()
				.toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> exchange(URI url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			ParameterizedTypeReference<T> responseType) throws RestClientException {
		return withHeadersAndBody(vaultClient.method(method).uri(url), requestEntity).retrieve().toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> exchange(RequestEntity<?> requestEntity, Class<T> responseType)
			throws RestClientException {
		return withHeadersAndBody(vaultClient.method(requestEntity.getMethod()).uri(requestEntity.getUrl()),
				requestEntity)
						.retrieve()
						.toEntity(responseType);
	}

	@Override
	public <T> ResponseEntity<T> exchange(RequestEntity<?> requestEntity, ParameterizedTypeReference<T> responseType)
			throws RestClientException {
		return withHeadersAndBody(vaultClient.method(requestEntity.getMethod()).uri(requestEntity.getUrl()),
				requestEntity)
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

	private <S extends VaultClient.RequestBodySpec> S withBody(S spec, @Nullable Object body) {
		if (body != null) {
			spec.body(body);
		}
		return spec;
	}

	private <S extends VaultClient.RequestBodySpec> S withHeadersAndBody(S spec,
			@Nullable HttpEntity<?> requestEntity) {
		if (requestEntity != null) {
			spec.headers(headers -> headers.putAll(requestEntity.getHeaders()));
			if (requestEntity.getBody() != null) {
				spec.body(requestEntity.getBody());
			}
		}
		return spec;
	}

}
