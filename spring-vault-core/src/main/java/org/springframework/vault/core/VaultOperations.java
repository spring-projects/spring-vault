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
package org.springframework.vault.core;

import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultResponseEntity;
import org.springframework.vault.client.VaultAccessor.RestTemplateCallback;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * Interface that specifies a basic set of Vault operations, implemented by
 * {@link VaultTemplate}. This is the main entry point to interact with Vault in an
 * authenticated and unauthenticated context with configured {@link VaultClient}
 * instances.
 * <p>
 * {@link VaultOperations} resolves {@link VaultClient} instances and allows execution of
 * callback methods on various levels. Callbacks can execute requests within a
 * {@link #doWithVault(SessionCallback) session}, the {@link #doWithVault(ClientCallback)
 * client (without requiring a session)} and a
 * {@link #doWithRestTemplate(String, Map, RestTemplateCallback) low-level}
 * {@link org.springframework.web.client.RestTemplate} level.
 *
 * @author Mark Paluch
 * @see VaultOperations#doWithVault(ClientCallback)
 * @see VaultOperations#doWithVault(SessionCallback)
 * @see VaultOperations#doWithRestTemplate(String, Map, RestTemplateCallback)
 * @see VaultClient
 * @see VaultTemplate
 * @see VaultTokenOperations
 * @see org.springframework.vault.authentication.SessionManager
 */
public interface VaultOperations {

	/**
	 * @return the operations interface administrative Vault access.
	 */
	VaultSysOperations opsForSys();

	/**
	 * @return the operations interface to interact with Vault token.
	 */
	VaultTokenOperations opsForToken();

	/**
	 * @return the operations interface to interact with the Vault transit backend.
	 */
	VaultTransitOperations opsForTransit();

	/**
	 * Returns {@link VaultTransitOperations} if the transit backend is mounted on a
	 * different path than {@code transit}.
	 * 
	 * @param path the mount path
	 * @return the operations interface to interact with the Vault transit backend.
	 */
	VaultTransitOperations opsForTransit(String path);

	/**
	 * @return the operations interface to interact with the Vault PKI backend.
	 */
	VaultPkiOperations opsForPki();

	/**
	 * Returns {@link VaultPkiOperations} if the PKI backend is mounted on a different
	 * path than {@code pki}.
	 *
	 * @param path the mount path
	 * @return the operations interface to interact with the Vault PKI backend.
	 */
	VaultPkiOperations opsForPki(String path);

	/**
	 * Read from a secret backend. Reading data using this method is suitable for secret
	 * backends that do not require a request body.
	 *
	 * @param path must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	VaultResponse read(String path);

	/**
	 * Read from a secret backend. Reading data using this method is suitable for secret
	 * backends that do not require a request body.
	 *
	 * @param path must not be {@literal null}.
	 * @param responseType must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	<T> VaultResponseSupport<T> read(String path, Class<T> responseType);

	/**
	 * Enumerate keys from a secret backend.
	 *
	 * @param path must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	List<String> list(String path);

	/**
	 * Write to a secret backend.
	 *
	 * @param path must not be {@literal null}.
	 * @param body the body, may be {@literal null} if absent.
	 * @return the configuration data. May be empty but never {@literal null}.
	 */
	VaultResponse write(String path, Object body);

	/**
	 * Delete a path in the secret backend.
	 *
	 * @param path must not be {@literal null}.
	 */
	void delete(String path);

	/**
	 * Executes a Vault {@link ClientCallback}. Allows to interact with Vault using
	 * {@link VaultClient} without requiring a session.
	 *
	 * @param clientCallback the request.
	 * @return the {@link ClientCallback} return value.
	 */
	<T> T doWithVault(ClientCallback<T> clientCallback);

	/**
	 * Executes a Vault {@link SessionCallback}. Allows to interact with Vault in an
	 * authenticated session.
	 *
	 * @param sessionCallback the request.
	 * @return the {@link SessionCallback} return value.
	 */
	<T> T doWithVault(SessionCallback<T> sessionCallback);

	/**
	 * Executes {@link RestTemplateCallback}. Expands the {@code pathTemplate} to an
	 * {@link java.net.URI} and allows low-level interaction with the underlying
	 * {@link org.springframework.web.client.RestTemplate}.
	 *
	 * @param pathTemplate the path of the resource, e.g. {@code transit/ key}/foo}, must
	 * not be empty or {@literal null}.
	 * @param variables the variables for expansion of the {@code pathTemplate}, must not
	 * be {@literal null}.
	 * @param callback the request callback.
	 * @return the {@link RestTemplateCallback} return value.
	 */
	<T> T doWithRestTemplate(String pathTemplate, Map<String, ?> variables,
			RestTemplateCallback<T> callback);

	/**
	 * A callback for executing arbitrary operations on the {@link VaultClient}.
	 *
	 * @author Mark Paluch
	 */
	public interface ClientCallback<T> {

		/**
		 * Callback method.
		 *
		 * @param client session to use, must not be {@literal null}.
		 * @return a result object or null if none.
		 */
		T doWithVault(VaultClient client);
	}

	/**
	 * A callback for executing arbitrary operations on the {@link VaultSession}.
	 *
	 * @author Mark Paluch
	 */
	public interface SessionCallback<T> {

		/**
		 * Callback method.
		 *
		 * @param session session to use, must not be {@literal null}.
		 * @return a result object or null if none.
		 */
		T doWithVault(VaultSession session);
	}

	/**
	 * An authenticated Vault session. {@link VaultSession} exposes request accessor
	 * methods to be executed in an authenticated context.
	 *
	 * @author Mark Paluch
	 */
	public interface VaultSession {

		/**
		 * Retrieve a resource by GETting from the path, and returns the response as
		 * {@link VaultResponseEntity}.
		 *
		 * @param path the path.
		 * @param responseType the type of the return value
		 * @return the response as entity.
		 * @see VaultResponseEntity
		 */
		<T, S extends T> VaultResponseEntity<S> getForEntity(String path,
				Class<T> responseType);

		/**
		 * Issue a POST request using the given object to the path, and returns the
		 * response as {@link VaultResponseEntity}.
		 *
		 * @param path the path.
		 * @param request the Object to be POSTed, may be {@code null}.
		 * @param responseType the type of the return value
		 * @return the response as entity.
		 * @see VaultResponseEntity
		 */
		<T, S extends T> VaultResponseEntity<S> postForEntity(String path,
				Object request, Class<T> responseType);

		/**
		 * Create a new resource by PUTting the given object to the path, and returns the
		 * response as {@link VaultResponseEntity}.
		 *
		 * @param path the path.
		 * @param request the Object to be PUT.
		 * @param responseType the type of the return value
		 * @return the response as entity.
		 * @see VaultResponseEntity
		 */
		<T, S extends T> VaultResponseEntity<S> putForEntity(String path, Object request,
				Class<T> responseType);

		/**
		 * Delete a resource by DELETEing from the path, and returns the response as
		 * {@link VaultResponseEntity}.
		 *
		 * @param path the path.
		 * @param responseType the type of the return value
		 * @return the response as entity.
		 * @see VaultResponseEntity
		 */
		<T, S extends T> VaultResponseEntity<S> deleteForEntity(String path,
				Class<T> responseType);

		/**
		 * Execute the HTTP method to the given URI template, writing the given request
		 * entity to the request, and returns the response as {@link VaultResponseEntity}.
		 * <p>
		 * URI Template variables are using the given URI variables, if any.
		 *
		 * @param pathTemplate the path template.
		 * @param method the HTTP method (GET, POST, etc).
		 * @param requestEntity the entity (headers and/or body) to write to the request,
		 * may be {@code null}.
		 * @param responseType the type of the return value.
		 * @param uriVariables the variables to expand in the template.
		 * @return the response as entity.
		 */
		<T, S extends T> VaultResponseEntity<S> exchange(String pathTemplate,
				HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType,
				Map<String, ?> uriVariables);

		/**
		 * Execute the HTTP method to the given path template, writing the given request
		 * entity to the request, and returns the response as {@link VaultResponseEntity}.
		 * The given {@link ParameterizedTypeReference} is used to pass generic type
		 * information:
		 *
		 * <pre class="code">
		 * ParameterizedTypeReference&lt;List&lt;MyBean&gt;&gt; myBean = new ParameterizedTypeReference&lt;List&lt;MyBean&gt;&gt;() {
		 * };
		 * ResponseEntity&lt;List&lt;MyBean&gt;&gt; response = session.exchange(&quot;http://example.com&quot;,
		 * 		HttpMethod.GET, null, myBean, null);
		 * </pre>
		 *
		 * @param pathTemplate the path template.
		 * @param method the HTTP method (GET, POST, etc).
		 * @param requestEntity the entity (headers and/or body) to write to the request,
		 * may be {@code null}.
		 * @param responseType the type of the return value.
		 * @param uriVariables the variables to expand in the template.
		 * @return the response as entity.
		 */
		<T, S extends T> VaultResponseEntity<S> exchange(String pathTemplate,
				HttpMethod method, HttpEntity<?> requestEntity,
				ParameterizedTypeReference<T> responseType, Map<String, ?> uriVariables);
	}
}
