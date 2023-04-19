/*
 * Copyright 2016-2022 the original author or authors.
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

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.authentication.SimpleSessionManager;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.SimpleVaultEndpointProvider;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.VaultEndpointProvider;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/**
 * This class encapsulates main Vault interaction. {@link VaultTemplate} will log into
 * Vault on initialization and use the token throughout the whole lifetime.
 *
 * @author Mark Paluch
 * @see SessionManager
 */
public class VaultTemplate implements InitializingBean, VaultOperations, DisposableBean {

	private final RestTemplate statelessTemplate;

	private final RestTemplate sessionTemplate;

	@Nullable
	private SessionManager sessionManager;

	private final boolean dedicatedSessionManager;

	/**
	 * Create a new {@link VaultTemplate} with a {@link VaultEndpoint}. This constructor
	 * does not use a {@link ClientAuthentication} mechanism. It is intended for usage
	 * with Vault Agent to inherit Vault Agent's authentication without using the
	 * {@link VaultHttpHeaders#VAULT_TOKEN authentication token header}.
	 * @param vaultEndpoint must not be {@literal null}.
	 * @since 2.2.1
	 */
	public VaultTemplate(VaultEndpoint vaultEndpoint) {
		this(SimpleVaultEndpointProvider.of(vaultEndpoint), new SimpleClientHttpRequestFactory());
	}

	/**
	 * Create a new {@link VaultTemplate} with a {@link VaultEndpoint} and
	 * {@link ClientAuthentication}.
	 * @param vaultEndpoint must not be {@literal null}.
	 * @param clientAuthentication must not be {@literal null}.
	 */
	public VaultTemplate(VaultEndpoint vaultEndpoint, ClientAuthentication clientAuthentication) {

		Assert.notNull(vaultEndpoint, "VaultEndpoint must not be null");
		Assert.notNull(clientAuthentication, "ClientAuthentication must not be null");

		this.sessionManager = new SimpleSessionManager(clientAuthentication);
		this.dedicatedSessionManager = true;

		ClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

		VaultEndpointProvider endpointProvider = SimpleVaultEndpointProvider.of(vaultEndpoint);

		this.statelessTemplate = doCreateRestTemplate(endpointProvider, requestFactory);
		this.sessionTemplate = doCreateSessionTemplate(endpointProvider, requestFactory);
	}

	/**
	 * Create a new {@link VaultTemplate} with a {@link VaultEndpoint}, and
	 * {@link ClientHttpRequestFactory}. This constructor does not use a
	 * {@link ClientAuthentication} mechanism. It is intended for usage with Vault Agent
	 * to inherit Vault Agent's authentication without using the
	 * {@link VaultHttpHeaders#VAULT_TOKEN authentication token header}.
	 * @param vaultEndpoint must not be {@literal null}.
	 * @param clientHttpRequestFactory must not be {@literal null}.
	 * @since 2.2.1
	 */
	public VaultTemplate(VaultEndpoint vaultEndpoint, ClientHttpRequestFactory clientHttpRequestFactory) {
		this(SimpleVaultEndpointProvider.of(vaultEndpoint), clientHttpRequestFactory);
	}

	/**
	 * Create a new {@link VaultTemplate} with a {@link VaultEndpoint},
	 * {@link ClientHttpRequestFactory} and {@link SessionManager}.
	 * @param vaultEndpoint must not be {@literal null}.
	 * @param clientHttpRequestFactory must not be {@literal null}.
	 * @param sessionManager must not be {@literal null}.
	 */
	public VaultTemplate(VaultEndpoint vaultEndpoint, ClientHttpRequestFactory clientHttpRequestFactory,
			SessionManager sessionManager) {
		this(SimpleVaultEndpointProvider.of(vaultEndpoint), clientHttpRequestFactory, sessionManager);
	}

	/**
	 * Create a new {@link VaultTemplate} with a {@link VaultEndpointProvider},
	 * {@link ClientHttpRequestFactory} and {@link SessionManager}. This constructor does
	 * not use a {@link ClientAuthentication} mechanism. It is intended for usage with
	 * Vault Agent to inherit Vault Agent's authentication without using the
	 * {@link VaultHttpHeaders#VAULT_TOKEN authentication token header}.
	 * @param endpointProvider must not be {@literal null}.
	 * @param requestFactory must not be {@literal null}.
	 * @since 2.2.1
	 */
	public VaultTemplate(VaultEndpointProvider endpointProvider, ClientHttpRequestFactory requestFactory) {

		Assert.notNull(endpointProvider, "VaultEndpointProvider must not be null");
		Assert.notNull(requestFactory, "ClientHttpRequestFactory must not be null");

		RestTemplate restTemplate = doCreateRestTemplate(endpointProvider, requestFactory);

		this.sessionManager = NoSessionManager.INSTANCE;
		this.dedicatedSessionManager = false;
		this.statelessTemplate = restTemplate;
		this.sessionTemplate = restTemplate;
	}

