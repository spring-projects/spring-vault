/*
 * Copyright 2016-2017 the original author or authors.
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.authentication.SimpleSessionManager;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
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

	private final RestTemplate sessionTemplate;

	private final RestTemplate plainTemplate;

	@Nullable
	private SessionManager sessionManager;

	private final boolean dedicatedSessionManager;

	/**
	 * Create a new {@link VaultTemplate} with a {@link VaultEndpoint} and
	 * {@link ClientAuthentication}.
	 *
	 * @param vaultEndpoint must not be {@literal null}.
	 * @param clientAuthentication must not be {@literal null}.
	 */
	public VaultTemplate(VaultEndpoint vaultEndpoint,
			ClientAuthentication clientAuthentication) {

		Assert.notNull(vaultEndpoint, "VaultEndpoint must not be null");
		Assert.notNull(clientAuthentication, "ClientAuthentication must not be null");

		this.sessionManager = new SimpleSessionManager(clientAuthentication);
		this.dedicatedSessionManager = true;

		ClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

		this.sessionTemplate = createSessionTemplate(vaultEndpoint, requestFactory);
		this.plainTemplate = VaultClients.createRestTemplate(vaultEndpoint,
				requestFactory);
	}

	/**
	 * Create a new {@link VaultTemplate} with a {@link VaultEndpoint},
	 * {@link ClientHttpRequestFactory} and {@link SessionManager}.
	 *
	 * @param vaultEndpoint must not be {@literal null}.
	 * @param clientHttpRequestFactory must not be {@literal null}.
	 * @param sessionManager must not be {@literal null}.
	 */
	public VaultTemplate(VaultEndpoint vaultEndpoint,
			ClientHttpRequestFactory clientHttpRequestFactory,
			SessionManager sessionManager) {

		Assert.notNull(vaultEndpoint, "VaultEndpoint must not be null");
		Assert.notNull(clientHttpRequestFactory,
				"ClientHttpRequestFactory must not be null");
		Assert.notNull(sessionManager, "SessionManager must not be null");

		this.sessionManager = sessionManager;
		this.dedicatedSessionManager = false;

		this.sessionTemplate = createSessionTemplate(vaultEndpoint,
				clientHttpRequestFactory);
		this.plainTemplate = VaultClients.createRestTemplate(vaultEndpoint,
				clientHttpRequestFactory);
	}

	private RestTemplate createSessionTemplate(VaultEndpoint endpoint,
			ClientHttpRequestFactory requestFactory) {

		RestTemplate restTemplate = VaultClients.createRestTemplate(endpoint,
				requestFactory);

		restTemplate.getInterceptors().add(
				(request, body, execution) -> {

					Assert.notNull(sessionManager, "SessionManager must not be null");

					request.getHeaders().add(VaultHttpHeaders.VAULT_TOKEN,
							sessionManager.getSessionToken().getToken());

					return execution.execute(request, body);
				});

		return restTemplate;
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
	@Nullable
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
	@SuppressWarnings("unchecked")
	@Nullable
	public List<String> list(String path) {

		Assert.hasText(path, "Path must not be empty");

		VaultListResponse read = doRead(
				String.format("%s?list=true", path.endsWith("/") ? path : (path + "/")),
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

		Assert.notNull(clientCallback, "Client callback must not be null");

		try {
			return clientCallback.doWithRestOperations(plainTemplate);
		}
		catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e);
		}
	}

	@Override
	public <T> T doWithSession(RestOperationsCallback<T> sessionCallback) {

		Assert.notNull(sessionCallback, "Session callback must not be null");

		try {
			return sessionCallback.doWithRestOperations(sessionTemplate);
		}
		catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e);
		}
	}

	@Nullable
	private <T> T doRead(final String path, final Class<T> responseType) {

		return doWithSession(restOperations -> {

			try {
				return restOperations.getForObject(path, responseType);
			}
			catch (HttpStatusCodeException e) {

				if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
					return null;
				}

				throw VaultResponses.buildException(e, path);
			}
		});
	}

	private static class VaultListResponse extends
			VaultResponseSupport<Map<String, Object>> {
	}
}
