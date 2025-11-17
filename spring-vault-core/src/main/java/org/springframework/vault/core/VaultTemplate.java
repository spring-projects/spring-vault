/*
 * Copyright 2016-2025 the original author or authors.
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.authentication.SimpleSessionManager;
import org.springframework.vault.client.RestClientBuilder;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.SimpleVaultEndpointProvider;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.VaultEndpointProvider;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/**
 * This class encapsulates main Vault interaction. {@code VaultTemplate} will
 * log into Vault on initialization and use the token throughout the whole
 * lifetime. This is the main entry point to interact with Vault in an
 * authenticated and unauthenticated context.
 * <p>{@code VaultTemplate} allows execution of callback methods. Callbacks can
 * execute requests within a {@link #doWithSession(RestOperationsCallback)
 * session context} and the {@link #doWithVault(RestOperationsCallback) without
 * a session}.
 * <p>Paths used in this interface (and interfaces accessible from here) are
 * considered relative to the {@link VaultEndpoint}. Paths that are
 * fully-qualified URI's can be used to access Vault cluster members in an
 * authenticated context. To prevent unwanted full URI access, make sure to
 * sanitize paths before passing them to this interface.
 *
 * @author Mark Paluch
 * @see SessionManager
 */
public class VaultTemplate implements InitializingBean, VaultOperations, DisposableBean {

	private final RestOperations statelessTemplate;

	private final VaultClient statelessClient;

	private final RestOperations sessionTemplate;

	private final VaultClient sessionClient = new SessionVaultClient();

	private SessionManager sessionManager = NoSessionManager.INSTANCE;

	private final boolean dedicatedSessionManager;


	/**
	 * Create a new {@link VaultTemplate} with a {@link VaultEndpoint}. This
	 * constructor does not use a {@link ClientAuthentication} mechanism. It is
	 * intended for usage with Vault Agent to inherit Vault Agent's authentication
	 * without using the {@link VaultHttpHeaders#VAULT_TOKEN authentication token
	 * header}.
	 * @param vaultEndpoint must not be {@literal null}.
	 * @since 2.2.1
	 */
	public VaultTemplate(VaultEndpoint vaultEndpoint) {
		this(SimpleVaultEndpointProvider.of(vaultEndpoint), new JdkClientHttpRequestFactory());
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
		this.statelessClient = createVaultClient(this.statelessTemplate);
		this.sessionTemplate = doCreateSessionTemplate(endpointProvider, requestFactory);
	}

	/**
	 * Create a new {@link VaultTemplate} with a {@link VaultClient}.
	 * @param client must not be {@literal null}.
	 * @since 4.1
	 */
	public VaultTemplate(VaultClient client) {

		Assert.notNull(client, "VaultClient must not be null");

		this.dedicatedSessionManager = true;

		this.statelessTemplate = new RestClientOperationsWrapper(client);
		this.statelessClient = client;
		this.sessionTemplate = this.statelessTemplate;
	}

	/**
	 * Create a new {@link VaultTemplate} with a {@link VaultClient} and
	 * {@link SessionManager}.
	 * @param client must not be {@literal null}.
	 * @param sessionManager must not be {@literal null}.
	 * @since 4.1
	 */
	public VaultTemplate(VaultClient client, SessionManager sessionManager) {

		Assert.notNull(client, "VaultEndpoint must not be null");
		Assert.notNull(sessionManager, "SessionManager must not be null");

		this.sessionManager = sessionManager;
		this.dedicatedSessionManager = false;

		this.statelessTemplate = new RestClientOperationsWrapper(client);
		this.statelessClient = client;
		this.sessionTemplate = new RestClientOperationsWrapper(this.sessionClient);
	}

