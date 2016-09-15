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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.DefaultSessionManager;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.client.VaultAccessor.RestTemplateCallback;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultException;
import org.springframework.vault.client.VaultResponseEntity;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * This class encapsulates main Vault interaction. {@link VaultTemplate} will log into Vault on initialization and use
 * the token throughout the whole lifetime.
 *
 * @author Mark Paluch
 * @see VaultClientFactory
 * @see SessionManager
 */
public class VaultTemplate implements InitializingBean, VaultOperations {

	private VaultClientFactory vaultClientFactory;

	private SessionManager sessionManager;

	/**
	 * Creates a new {@link VaultTemplate} without setting {@link VaultClientFactory} and {@link SessionManager}.
	 */
	public VaultTemplate() {}

	/**
	 * Creates a new {@link VaultTemplate} with a {@link VaultClient} and {@link ClientAuthentication}.
	 *
	 * @param vaultClient must not be {@literal null}.
	 * @param clientAuthentication must not be {@literal null}.
	 */
	public VaultTemplate(VaultClient vaultClient, ClientAuthentication clientAuthentication) {

		Assert.notNull(vaultClient, "VaultClientFactory must not be null");
		Assert.notNull(clientAuthentication, "ClientAuthentication must not be null");

		this.vaultClientFactory = new DefaultVaultClientFactory(vaultClient);
		this.sessionManager = new DefaultSessionManager(clientAuthentication);
	}

	/**
	 * Creates a new {@link VaultTemplate} with a {@link VaultClientFactory} and {@link SessionManager}.
	 * 
	 * @param vaultClientFactory must not be {@literal null}.
	 * @param sessionManager must not be {@literal null}.
	 */
	public VaultTemplate(VaultClientFactory vaultClientFactory, SessionManager sessionManager) {

		Assert.notNull(vaultClientFactory, "VaultClientFactory must not be null");
		Assert.notNull(sessionManager, "SessionManager must not be null");

		this.vaultClientFactory = vaultClientFactory;
		this.sessionManager = sessionManager;
	}

