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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.authentication.SimpleSessionManager;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriTemplateHandler;

/**
 * This class encapsulates main Vault interaction. {@link VaultTemplate} will log into
 * Vault on initialization and use the token throughout the whole lifetime.
 *
 * @author Mark Paluch
 * @see VaultClientFactory
 * @see SessionManager
 */
public class VaultTemplate implements InitializingBean, VaultOperations, DisposableBean {

	public static final String VAULT_TOKEN = "X-Vault-Token";

	private VaultClientFactory vaultClientFactory;

	private SessionManager sessionManager;

	private RestTemplate sessionTemplate;

	private RestTemplate plainTemplate;

	private final boolean dedicatedSessionManager;

	/**
	 * Creates a new {@link VaultTemplate} without setting {@link VaultClientFactory} and
	 * {@link SessionManager}.
	 */
	public VaultTemplate() {
		this.dedicatedSessionManager = false;
	}

	/**
	 * Creates a new {@link VaultTemplate} with a {@link VaultClient} and
	 * {@link ClientAuthentication}.
	 *
	 * @param vaultClient must not be {@literal null}.
	 * @param clientAuthentication must not be {@literal null}.
	 */
	public VaultTemplate(VaultClient vaultClient,
			ClientAuthentication clientAuthentication) {

		Assert.notNull(vaultClient, "VaultClientFactory must not be null");
		Assert.notNull(clientAuthentication, "ClientAuthentication must not be null");

		this.vaultClientFactory = new DefaultVaultClientFactory(vaultClient);
		this.sessionManager = new SimpleSessionManager(clientAuthentication);
		this.dedicatedSessionManager = true;

		this.sessionTemplate = createSessionTemplate(vaultClient.getEndpoint(),
				vaultClient.getRestTemplate().getRequestFactory());

		this.plainTemplate = createPlainTemplate(vaultClient.getEndpoint(), vaultClient
				.getRestTemplate().getRequestFactory());
	}

	/**
	 * Creates a new {@link VaultTemplate} with a {@link VaultClientFactory} and
	 * {@link SessionManager}.
	 * 
	 * @param vaultClientFactory must not be {@literal null}.
	 * @param sessionManager must not be {@literal null}.
	 */
	public VaultTemplate(VaultClientFactory vaultClientFactory,
			SessionManager sessionManager) {

		Assert.notNull(vaultClientFactory, "VaultClientFactory must not be null");
		Assert.notNull(sessionManager, "SessionManager must not be null");

		this.vaultClientFactory = vaultClientFactory;
		this.sessionManager = sessionManager;
		this.dedicatedSessionManager = false;

		VaultClient vaultClient = vaultClientFactory.getVaultClient();
		this.sessionTemplate = createSessionTemplate(vaultClientFactory.getVaultClient()
				.getEndpoint(), vaultClientFactory.getVaultClient().getRestTemplate()
				.getRequestFactory());

		this.plainTemplate = createPlainTemplate(vaultClient.getEndpoint(), vaultClient
				.getRestTemplate().getRequestFactory());
	}

	private RestTemplate createSessionTemplate(VaultEndpoint endpoint,
			ClientHttpRequestFactory requestFactory) {

		RestTemplate restTemplate = new RestTemplate(requestFactory);

		restTemplate.setUriTemplateHandler(createDefaultUriTemplateHandler(endpoint));
		restTemplate.getInterceptors().add(new ClientHttpRequestInterceptor() {

			@Override
			public ClientHttpResponse intercept(HttpRequest request, byte[] body,
					ClientHttpRequestExecution execution) throws IOException {

				request.getHeaders().add(VAULT_TOKEN,
						sessionManager.getSessionToken().getToken());

				return execution.execute(request, body);
			}
		});

		return restTemplate;
	}

	private RestTemplate createPlainTemplate(VaultEndpoint endpoint,
			ClientHttpRequestFactory requestFactory) {

		RestTemplate restTemplate = new RestTemplate(requestFactory);

		restTemplate.setUriTemplateHandler(createDefaultUriTemplateHandler(endpoint));

		// Interceptor enforces byte[] serialization so netty can calculate a
		// Content-length header instead of streaming data without a Content-length
		// header.
		restTemplate.getInterceptors().add(new ClientHttpRequestInterceptor() {

			@Override
			public ClientHttpResponse intercept(HttpRequest request, byte[] body,
					ClientHttpRequestExecution execution) throws IOException {
				return execution.execute(request, body);
			}
		});

		return restTemplate;
	}