	/**
	 * Create a new {@link VaultTemplate} with a {@link VaultEndpoint}, and
	 * {@link ClientHttpRequestFactory}. This constructor does not use a
	 * {@link ClientAuthentication} mechanism. It is intended for usage with Vault
	 * Agent to inherit Vault Agent's authentication without using the
	 * {@link VaultHttpHeaders#VAULT_TOKEN authentication token header}.
	 * @param vaultEndpoint must not be {@literal null}.
	 * @param clientHttpRequestFactory must not be {@literal null}.
	 * @since 2.2.1
	 * @deprecated since 4.1 in favor of a revised {@link VaultClient}-based constructor.
	 */
	@Deprecated(since = "4.1")
	public VaultTemplate(VaultEndpoint vaultEndpoint, ClientHttpRequestFactory clientHttpRequestFactory) {
		this(SimpleVaultEndpointProvider.of(vaultEndpoint), clientHttpRequestFactory);
	}

	/**
	 * Create a new {@link VaultTemplate} with a {@link VaultEndpoint},
	 * {@link ClientHttpRequestFactory} and {@link SessionManager}.
	 * @param vaultEndpoint must not be {@literal null}.
	 * @param clientHttpRequestFactory must not be {@literal null}.
	 * @param sessionManager must not be {@literal null}.
	 * @deprecated since 4.1 in favor of a revised {@link VaultClient}-based constructor.
	 */
	@Deprecated(since = "4.1")
	public VaultTemplate(VaultEndpoint vaultEndpoint, ClientHttpRequestFactory clientHttpRequestFactory,
			SessionManager sessionManager) {
		this(SimpleVaultEndpointProvider.of(vaultEndpoint), clientHttpRequestFactory, sessionManager);
	}

	/**
	 * Create a new {@link VaultTemplate} with a {@link VaultEndpointProvider},
	 * {@link ClientHttpRequestFactory} and {@link SessionManager}. This constructor
	 * does not use a {@link ClientAuthentication} mechanism. It is intended for
	 * usage with Vault Agent to inherit Vault Agent's authentication without using
	 * the {@link VaultHttpHeaders#VAULT_TOKEN authentication token header}.
	 * @param endpointProvider must not be {@literal null}.
	 * @param requestFactory must not be {@literal null}.
	 * @since 2.2.1
	 * @deprecated since 4.1 in favor of a revised {@link VaultClient}-based constructor.
	 */
	@Deprecated(since = "4.1")
	public VaultTemplate(VaultEndpointProvider endpointProvider, ClientHttpRequestFactory requestFactory) {
		Assert.notNull(endpointProvider, "VaultEndpointProvider must not be null");
		Assert.notNull(requestFactory, "ClientHttpRequestFactory must not be null");
		RestTemplate restTemplate = doCreateRestTemplate(endpointProvider, requestFactory);
		this.sessionManager = NoSessionManager.INSTANCE;
		this.dedicatedSessionManager = false;
		this.statelessTemplate = restTemplate;
		this.statelessClient = VaultClient.builder(restTemplate).build();
		this.sessionTemplate = restTemplate;
	}

	/**
	 * Create a new {@link VaultTemplate} with a {@link VaultEndpointProvider},
	 * {@link ClientHttpRequestFactory} and {@link SessionManager}.
	 * @param endpointProvider must not be {@literal null}.
	 * @param requestFactory must not be {@literal null}.
	 * @param sessionManager must not be {@literal null}.
	 * @since 1.1
	 * @deprecated since 4.1 in favor of a revised {@link VaultClient}-based constructor.
	 */
	@Deprecated(since = "4.1")
	public VaultTemplate(VaultEndpointProvider endpointProvider, ClientHttpRequestFactory requestFactory,
			SessionManager sessionManager) {
		Assert.notNull(endpointProvider, "VaultEndpointProvider must not be null");
		Assert.notNull(requestFactory, "ClientHttpRequestFactory must not be null");
		Assert.notNull(sessionManager, "SessionManager must not be null");
		this.sessionManager = sessionManager;
		this.dedicatedSessionManager = false;

		this.statelessTemplate = doCreateRestTemplate(endpointProvider, requestFactory);
		this.statelessClient = createVaultClient(this.statelessTemplate);
		this.sessionTemplate = doCreateSessionTemplate(endpointProvider, requestFactory);
	}

