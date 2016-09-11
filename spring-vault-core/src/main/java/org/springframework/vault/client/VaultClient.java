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
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Implementation of the low-level Vault client. This client uses the Vault HTTP API to issue requests using different
 * {@link HttpMethod HTTP methods}. {@link VaultClient} is configured with an {@link VaultEndpoint} and
 * {@link RestTemplate}. It does not maintain any session or token state. See {@link VaultTemplate} and
 * {@link org.springframework.vault.authentication.SessionManager} for authenticated and stateful Vault access.
 * <p>
 * {@link VaultClient} encapsulates base URI and path construction and uses {@link VaultAccessor} for request and error
 * handling by returning {@link VaultResponseEntity} for requests.
 * 
 * @author Mark Paluch
 * @see VaultResponseEntity
 * @see VaultTemplate
 */
public class VaultClient extends VaultAccessor {

	public static final String VAULT_TOKEN = "X-Vault-Token";

	private final VaultEndpoint endpoint;

	/**
	 * Creates a new {@link VaultClient} with a default a {@link RestTemplate} and {@link VaultEndpoint}.
	 * 
	 * @see VaultEndpoint
	 */
	public VaultClient() {
		this(new RestTemplate(), new VaultEndpoint());
	}

	/**
	 * Creates a new {@link VaultClient} for a {@link RestTemplate} and {@link VaultEndpoint}.
	 *
	 * @param restTemplate must not be {@literal null}.
	 * @param endpoint must not be {@literal null}.
	 */
	public VaultClient(RestTemplate restTemplate, VaultEndpoint endpoint) {

		super(restTemplate);

		Assert.notNull(endpoint, "VaultEndpoint must not be null");
		this.endpoint = endpoint;
	}

	/**
	 * Retrieve a resource by GETting from the path, and returns the response as {@link VaultResponseEntity}.
	 *
	 * @param path the path.
	 * @param responseType the type of the return value
	 * @return the response as entity.
	 * @see VaultResponseEntity
	 */
	public <T, S extends T> VaultResponseEntity<S> getForEntity(String path, Class<T> responseType) {
		return exchange(path, HttpMethod.GET, new HttpEntity<Object>(null), responseType, null);
	}

	/**
	 * Retrieve a resource by GETting from the path, and returns the response as {@link VaultResponseEntity}.
	 *
	 * @param path the path.
	 * @param vaultToken the {@link VaultToken}.
	 * @param responseType the type of the return value
	 * @return the response as entity.
	 * @see VaultResponseEntity
	 */
	public <T, S extends T> VaultResponseEntity<S> getForEntity(String path, VaultToken vaultToken,
			Class<T> responseType) {

		return exchange(path, HttpMethod.GET, new HttpEntity<Object>(null, createHeaders(vaultToken)), responseType, null);
	}

	/**
	 * Issue a POST request using the given object to the path, and returns the response as {@link VaultResponseEntity}.
	 * 
	 * @param path the path.
	 * @param request the Object to be POSTed, may be {@code null}.
	 * @param responseType the type of the return value
	 * @return the response as entity.
	 * @see VaultResponseEntity
	 */
	public <T, S extends T> VaultResponseEntity<S> postForEntity(String path, Object request, Class<T> responseType) {
		return exchange(path, HttpMethod.POST, new HttpEntity<Object>(request), responseType, null);
	}

	/**
	 * Issue a POST request using the given object to the path, and returns the response as {@link VaultResponseEntity}.
	 *
	 * @param path the path.
	 * @param vaultToken the {@link VaultToken}.
	 * @param request the Object to be POSTed, may be {@code null}.
	 * @param responseType the type of the return value
	 * @return the response as entity.
	 * @see VaultResponseEntity
	 */
	public <T, S extends T> VaultResponseEntity<S> postForEntity(String path, VaultToken vaultToken, Object request,
			Class<T> responseType) {
		return exchange(path, HttpMethod.POST, new HttpEntity<Object>(request, createHeaders(vaultToken)), responseType,
				null);
	}

	/**
	 * Create a new resource by PUTting the given object to the path, and returns the response as
	 * {@link VaultResponseEntity}.
	 *
	 * @param path the path.
	 * @param request the Object to be PUT.
	 * @param responseType the type of the return value
	 * @return the response as entity.
	 * @see VaultResponseEntity
	 */
	public <T, S extends T> VaultResponseEntity<S> putForEntity(String path, Object request, Class<T> responseType) {
		return exchange(path, HttpMethod.PUT, new HttpEntity<Object>(request), responseType, null);
	}

	/**
	 * Create a new resource by PUTting the given object to the path, and returns the response as
	 * {@link VaultResponseEntity}.
	 *
	 * @param path the path.
	 * @param vaultToken the {@link VaultToken}.
	 * @param request the Object to be PUT.
	 * @param responseType the type of the return value
	 * @return the response as entity.
	 * @see VaultResponseEntity
	 */
	public <T, S extends T> VaultResponseEntity<S> putForEntity(String path, VaultToken vaultToken, Object request,
			Class<T> responseType) {
		return exchange(path, HttpMethod.PUT, new HttpEntity<Object>(request, createHeaders(vaultToken)), responseType,
				null);
	}