	/**
	 * Set the {@link VaultClientFactory}.
	 * 
	 * @param vaultClientFactory must not be {@literal null}.
	 */
	public void setVaultClientFactory(VaultClientFactory vaultClientFactory) {

		Assert.notNull(vaultClientFactory, "VaultClientFactory must not be null");

		this.vaultClientFactory = vaultClientFactory;
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
	public <T> T doWithVault(ClientCallback<T> clientCallback) {

		Assert.notNull(clientCallback, "ClientCallback must not be null!");
		Assert.state(vaultClientFactory != null, "VaultClientFactory must not be null");
		Assert.state(sessionManager != null, "SessionManager must not be null");

		return clientCallback.doWithVault(vaultClientFactory.getVaultClient());
	}

	@Override
	public <T> T doWithVault(SessionCallback<T> sessionCallback) {

		Assert.notNull(sessionCallback, "SessionCallback must not be null!");
		Assert.state(vaultClientFactory != null, "VaultClientFactory must not be null");
		Assert.state(sessionManager != null, "SessionManager must not be null");

		VaultClient vaultClient = vaultClientFactory.getVaultClient();

		return sessionCallback.doWithVault(new DefaultVaultSession(sessionManager, vaultClient));
	}

	@Override
	public <T> T doWithRestTemplate(String pathTemplate, Map<String, ?> uriVariables, RestTemplateCallback<T> callback) {

		Assert.notNull(callback, "RestTemplateCallback must not be null!");
		Assert.state(vaultClientFactory != null, "VaultClientFactory must not be null");

		return vaultClientFactory.getVaultClient().doWithRestTemplate(pathTemplate, uriVariables, callback);
	}

	@Override
	public VaultResponse read(String path) {

		Assert.hasText(path, "Path must not be empty");

		return doRead(path, VaultResponse.class);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> VaultResponseSupport<T> read(final String path, final Class<T> responseType) {

		final ParameterizedTypeReference<VaultResponseSupport<T>> ref = getTypeReference(responseType);

		return doWithVault(new SessionCallback<VaultResponseSupport<T>>() {

			@Override
			public VaultResponseSupport<T> doWithVault(VaultSession session) {

				VaultResponseEntity<VaultResponseSupport<T>> entity = session.exchange(path, HttpMethod.GET, null, ref, null);

				if (entity.isSuccessful() && entity.hasBody()) {
					return entity.getBody();
				}

				if (entity.getStatusCode() == HttpStatus.NOT_FOUND) {
					return null;
				}

				throw new VaultException(buildExceptionMessage(entity));
			}
		});
	}

	@Override
	public List<String> list(String path) {

		Assert.hasText(path, "Path must not be empty");

		VaultListResponse read = doRead(String.format("%s?list=true", path.endsWith("/") ? path : (path + "/")),
				VaultListResponse.class);
		if (read == null) {
			return Collections.emptyList();
		}

		return (List) read.getData().get("keys");
	}

	@Override
	public VaultResponse write(final String path, final Object body) {

		Assert.hasText(path, "Path must not be empty");

		return doWithVault(new SessionCallback<VaultResponse>() {

			@Override
			public VaultResponse doWithVault(VaultSession session) {
				VaultResponseEntity<VaultResponse> entity = session.postForEntity(path, body, VaultResponse.class);

				if (entity.isSuccessful()) {
					if (entity.hasBody()) {
						return entity.getBody();
					}
					return null;
				}

				throw new VaultException(buildExceptionMessage(entity));
			}
		});
	}

	@Override
	public void delete(final String path) {

		Assert.hasText(path, "Path must not be empty");

		doWithVault(new SessionCallback<VaultResponse>() {

			@Override
			public VaultResponse doWithVault(VaultSession session) {
				VaultResponseEntity<VaultResponse> entity = session.deleteForEntity(path, VaultResponse.class);

				if (entity.isSuccessful()) {
					return null;
				}

				throw new VaultException(buildExceptionMessage(entity));
			}
		});
	}

	private <T> ParameterizedTypeReference<VaultResponseSupport<T>> getTypeReference(final Class<T> responseType) {
		final Type supportType = new ParameterizedType() {

			@Override
			public Type[] getActualTypeArguments() {
				return new Type[] { responseType };
			}

			@Override
			public Type getRawType() {
				return VaultResponseSupport.class;
			}

			@Override
			public Type getOwnerType() {
				return VaultResponseSupport.class;
			}
		};

		return new ParameterizedTypeReference<VaultResponseSupport<T>>() {
			@Override
			public Type getType() {
				return supportType;
			}
		};
	}

	private <T> T doRead(final String path, final Class<T> responseType) {

		return doWithVault(new SessionCallback<T>() {

			@Override
			public T doWithVault(VaultSession session) {

				VaultResponseEntity<T> entity = session.getForEntity(path, responseType);

				if (entity.isSuccessful() && entity.hasBody()) {
					return entity.getBody();
				}

				if (entity.getStatusCode() == HttpStatus.NOT_FOUND) {
					return null;
				}

				throw new VaultException(buildExceptionMessage(entity));
			}
		});
	}

	private static String buildExceptionMessage(VaultResponseEntity<?> response) {

		if (StringUtils.hasText(response.getMessage())) {
			return String.format("Status %s URI %s: %s", response.getStatusCode(), response.getUri(), response.getMessage());
		}

		return String.format("Status %s URI %s", response.getStatusCode(), response.getUri());
	}

	private static class DefaultVaultSession implements VaultSession {

		private final SessionManager sessionManager;

		private final VaultClient vaultClient;

		DefaultVaultSession(SessionManager sessionManager, VaultClient vaultClient) {
			this.sessionManager = sessionManager;
			this.vaultClient = vaultClient;
		}

		@Override
		public <T, S extends T> VaultResponseEntity<S> getForEntity(String path, Class<T> responseType) {
			return vaultClient.getForEntity(path, sessionManager.getSessionToken(), responseType);
		}

		@Override
		public <T, S extends T> VaultResponseEntity<S> putForEntity(String path, Object request, Class<T> responseType) {
			return vaultClient.putForEntity(path, sessionManager.getSessionToken(), request, responseType);
		}

		@Override
		public <T, S extends T> VaultResponseEntity<S> postForEntity(String path, Object request, Class<T> responseType) {
			return vaultClient.postForEntity(path, sessionManager.getSessionToken(), request, responseType);
		}

		@Override
		public <T, S extends T> VaultResponseEntity<S> deleteForEntity(String path, Class<T> responseType) {
			return vaultClient.deleteForEntity(path, sessionManager.getSessionToken(), responseType);
		}

		@Override
		public <T, S extends T> VaultResponseEntity<S> exchange(String pathTemplate, HttpMethod method,
				HttpEntity<?> requestEntity, Class<T> responseType, Map<String, ?> uriVariables) {

			HttpEntity<?> requestEntityToUse = getHttpEntity(requestEntity);
			return vaultClient.exchange(pathTemplate, method, requestEntityToUse, responseType, uriVariables);
		}

		@Override
		public <T, S extends T> VaultResponseEntity<S> exchange(String pathTemplate, HttpMethod method,
				HttpEntity<?> requestEntity, ParameterizedTypeReference<T> responseType, Map<String, ?> uriVariables) {

			HttpEntity<?> requestEntityToUse = getHttpEntity(requestEntity);
			return vaultClient.exchange(pathTemplate, method, requestEntityToUse, responseType, uriVariables);
		}

		private HttpEntity<?> getHttpEntity(HttpEntity<?> requestEntity) {
			HttpHeaders httpHeaders = VaultClient.createHeaders(sessionManager.getSessionToken());

			HttpEntity<?> requestEntityToUse = requestEntity;
			if (requestEntityToUse != null) {
				requestEntityToUse = new HttpEntity<Object>(requestEntityToUse.getBody(), httpHeaders);
			} else {
				requestEntityToUse = new HttpEntity<Object>(httpHeaders);
			}
			return requestEntityToUse;
		}
	}

	private static class VaultListResponse extends VaultResponseSupport<Map<String, Object>> {}
}
