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
package org.springframework.vault.authentication;

import java.net.URI;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestOperations;

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
		return new RestOperationsAdapter(restOperations);
	}

	/**
	 * Factory method creating a ClientAdapter delegating to {@link RestClient}.
	 */
	public static ClientAdapter from(RestClient client) {
		return new RestClientAdapter(client);
	}

	/**
	 * Create a new resource by POSTing the given object to the URI template, and return
	 * the representation found in the response.
	 * <p>
	 * URI Template variables are expanded using the given URI variables, if any.
	 * <p>
	 * The {@code request} parameter can be a {@link HttpEntity} in order to add
	 * additional HTTP headers to the request.
	 * <p>
	 * The body of the entity, or {@code request} itself, can be a
	 * {@link org.springframework.util.MultiValueMap MultiValueMap} to create a multipart
	 * request. The values in the {@code MultiValueMap} can be any Object representing the
	 * body of the part, or an {@link org.springframework.http.HttpEntity HttpEntity}
	 * representing a part with body and headers.
	 * @param url the URL
	 * @param request the Object to be POSTed (may be {@code null})
	 * @param responseType the type of the return value
	 * @param uriVariables the variables to expand the template
	 * @return the converted object
	 * @see HttpEntity
	 */
	abstract <T> @Nullable T postForObject(String url, @Nullable Object request, Class<T> responseType,
			@Nullable Object... uriVariables);

	/**
	 * Create a new resource by POSTing the given object to the URI template, and return
	 * the representation found in the response.
	 * <p>
	 * URI Template variables are expanded using the given map.
	 * <p>
	 * The {@code request} parameter can be a {@link HttpEntity} in order to add
	 * additional HTTP headers to the request.
	 * <p>
	 * The body of the entity, or {@code request} itself, can be a
	 * {@link org.springframework.util.MultiValueMap MultiValueMap} to create a multipart
	 * request. The values in the {@code MultiValueMap} can be any Object representing the
	 * body of the part, or an {@link org.springframework.http.HttpEntity HttpEntity}
	 * representing a part with body and headers.
	 * @param url the URL
	 * @param request the Object to be POSTed (may be {@code null})
	 * @param responseType the type of the return value
	 * @param uriVariables the variables to expand the template
	 * @return the converted object
	 * @see HttpEntity
	 */
	abstract <T> @Nullable T postForObject(String url, @Nullable Object request, Class<T> responseType,
			Map<String, ? extends @Nullable Object> uriVariables);

	/**
	 * Execute the HTTP method to the given URI template, writing the given request entity
	 * to the request, and return the response as {@link ResponseEntity}.
	 * <p>
	 * URI Template variables are expanded using the given URI variables, if any.
	 * @param url the URL
	 * @param method the HTTP method (GET, POST, etc)
	 * @param requestEntity the entity (headers and/or body) to write to the request may
	 * be {@code null})
	 * @param responseType the type to convert the response to, or {@code Void.class} for
	 * no body
	 * @param uriVariables the variables to expand in the template
	 * @return the response as entity
	 */
	abstract <T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			Class<T> responseType, @Nullable Object... uriVariables);

	/**
	 * Execute the HTTP method to the given URI template, writing the given request entity
	 * to the request, and return the response as {@link ResponseEntity}.
	 * <p>
	 * URI Template variables are expanded using the given URI variables, if any.
	 * @param url the URL
	 * @param method the HTTP method (GET, POST, etc)
	 * @param requestEntity the entity (headers and/or body) to write to the request (may
	 * be {@code null})
	 * @param responseType the type to convert the response to, or {@code Void.class} for
	 * no body
	 * @param uriVariables the variables to expand in the template
	 * @return the response as entity
	 */
	abstract <T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			Class<T> responseType, Map<String, ? extends @Nullable Object> uriVariables);

	/**
	 * Execute the HTTP method to the given URI template, writing the given request entity
	 * to the request, and return the response as {@link ResponseEntity}.
	 * @param url the URL
	 * @param method the HTTP method (GET, POST, etc)
	 * @param requestEntity the entity (headers and/or body) to write to the request (may
	 * be {@code null})
	 * @param responseType the type to convert the response to, or {@code Void.class} for
	 * no body
	 * @return the response as entity
	 */
	abstract <T> ResponseEntity<T> exchange(URI uri, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			Class<T> responseType);

	/**
	 * {@link RestOperations}-based adapter.
	 */
	static class RestOperationsAdapter extends ClientAdapter {

		private final RestOperations restOperations;

		RestOperationsAdapter(RestOperations restOperations) {
			this.restOperations = restOperations;
		}

		@Override
		<T> @Nullable T postForObject(String url, @Nullable Object request, Class<T> responseType,
				@Nullable Object... uriVariables) {
			return restOperations.postForObject(url, request, responseType, uriVariables);
		}

		@Override
		<T> @Nullable T postForObject(String url, @Nullable Object request, Class<T> responseType,
				Map<String, ?> uriVariables) {
			return restOperations.postForObject(url, request, responseType, uriVariables);
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

		RestClientAdapter(RestClient client) {
			this.client = client;
		}

		@Override
		<T> @Nullable T postForObject(String url, @Nullable Object request, Class<T> responseType,
				@Nullable Object... uriVariables) {
			return retrieve(client.post().uri(url, uriVariables), request, responseType).getBody();
		}

		@Override
		<T> @Nullable T postForObject(String url, @Nullable Object request, Class<T> responseType,
				Map<String, ?> uriVariables) {
			return retrieve(client.post().uri(url, uriVariables), request, responseType).getBody();
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
