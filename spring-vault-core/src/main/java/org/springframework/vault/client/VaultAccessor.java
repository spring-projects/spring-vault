/*
 * Copyright 2016 the original author or authors.
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

import java.net.URI;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/**
 * Base class for Vault accessing helpers, defining common properties such as the
 * {@link RestTemplate} to operate on.
 * <p>
 * Not intended to be used directly. See {@link VaultClient}.
 *
 * @author Spencer Gibb
 * @author Mark Paluch
 */
public abstract class VaultAccessor {

	private final RestTemplate restTemplate;

	/**
	 * Create a {@link VaultAccessor} with a {@link RestTemplate}.
	 * 
	 * @param restTemplate must not be {@literal null}.
	 */
	protected VaultAccessor(RestTemplate restTemplate) {

		Assert.notNull(restTemplate, "RestTemplate must not be null");

		this.restTemplate = restTemplate;
	}

	/**
	 * Execute a {@link RestTemplateCallback} in the scope of the {@link RestTemplate}.
	 * 
	 * @param uri must not be {@literal null}.
	 * @param callback must not be {@literal null}.
	 * @param <T> return type
	 * @return the {@link RestTemplateCallback} return value.
	 */
	protected <T> T doWithRestTemplate(URI uri, RestTemplateCallback<T> callback) {

		Assert.notNull(uri, "URI must not be null");
		Assert.notNull(callback, "RestTemplateCallback must not be null");

		return callback.doWithRestTemplate(uri, getRestTemplate());
	}

	public <T, S extends T> VaultResponseEntity<S> exchange(URI uri,
			HttpMethod httpMethod, HttpEntity<?> httpEntity, Class<T> returnType) {

		Assert.notNull(uri, "URI must not be null");
		Assert.notNull(httpMethod, "HttpMethod must not be null");
		Assert.notNull(returnType, "Return type must not be null");

		try {
			ResponseEntity<T> response = this.getRestTemplate().exchange(uri, httpMethod,
					httpEntity, returnType);

			return new VaultResponseEntity<S>((S) response.getBody(),
					response.getStatusCode(), uri, response.getStatusCode()
							.getReasonPhrase());
		}
		catch (HttpStatusCodeException e) {
			return handleCodeException(uri, e);
		}
	}

	public <T, S extends T> VaultResponseEntity<S> exchange(URI uri,
			HttpMethod httpMethod, HttpEntity<?> httpEntity,
			ParameterizedTypeReference<T> returnType) {

		Assert.notNull(uri, "URI must not be null");
		Assert.notNull(httpMethod, "HttpMethod must not be null");
		Assert.notNull(returnType, "Return type must not be null");

		try {
			ResponseEntity<T> response = this.getRestTemplate().exchange(uri, httpMethod,
					httpEntity, returnType);

			return new VaultResponseEntity<S>((S) response.getBody(),
					response.getStatusCode(), uri, response.getStatusCode()
							.getReasonPhrase());
		}
		catch (HttpStatusCodeException e) {
			return handleCodeException(uri, e);
		}
	}

	private <T> VaultResponseEntity<T> handleCodeException(URI uri,
			HttpStatusCodeException e) {

		String message = e.getResponseBodyAsString();

		if (MediaType.APPLICATION_JSON.includes(e.getResponseHeaders().getContentType())) {
			message = VaultErrorMessage.getError(message);
		}

		return new VaultResponseEntity<T>(null, e.getStatusCode(), uri, message);
	}

	/**
	 * @return the underlying {@link RestTemplate}.
	 */
	public RestTemplate getRestTemplate() {
		return restTemplate;
	}

	/**
	 * A callback for executing arbitrary operations on the {@link RestTemplate}.
	 *
	 * @author Mark Paluch
	 */
	public interface RestTemplateCallback<T> {

		/**
		 * @param uri must not be {@literal null}.
		 * @param restTemplate must not be {@literal null}.
		 * @return a result object or null if none.
		 */
		T doWithRestTemplate(URI uri, RestTemplate restTemplate);
	}

}