	/**
	 * Create a new {@link VaultTemplate} through a {@link RestTemplateBuilder}.
	 * This constructor does not use a {@link ClientAuthentication} mechanism. It is
	 * intended for usage with Vault Agent to inherit Vault Agent's authentication
	 * without using the {@link VaultHttpHeaders#VAULT_TOKEN authentication token
	 * header}.
	 * @param restTemplateBuilder must not be {@literal null}.
	 * @since 2.2.1
	 */
	public VaultTemplate(RestTemplateBuilder restTemplateBuilder) {
		Assert.notNull(restTemplateBuilder, "RestTemplateBuilder must not be null");
		RestTemplate restTemplate = restTemplateBuilder.build();
		this.dedicatedSessionManager = false;
		this.statelessTemplate = restTemplate;
		this.sessionTemplate = restTemplate;
		this.statelessClient = createVaultClient(restTemplate);
	}

	/**
	 * Create a new {@link VaultTemplate} through a {@link RestClientBuilder}. This
	 * constructor does not use a {@link ClientAuthentication} mechanism. It is
	 * intended for usage with Vault Agent to inherit Vault Agent's authentication
	 * without using the {@link VaultHttpHeaders#VAULT_TOKEN authentication token
	 * header}.
	 * @param restClientBuilder must not be {@literal null}.
	 * @since 4.0
	 */
	public VaultTemplate(RestClientBuilder restClientBuilder) {
		Assert.notNull(restClientBuilder, "RestClientBuilder must not be null");
		RestTemplate restTemplate = RestTemplateBuilder.builder(restClientBuilder).build();
		this.dedicatedSessionManager = false;
		this.statelessTemplate = restTemplate;
		this.sessionTemplate = restTemplate;
		this.statelessClient = VaultClient.builder(restTemplate).build();
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
		RestTemplate statelessTemplate = restTemplateBuilder.build();
		this.statelessTemplate = restTemplateBuilder.build();
		this.statelessClient = createVaultClient(statelessTemplate);
		RestTemplate sessionTemplate = restTemplateBuilder.build();
		sessionTemplate.getInterceptors().add(getSessionInterceptor());
		this.sessionTemplate = sessionTemplate;
	}

	/**
	 * Create a new {@link VaultTemplate} through a {@link RestClientBuilder} and
	 * {@link SessionManager}.
	 * @param restClientBuilder must not be {@literal null}.
	 * @param sessionManager must not be {@literal null}.
	 * @since 4.0
	 */
	public VaultTemplate(RestClientBuilder restClientBuilder, SessionManager sessionManager) {
		Assert.notNull(restClientBuilder, "RestTemplateBuilder must not be null");
		Assert.notNull(sessionManager, "SessionManager must not be null");
		this.sessionManager = sessionManager;
		this.dedicatedSessionManager = false;
		RestTemplateBuilder templateBuilder = RestTemplateBuilder.builder(restClientBuilder);
		this.statelessTemplate = templateBuilder.build();
		this.statelessClient = createVaultClient(this.statelessTemplate);
		ClientHttpRequestInterceptor sessionInterceptor = getSessionInterceptor();
		this.sessionTemplate = templateBuilder.customizers(restTemplate -> {
			restTemplate.getInterceptors().add(sessionInterceptor);
		}).build();
	}

	private VaultTemplate(RestTemplate statelessTemplate, RestTemplate sessionTemplate) {
		this.dedicatedSessionManager = false;
		this.statelessTemplate = statelessTemplate;
		this.statelessClient = createVaultClient(statelessTemplate);
		this.sessionTemplate = sessionTemplate;
	}

	private static VaultClient createVaultClient(RestOperations restOperations) {

		if (restOperations instanceof RestClientOperationsWrapper wrapper) {
			return wrapper.vaultClient();
		}

		return VaultClient.builder((RestTemplate) restOperations).build();
	}

	/**
	 * Create or obtain a {@link VaultTemplate} from {@link VaultOperations}.
	 */
	static VaultTemplate from(VaultOperations operations) {

		if (operations instanceof VaultTemplate) {
			return (VaultTemplate) operations;
		}

		return operations.doWithVault(ops -> {
			return operations.doWithSession(sessionOps -> {
				return new VaultTemplate((RestTemplate) ops, (RestTemplate) sessionOps);
			});
		});
	}


