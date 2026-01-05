/*
 * Copyright 2025-present the original author or authors.
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

import java.net.URI;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.vault.client.VaultClient;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/**
 * Simple adapter to unify {@link RestOperations} and {@link RestClient} usage.
 *
 * @author Mark Paluch
 * @since 4.0
 */
abstract class ClientAdapter {

	/**
	 * Factory method creating a ClientAdapter delegating to {@link RestOperations}.
	 */
	public static ClientAdapter from(RestOperations restOperations) {
		Assert.notNull(restOperations, "RestOperations must not be null");
		return new RestOperationsAdapter(restOperations);
	}

	/**
	 * Factory method creating a ClientAdapter delegating to {@link RestClient}.
	 */
	public static ClientAdapter from(RestClient client) {
		Assert.notNull(client, "RestClient must not be null");
		return new RestClientAdapter(client);
	}

	public VaultLoginClient loginClient(String authenticationMechanism) {
		return VaultLoginClient.create(vaultClient(), authenticationMechanism);
	}

	public abstract VaultClient vaultClient();

	/**
	 * Execute the HTTP method to the given URI template, writing the given request
	 * entity to the request, and return the response as {@link ResponseEntity}.
	 * <p>URI Template variables are expanded using the given URI variables, if any.
	 * @param url the URL
	 * @param method the HTTP method (GET, POST, etc)
	 * @param requestEntity the entity (headers and/or body) to write to the request
	 * may be {@code null})
	 * @param responseType the type to convert the response to, or
	 * {@code Void.class} for no body
	 * @param uriVariables the variables to expand in the template
	 * @return the response as entity
	 */
	abstract <T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			Class<T> responseType, @Nullable Object... uriVariables);

	/**
	 * Execute the HTTP method to the given URI template, writing the given request
	 * entity to the request, and return the response as {@link ResponseEntity}.
	 * <p>URI Template variables are expanded using the given URI variables, if any.
	 * @param url the URL.
	 * @param method the HTTP method (GET, POST, etc).
	 * @param requestEntity the entity (headers and/or body) to write to the request
	 * (may be {@code null}).
	 * @param responseType the type to convert the response to, or
	 * {@code Void.class} for no body.
	 * @param uriVariables the variables to expand in the template.
	 * @return the response as entity.
	 */
	abstract <T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			Class<T> responseType, Map<String, ? extends @Nullable Object> uriVariables);

	/**
	 * Execute the HTTP method to the given URI template, writing the given request
	 * entity to the request, and return the response as {@link ResponseEntity}.
	 * @param uri the URL.
	 * @param method the HTTP method (GET, POST, etc).
	 * @param requestEntity the entity (headers and/or body) to write to the request
	 * (may be {@code null}).
	 * @param responseType the type to convert the response to, or
	 * {@code Void.class} for no body.
	 * @return the response as entity.
	 */
	abstract <T> ResponseEntity<T> exchange(URI uri, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			Class<T> responseType);


	/**
	 * {@link RestOperations}-based adapter.
	 */
	static class RestOperationsAdapter extends ClientAdapter {

		private final RestOperations restOperations;

		private final VaultClient vaultClient;


		RestOperationsAdapter(RestOperations restOperations) {
			this.restOperations = restOperations;
			this.vaultClient = VaultClient.builder((RestTemplate) restOperations).build();
		}


		@Override
		public VaultClient vaultClient() {
			return vaultClient;
		}

		@Override
		<T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
				Class<T> responseType, @Nullable Object... uriVariables) {
			return restOperations.exchange(url, method, requestEntity, responseType, uriVariables);
		}

		@Override
		<T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
				Class<T> responseType, Map<String, ?> uriVariables) {
			return restOperations.exchange(url, method, requestEntity, responseType, uriVariables);
		}

		@Override
		<T> ResponseEntity<T> exchange(URI uri, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
				Class<T> responseType) {
			return restOperations.exchange(uri, method, requestEntity, responseType);
		}

	}


	/**
	 * {@link RestClient}-based adapter.
	 */
	static class RestClientAdapter extends ClientAdapter {

		private final RestClient client;

		private final VaultClient vaultClient;


		RestClientAdapter(RestClient client) {
			this.client = client;
			this.vaultClient = VaultClient.builder(client).build();
		}


		@Override
		public VaultClient vaultClient() {
			return vaultClient;
		}

		@Override
		<T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
				Class<T> responseType, @Nullable Object... uriVariables) {
			return retrieve(client.method(method).uri(url, uriVariables), requestEntity, responseType);
		}

		@Override
		<T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
				Class<T> responseType, Map<String, ?> uriVariables) {
			return retrieve(client.method(method).uri(url, uriVariables), requestEntity, responseType);
		}

		@Override
		<T> ResponseEntity<T> exchange(URI uri, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
				Class<T> responseType) {
			return retrieve(client.method(method).uri(uri), requestEntity, responseType);
		}

		private static <T> ResponseEntity<T> retrieve(RestClient.RequestBodySpec spec, @Nullable Object requestEntity,
				Class<T> responseType) {

			if (requestEntity instanceof HttpEntity<?> entity) {

				Object body = entity.getBody();
				if (body != null) {
					spec = spec.body(body);
				}

				spec = spec.headers(it -> it.putAll(entity.getHeaders()));
			}

			return spec.retrieve().toEntity(responseType);
		}

	}

}