	/**
	 * Create a new {@link VaultTemplate} with a {@link VaultEndpointProvider},
	 * {@link ClientHttpRequestFactory} and {@link SessionManager}.
	 * @param endpointProvider must not be {@literal null}.
	 * @param requestFactory must not be {@literal null}.
	 * @param sessionManager must not be {@literal null}.
	 * @since 1.1
	 */
	public VaultTemplate(VaultEndpointProvider endpointProvider, ClientHttpRequestFactory requestFactory,
			SessionManager sessionManager) {

		Assert.notNull(endpointProvider, "VaultEndpointProvider must not be null");
		Assert.notNull(requestFactory, "ClientHttpRequestFactory must not be null");
		Assert.notNull(sessionManager, "SessionManager must not be null");

		this.sessionManager = sessionManager;
		this.dedicatedSessionManager = false;
		this.statelessTemplate = doCreateRestTemplate(endpointProvider, requestFactory);
		this.sessionTemplate = doCreateSessionTemplate(endpointProvider, requestFactory);
	}

	/**
	 * Create a new {@link VaultTemplate} through a {@link RestTemplateBuilder} and
	 * {@link SessionManager}. This constructor does not use a
	 * {@link ClientAuthentication} mechanism. It is intended for usage with Vault Agent
	 * to inherit Vault Agent's authentication without using the
	 * {@link VaultHttpHeaders#VAULT_TOKEN authentication token header}.
	 * @param restTemplateBuilder must not be {@literal null}.
	 * @since 2.2.1
	 */
	public VaultTemplate(RestTemplateBuilder restTemplateBuilder) {

		Assert.notNull(restTemplateBuilder, "RestTemplateBuilder must not be null");

		RestTemplate restTemplate = restTemplateBuilder.build();

		this.sessionManager = NoSessionManager.INSTANCE;
		this.dedicatedSessionManager = false;
		this.statelessTemplate = restTemplate;
		this.sessionTemplate = restTemplate;
	}

	/**
	 * Create a new {@link VaultTemplate} through a {@link RestTemplateBuilder} and
	 * {@link SessionManager}.
	 * @param restTemplateBuilder must not be {@literal null}.
	 * @param sessionManager must not be {@literal null}.
	 * @since 2.2
	 */
	public VaultTemplate(RestTemplateBuilder restTemplateBuilder, SessionManager sessionManager) {

		Assert.notNull(restTemplateBuilder, "RestTemplateBuilder must not be null");
		Assert.notNull(sessionManager, "SessionManager must not be null");

		this.sessionManager = sessionManager;
		this.dedicatedSessionManager = false;

		this.statelessTemplate = restTemplateBuilder.build();
		this.sessionTemplate = restTemplateBuilder.build();
		this.sessionTemplate.getInterceptors().add(getSessionInterceptor());
	}

	/**
	 * Create a {@link RestTemplate} to be used by {@link VaultTemplate} for Vault
	 * communication given {@link VaultEndpointProvider} and
	 * {@link ClientHttpRequestFactory}. {@link VaultEndpointProvider} is used to
	 * contribute host and port details for relative URLs typically used by the Template
	 * API. Subclasses may override this method to customize the {@link RestTemplate}.
	 * @param endpointProvider must not be {@literal null}.
	 * @param requestFactory must not be {@literal null}.
	 * @return the {@link RestTemplate} used for Vault communication.
	 * @since 2.1
	 */
	protected RestTemplate doCreateRestTemplate(VaultEndpointProvider endpointProvider,
			ClientHttpRequestFactory requestFactory) {

		return RestTemplateBuilder.builder().endpointProvider(endpointProvider).requestFactory(requestFactory).build();
	}

	/**
	 * Create a session-bound {@link RestTemplate} to be used by {@link VaultTemplate} for
	 * Vault communication given {@link VaultEndpointProvider} and
	 * {@link ClientHttpRequestFactory} for calls that require an authenticated context.
	 * {@link VaultEndpointProvider} is used to contribute host and port details for
	 * relative URLs typically used by the Template API. Subclasses may override this
	 * method to customize the {@link RestTemplate}.
	 * @param endpointProvider must not be {@literal null}.
	 * @param requestFactory must not be {@literal null}.
	 * @return the {@link RestTemplate} used for Vault communication.
	 * @since 2.1
	 */
	protected RestTemplate doCreateSessionTemplate(VaultEndpointProvider endpointProvider,
			ClientHttpRequestFactory requestFactory) {

		return RestTemplateBuilder.builder()
			.endpointProvider(endpointProvider)
			.requestFactory(requestFactory)
			.customizers(restTemplate -> restTemplate.getInterceptors().add(getSessionInterceptor()))
			.build();
	}

	private ClientHttpRequestInterceptor getSessionInterceptor() {

		return (request, body, execution) -> {

			Assert.notNull(this.sessionManager, "SessionManager must not be null");

			request.getHeaders().add(VaultHttpHeaders.VAULT_TOKEN, this.sessionManager.getSessionToken().getToken());

			return execution.execute(request, body);
		};
	}