	/**
	 * Create a {@link RestTemplate} to be used by {@link VaultTemplate} for Vault
	 * communication given {@link VaultEndpointProvider} and
	 * {@link ClientHttpRequestFactory}. {@link VaultEndpointProvider} is used to
	 * contribute host and port details for relative URLs typically used by the
	 * Template API. Subclasses may override this method to customize the
	 * {@link RestTemplate}.
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
	 * Create a {@link RestClient} to be used by {@link VaultTemplate} for Vault
	 * communication given {@link VaultEndpointProvider} and
	 * {@link ClientHttpRequestFactory}. {@link VaultEndpointProvider} is used to
	 * contribute host and port details for relative URLs typically used by the
	 * client API. Subclasses may override this method to customize the
	 * {@link RestClient}.
	 * @param endpointProvider must not be {@literal null}.
	 * @param requestFactory must not be {@literal null}.
	 * @return the {@link RestClient} used for Vault communication.
	 * @since 4.0
	 */
	protected RestClient doCreateRestClient(VaultEndpointProvider endpointProvider,
			ClientHttpRequestFactory requestFactory) {
		return RestClientBuilder.builder().endpointProvider(endpointProvider).requestFactory(requestFactory).build();
	}

	/**
	 * Create a session-bound {@link RestTemplate} to be used by
	 * {@link VaultTemplate} for Vault communication given
	 * {@link VaultEndpointProvider} and {@link ClientHttpRequestFactory} for calls
	 * that require an authenticated context. {@link VaultEndpointProvider} is used
	 * to contribute host and port details for relative URLs typically used by the
	 * Template API. Subclasses may override this method to customize the
	 * {@link RestTemplate}.
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

	/**
	 * Create a session-bound {@link RestClient} to be used by {@link VaultTemplate}
	 * for Vault communication given {@link VaultEndpointProvider} and
	 * {@link ClientHttpRequestFactory} for calls that require an authenticated
	 * context. {@link VaultEndpointProvider} is used to contribute host and port
	 * details for relative URLs typically used by the Template API. Subclasses may
	 * override this method to customize the {@link RestClient}.
	 * @param endpointProvider must not be {@literal null}.
	 * @param requestFactory must not be {@literal null}.
	 * @return the {@link RestClient} used for Vault communication.
	 * @since 4.0
	 */
	protected RestClient doCreateSessionClient(VaultEndpointProvider endpointProvider,
			ClientHttpRequestFactory requestFactory) {
		return RestClientBuilder.builder()
				.endpointProvider(endpointProvider)
				.requestFactory(requestFactory)
				.customizers(builder -> builder.requestInterceptor(getSessionInterceptor()))
				.build();
	}