	/**
	 * Delete a resource by DELETEing from the path, and returns the response as {@link VaultResponseEntity}.
	 *
	 * @param path the path.
	 * @param vaultToken the {@link VaultToken}.
	 * @param responseType the type of the return value
	 * @return the response as entity.
	 * @see VaultResponseEntity
	 */
	public <T, S extends T> VaultResponseEntity<S> deleteForEntity(String path, VaultToken vaultToken,
			Class<T> responseType) {

		return exchange(path, HttpMethod.DELETE, new HttpEntity<Object>(null, createHeaders(vaultToken)), responseType,
				null);
	}

	/**
	 * Execute the HTTP method to the given URI template, writing the given request entity to the request, and returns the
	 * response as {@link VaultResponseEntity}.
	 * <p>
	 * URI Template variables are using the given URI variables, if any.
	 * 
	 * @param pathTemplate the path template.
	 * @param method the HTTP method (GET, POST, etc).
	 * @param requestEntity the entity (headers and/or body) to write to the request, may be {@code null}.
	 * @param responseType the type of the return value.
	 * @param uriVariables the variables to expand in the template.
	 * @return the response as entity.
	 */
	public <T, S extends T> VaultResponseEntity<S> exchange(String pathTemplate, HttpMethod method,
			HttpEntity<?> requestEntity, Class<T> responseType, Map<String, ?> uriVariables) throws RestClientException {

		URI uri = uriVariables != null ? buildUri(pathTemplate, uriVariables) : getEndpoint().createUri(pathTemplate);

		return exchange(uri, method, requestEntity, responseType);
	}

	/**
	 * Execute the HTTP method to the given path template, writing the given request entity to the request, and returns
	 * the response as {@link VaultResponseEntity}. The given {@link ParameterizedTypeReference} is used to pass generic
	 * type information:
	 * 
	 * <pre class="code">
	 * ParameterizedTypeReference&lt;List&lt;MyBean&gt;&gt; myBean = new ParameterizedTypeReference&lt;List&lt;MyBean&gt;&gt;() {};
	 * ResponseEntity&lt;List&lt;MyBean&gt;&gt; response = client.exchange(&quot;http://example.com&quot;, HttpMethod.GET, null, myBean, null);
	 * </pre>
	 * 
	 * @param pathTemplate the path template.
	 * @param method the HTTP method (GET, POST, etc).
	 * @param requestEntity the entity (headers and/or body) to write to the request, may be {@code null}.
	 * @param responseType the type of the return value.
	 * @param uriVariables the variables to expand in the template.
	 * @return the response as entity.
	 */
	public <T, S extends T> VaultResponseEntity<S> exchange(String pathTemplate, HttpMethod method,
			HttpEntity<?> requestEntity, ParameterizedTypeReference<T> responseType, Map<String, ?> uriVariables)
			throws RestClientException {

		URI uri = uriVariables != null ? buildUri(pathTemplate, uriVariables) : getEndpoint().createUri(pathTemplate);

		return exchange(uri, method, requestEntity, responseType);
	}

	/**
	 * Executes a {@link RestTemplateCallback}. Allows to interact with the underlying {@link RestTemplate} and benefit
	 * from optional parameter expansion.
	 *
	 * @param pathTemplate the path template.
	 * @param uriVariables the variables to expand in the template
	 * @param callback the request.
	 * @return the {@link RestTemplateCallback} return value.
	 */
	public <T> T doWithRestTemplate(String pathTemplate, Map<String, ?> uriVariables, RestTemplateCallback<T> callback) {

		URI uri = uriVariables != null ? buildUri(pathTemplate, uriVariables) : getEndpoint().createUri(pathTemplate);

		return super.doWithRestTemplate(uri, callback);
	}

	/**
	 * @return the configured {@link VaultEndpoint}.
	 */
	public VaultEndpoint getEndpoint() {
		return endpoint;
	}

	/**
	 * Build the Vault {@link URI} based on the given {@link VaultEndpoint} and {@code pathTemplate}. URI template
	 * variables will be expanded using {@code uriVariables}.
	 *
	 * @param pathTemplate must not be empty or {@literal null}.
	 * @param uriVariables must not be {@literal null}.
	 * @see org.springframework.web.util.UriComponentsBuilder
	 * @return
	 */
	protected URI buildUri(String pathTemplate, Map<String, ?> uriVariables) {

		Assert.hasText(pathTemplate, "Path must not be empty");

		return getRestTemplate().getUriTemplateHandler().expand(getEndpoint().createUriString(pathTemplate), uriVariables);
	}

	/**
	 * Create {@link HttpHeaders} for a {@link VaultToken}.
	 * 
	 * @param vaultToken must not be {@literal null}.
	 * @return {@link HttpHeaders} for a {@link VaultToken}.
	 */
	public static HttpHeaders createHeaders(VaultToken vaultToken) {

		Assert.notNull(vaultToken, "Vault Token must not be null");

		HttpHeaders headers = new HttpHeaders();
		headers.add(VAULT_TOKEN, vaultToken.getToken());
		return headers;
	}
}