	private DefaultUriTemplateHandler createDefaultUriTemplateHandler(
			VaultEndpoint endpoint) {
		String baseUrl = String.format("%s://%s:%s/%s/", endpoint.getScheme(),
				endpoint.getHost(), endpoint.getPort(), "v1");

		DefaultUriTemplateHandler defaultUriTemplateHandler = new DefaultUriTemplateHandler();
		defaultUriTemplateHandler.setBaseUrl(baseUrl);
		return defaultUriTemplateHandler;
	}

	/**
	 * Set the {@link SessionManager}.
	 * 
	 * @param sessionManager must not be {@literal null}.
	 */
	public void setSessionManager(SessionManager sessionManager) {

		Assert.notNull(sessionManager, "SessionManager must not be null");

		this.sessionManager = sessionManager;
	}

	@Override
	public void afterPropertiesSet() {

		Assert.notNull(vaultClientFactory, "VaultClientFactory must not be null");
		Assert.notNull(sessionManager, "SessionManager must not be null");
	}

	@Override
	public void destroy() throws Exception {

		if (dedicatedSessionManager && sessionManager instanceof DisposableBean) {
			((DisposableBean) sessionManager).destroy();
		}

	}

	@Override
	public VaultSysOperations opsForSys() {
		return new VaultSysTemplate(this);
	}

	@Override
	public VaultTokenOperations opsForToken() {
		return new VaultTokenTemplate(this);
	}

	@Override
	public VaultTransitOperations opsForTransit() {
		return opsForTransit("transit");
	}

	@Override
	public VaultTransitOperations opsForTransit(String path) {
		return new VaultTransitTemplate(this, path);
	}

	@Override
	public VaultPkiOperations opsForPki() {
		return opsForPki("pki");
	}

	@Override
	public VaultPkiOperations opsForPki(String path) {
		return new VaultPkiTemplate(this, path);
	}

	@Override
	public VaultResponse read(String path) {

		Assert.hasText(path, "Path must not be empty");

		return doRead(path, VaultResponse.class);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> VaultResponseSupport<T> read(final String path, final Class<T> responseType) {

		final ParameterizedTypeReference<VaultResponseSupport<T>> ref = VaultResponses
				.getTypeReference(responseType);

		try {
			ResponseEntity<VaultResponseSupport<T>> exchange = sessionTemplate.exchange(
					path, HttpMethod.GET, null, ref);

			return exchange.getBody();
		}
		catch (HttpStatusCodeException e) {

			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				return null;
			}

			throw VaultResponses.buildException(e, path);
		}
	}

	@Override
	public List<String> list(String path) {

		Assert.hasText(path, "Path must not be empty");

		VaultListResponse read = doRead(
				String.format("%s?list=true", path.endsWith("/") ? path : (path + "/")),
				VaultListResponse.class);
		if (read == null) {
			return Collections.emptyList();
		}

		return (List) read.getData().get("keys");
	}

	@Override
	public VaultResponse write(final String path, final Object body) {

		Assert.hasText(path, "Path must not be empty");

		try {
			return sessionTemplate.postForObject(path, body, VaultResponse.class);
		}
		catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e, path);
		}
	}

	@Override
	public void delete(final String path) {

		Assert.hasText(path, "Path must not be empty");

		try {
			sessionTemplate.delete(path);
		}
		catch (HttpStatusCodeException e) {

			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				return;
			}

			throw VaultResponses.buildException(e, path);
		}
	}

	@Override
	public <T> T doWithVault(RestOperationsCallback<T> clientCallback) {
		try {
			return clientCallback.doWithRestOperations(plainTemplate);
		}
		catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e);
		}
	}

	@Override
	public <T> T doWithSession(RestOperationsCallback<T> sessionCallback) {
		try {
			return sessionCallback.doWithRestOperations(sessionTemplate);
		}
		catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e);
		}
	}

	private <T> T doRead(final String path, final Class<T> responseType) {

		return doWithSession(new RestOperationsCallback<T>() {

			@Override
			public T doWithRestOperations(RestOperations restOperations) {

				try {
					return restOperations.getForObject(path, responseType);
				}
				catch (HttpStatusCodeException e) {

					if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
						return null;
					}

					throw VaultResponses.buildException(e, path);
				}
			}
		});
	}

	private static class VaultListResponse extends
			VaultResponseSupport<Map<String, Object>> {
	}

}