	private ClientHttpRequestInterceptor getSessionInterceptor() {
		return (request, body, execution) -> {

			HttpHeaders headers = request.getHeaders();

			if (!headers.containsHeader(VaultHttpHeaders.VAULT_TOKEN)) {
				Assert.notNull(this.sessionManager, "SessionManager must not be null");
				headers.add(VaultHttpHeaders.VAULT_TOKEN, this.sessionManager.getSessionToken().getToken());
			}
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

		return switch (apiVersion) {
		case KV_1 -> new VaultKeyValue1Template(this, path);
		case KV_2 -> new VaultKeyValue2Template(this, path);
		};

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
	public @Nullable VaultResponse read(String path) {
		Assert.hasText(path, "Path must not be empty");
		return doRead(path, VaultResponse.class);
	}

	@Override
	@SuppressWarnings("NullAway")
	public <T> @Nullable VaultResponseSupport<T> read(String path, Class<T> responseType) {
		ParameterizedTypeReference<VaultResponseSupport<T>> ref = VaultResponses.getTypeReference(responseType);
		return doWithSessionClient(client -> {

			ResponseEntity<VaultResponseSupport<T>> entity = client.get()
				.path(path)
				.retrieve()
				.onStatus(HttpStatusUtil::isNotFound, HttpStatusUtil.proceed())
				.toEntity(ref);

			if (HttpStatusUtil.isNotFound(entity.getStatusCode())) {
				return null;

				}
			return entity.getBody();
		});
	}

	@Override
	@SuppressWarnings("unchecked")
	public @Nullable List<String> list(String path) {
		Assert.hasText(path, "Path must not be empty");
		VaultListResponse read = doRead("%s?list=true".formatted(path.endsWith("/") ? path : (path + "/")),
				VaultListResponse.class);
		if (read == null) {
			return Collections.emptyList();
		}
		return (List<String>) read.getRequiredData().get("keys");
	}

	@Override
	@SuppressWarnings("NullAway")
	public @Nullable VaultResponse write(String path, @Nullable Object body) {
		Assert.hasText(path, "Path must not be empty");
		return doWithSessionClient(client -> {
			VaultClient.RequestBodySpec spec = client.post().path(path);
			if (body != null) {
				spec = spec.body(body);
			}
			return spec.retrieve().body(VaultResponse.class);
		});
	}

	@Override
	@SuppressWarnings("NullAway")
	public void delete(String path) {
		Assert.hasText(path, "Path must not be empty");

		doWithSessionClient((VaultClientCallback<@Nullable Void>) client -> {

			client.delete()
				.path(path)
				.retrieve()
				.onStatus(HttpStatusUtil::isNotFound, HttpStatusUtil.proceed())
				.toBodilessEntity();

			return null;
		});
	}

	@Override
	public <T extends @Nullable Object> T doWithVault(RestOperationsCallback<T> clientCallback) {
		Assert.notNull(clientCallback, "Client callback must not be null");
		try {
			return clientCallback.doWithRestOperations(this.statelessTemplate);
		} catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e);
		}
	}

	<T extends @Nullable Object> T doWithVaultClient(VaultClientCallback<T> clientCallback)
			throws VaultException, RestClientException {
		Assert.notNull(clientCallback, "Client callback must not be null");

		return clientCallback.doWithVaultClient(this.statelessClient);
	}