	/**
	 * Set the {@link SessionManager}.
	 * @param sessionManager must not be {@literal null}.
	 */
	public void setSessionManager(SessionManager sessionManager) {

		Assert.notNull(sessionManager, "SessionManager must not be null");

		this.sessionManager = sessionManager;
	}

	@Override
	public void afterPropertiesSet() {
		Assert.notNull(this.sessionManager, "SessionManager must not be null");
	}

	@Override
	public void destroy() throws Exception {

		if (this.dedicatedSessionManager && this.sessionManager instanceof DisposableBean) {
			((DisposableBean) this.sessionManager).destroy();
		}
	}

	@Override
	public VaultKeyValueOperations opsForKeyValue(String path, KeyValueBackend apiVersion) {

		switch (apiVersion) {
			case KV_1:
				return new VaultKeyValue1Template(this, path);
			case KV_2:
				return new VaultKeyValue2Template(this, path);
		}

		throw new UnsupportedOperationException(
				String.format("Key/Value backend version %s not supported", apiVersion));

	}

	@Override
	public VaultVersionedKeyValueOperations opsForVersionedKeyValue(String path) {
		return new VaultVersionedKeyValueTemplate(this, path);
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
	public VaultSysOperations opsForSys() {
		return new VaultSysTemplate(this);
	}

	@Override
	public VaultTokenOperations opsForToken() {
		return new VaultTokenTemplate(this);
	}

	@Override
	public VaultTransformOperations opsForTransform() {
		return opsForTransform("transform");
	}

	@Override
	public VaultTransformOperations opsForTransform(String path) {
		return new VaultTransformTemplate(this, path);
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
	public VaultWrappingOperations opsForWrapping() {
		return new VaultWrappingTemplate(this);
	}

	@Override
	public VaultResponse read(String path) {

		Assert.hasText(path, "Path must not be empty");

		return doRead(path, VaultResponse.class);
	}

	@Override
	@Nullable
	public <T> VaultResponseSupport<T> read(String path, Class<T> responseType) {

		ParameterizedTypeReference<VaultResponseSupport<T>> ref = VaultResponses.getTypeReference(responseType);

		return doWithSession(restOperations -> {

			try {
				ResponseEntity<VaultResponseSupport<T>> exchange = restOperations.exchange(path, HttpMethod.GET, null,
						ref);

				return exchange.getBody();
			}
			catch (HttpStatusCodeException e) {

				if (HttpStatusUtil.isNotFound(e.getStatusCode())) {
					return null;
				}

				throw VaultResponses.buildException(e, path);
			}
		});
	}

	@Override
	@SuppressWarnings("unchecked")
	@Nullable
	public List<String> list(String path) {

		Assert.hasText(path, "Path must not be empty");

		VaultListResponse read = doRead(String.format("%s?list=true", path.endsWith("/") ? path : (path + "/")),
				VaultListResponse.class);
		if (read == null) {
			return Collections.emptyList();
		}

		return (List<String>) read.getRequiredData().get("keys");
	}

	@Override
	@Nullable
	public VaultResponse write(String path, @Nullable Object body) {

		Assert.hasText(path, "Path must not be empty");

		return doWithSession(restOperations -> restOperations.postForObject(path, body, VaultResponse.class));
	}

	@Override
	public void delete(String path) {

		Assert.hasText(path, "Path must not be empty");

		doWithSession(restOperations -> {

			try {
				restOperations.delete(path);
			}
			catch (HttpStatusCodeException e) {

				if (HttpStatusUtil.isNotFound(e.getStatusCode())) {
					return null;
				}

				throw VaultResponses.buildException(e, path);
			}

			return null;
		});
	}

	@Override
	public <T> T doWithVault(RestOperationsCallback<T> clientCallback) {

		Assert.notNull(clientCallback, "Client callback must not be null");

		try {
			return clientCallback.doWithRestOperations(this.statelessTemplate);
		}
		catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e);
		}
	}

	@Override
	public <T> T doWithSession(RestOperationsCallback<T> sessionCallback) {

		Assert.notNull(sessionCallback, "Session callback must not be null");

		try {
			return sessionCallback.doWithRestOperations(this.sessionTemplate);
		}
		catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e);
		}
	}

	@Nullable
	private <T> T doRead(String path, Class<T> responseType) {

		return doWithSession(restOperations -> {

			try {
				return restOperations.getForObject(path, responseType);
			}
			catch (HttpStatusCodeException e) {

				if (HttpStatusUtil.isNotFound(e.getStatusCode())) {
					return null;
				}

				throw VaultResponses.buildException(e, path);
			}
		});
	}

	private enum NoSessionManager implements SessionManager {

		INSTANCE;

		@Override
		public VaultToken getSessionToken() {
			throw new UnsupportedOperationException();
		}

	}

}