	@Override
	public <T extends @Nullable Object> T doWithSession(RestOperationsCallback<T> sessionCallback) {
		Assert.notNull(sessionCallback, "Session callback must not be null");
		try {
			return sessionCallback.doWithRestOperations(this.sessionTemplate);
		} catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e);
		}
	}

	<T extends @Nullable Object> T doWithSessionClient(VaultClientCallback<T> sessionCallback)
			throws VaultException, RestClientException {
		Assert.notNull(sessionCallback, "Session callback must not be null");

		return sessionCallback.doWithVaultClient(this.sessionClient);
	}

	@SuppressWarnings("NullAway")
	private <T> @Nullable T doRead(String path, Class<T> responseType) {
		return doWithSessionClient((client) -> {
			ResponseEntity<T> entity = client.get()
				.path(path)
				.retrieve()
				.onStatus(HttpStatusUtil::isNotFound, HttpStatusUtil.proceed())
				.toEntity(responseType);

			if (HttpStatusUtil.isNotFound(entity.getStatusCode())) {
				return null;

				}
			return entity.getBody();
		});
	}


	private enum NoSessionManager implements SessionManager {

		INSTANCE;

		@Override
		public VaultToken getSessionToken() {
			throw new UnsupportedOperationException();
		}

	}

	/**
	 * Session-bound {@link VaultClient} implementation.
	 */
	class SessionVaultClient implements VaultClient {

		@Override
		public RequestHeadersPathSpec<?> get() {

			if (sessionManager == NoSessionManager.INSTANCE) {
				return statelessClient.get();
			}

			return new SessionRequestHeadersPathSpec(sessionManager.getSessionToken(), statelessClient.get());
		}

		@Override
		public RequestHeadersBodyPathSpec post() {

			if (sessionManager == NoSessionManager.INSTANCE) {
				return statelessClient.post();
			}

			return new SessionRequestBodyHeadersPathSpec(sessionManager.getSessionToken(), statelessClient.post());
		}

		@Override
		public RequestHeadersBodyPathSpec put() {

			if (sessionManager == NoSessionManager.INSTANCE) {
				return statelessClient.put();
			}

			return new SessionRequestBodyHeadersPathSpec(sessionManager.getSessionToken(), statelessClient.put());
		}

		@Override
		public RequestHeadersPathSpec<?> delete() {

			if (sessionManager == NoSessionManager.INSTANCE) {
				return statelessClient.delete();
			}

			return new SessionRequestHeadersPathSpec(sessionManager.getSessionToken(), statelessClient.delete());
		}

		@Override
		public RequestHeadersBodyPathSpec method(HttpMethod method) {

			if (sessionManager == NoSessionManager.INSTANCE) {
				return statelessClient.method(method);
			}

			return new SessionRequestBodyHeadersPathSpec(sessionManager.getSessionToken(),
					statelessClient.method(method));
		}

		@Override
		public Builder mutate() {
			return statelessClient.mutate();
		}

	}

	static class SessionRequestHeadersPathSpec
			implements VaultClient.RequestHeadersPathSpec<SessionRequestHeadersPathSpec> {

		private final VaultToken token;

		private final VaultClient.RequestHeadersPathSpec<?> spec;

		SessionRequestHeadersPathSpec(VaultToken token, VaultClient.RequestHeadersPathSpec<?> spec) {
			this.token = token;
			this.spec = spec;
		}

		@Override
		public SessionRequestHeadersPathSpec path(String path, @Nullable Object... pathVariables) {
			spec.path(path, pathVariables);
			return this;
		}

		@Override
		public SessionRequestHeadersPathSpec path(String path, Map<String, ?> pathVariables) {
			spec.path(path, pathVariables);
			return this;
		}

		@Override
		public SessionRequestHeadersPathSpec uri(URI uri) {
			spec.uri(uri);
			return this;
		}

		@Override
		public SessionRequestHeadersPathSpec header(String headerName, String... headerValues) {
			spec.header(headerName, headerValues);
			return this;
		}

		@Override
		public SessionRequestHeadersPathSpec headers(HttpHeaders httpHeaders) {
			spec.headers(httpHeaders);
			return this;
		}

		@Override
		public SessionRequestHeadersPathSpec headers(Consumer<HttpHeaders> headersConsumer) {
			spec.headers(headersConsumer);
			return this;
		}

		@Override
		public VaultClient.ResponseSpec retrieve() {
			spec.token(token);
			return spec.retrieve();
		}

	}

	static class SessionRequestBodyHeadersPathSpec implements VaultClient.RequestHeadersBodyPathSpec {

		private final VaultToken token;

		private final VaultClient.RequestHeadersBodyPathSpec spec;

		SessionRequestBodyHeadersPathSpec(VaultToken token, VaultClient.RequestHeadersBodyPathSpec spec) {
			this.token = token;
			this.spec = spec;
		}

		@Override
		public SessionRequestBodyHeadersPathSpec path(String path, @Nullable Object... pathVariables) {
			spec.path(path, pathVariables);
			return this;
		}

		@Override
		public SessionRequestBodyHeadersPathSpec path(String path, Map<String, ?> pathVariables) {
			spec.path(path, pathVariables);
			return this;
		}

		@Override
		public VaultClient.RequestBodySpec uri(URI uri) {
			spec.uri(uri);
			return this;
		}

		@Override
		public SessionRequestBodyHeadersPathSpec header(String headerName, String... headerValues) {
			spec.header(headerName, headerValues);
			return this;
		}

		@Override
		public SessionRequestBodyHeadersPathSpec headers(HttpHeaders httpHeaders) {
			spec.headers(httpHeaders);
			return this;
		}

		@Override
		public SessionRequestBodyHeadersPathSpec headers(Consumer<HttpHeaders> headersConsumer) {
			spec.headers(headersConsumer);
			return this;
		}

		@Override
		public VaultClient.RequestBodySpec body(Object body) {
			spec.body(body);
			return this;
		}

		@Override
		public <T> VaultClient.RequestBodySpec body(T body, ParameterizedTypeReference<T> bodyType) {
			spec.body(body, bodyType);
			return this;
		}

		@Override
		public VaultClient.ResponseSpec retrieve() {
			spec.token(token);
			return spec.retrieve();
		}

	}

}
