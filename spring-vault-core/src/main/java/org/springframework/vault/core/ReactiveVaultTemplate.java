/*
 * Copyright 2017-2025 the original author or authors.
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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.authentication.VaultTokenSupplier;
import org.springframework.vault.client.ReactiveVaultClient;
import org.springframework.vault.client.ReactiveVaultClient.RequestBodySpec;
import org.springframework.vault.client.SimpleVaultEndpointProvider;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.VaultEndpointProvider;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.client.WebClientBuilder;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import static org.springframework.web.reactive.function.client.ExchangeFilterFunction.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

/**
 * This class encapsulates main Vault interaction. {@link ReactiveVaultTemplate}
 * will log into Vault on initialization and use the token throughout the whole
 * lifetime. This is the main entry point to interact with Vault in an
 * authenticated and unauthenticated context.
 * <p>{@link ReactiveVaultTemplate} allows execution of callback methods.
 * Callbacks can execute requests within a {@link #doWithSession(Function)
 * session context} and the {@link #doWithVault(Function) without a session}.
 * <p>Paths used in this interface (and interfaces accessible from here) are
 * considered relative to the {@link VaultEndpoint}. Paths that are
 * fully-qualified URI's can be used to access Vault cluster members in an
 * authenticated context. To prevent unwanted full URI access, make sure to
 * sanitize paths before passing them to this interface.
 *
 * @author Mark Paluch
 * @author Raoof Mohammed
 * @author James Luke
 * @author Timothy R. Weiand
 * @see SessionManager
 * @since 2.0
 */
public class ReactiveVaultTemplate implements ReactiveVaultOperations {

	private final WebClient statelessClient;

	private final ReactiveVaultClient vaultClient;

	private final WebClient sessionClient;

	private final ReactiveVaultClient sessionVaultClient;

	private final VaultTokenSupplier vaultTokenSupplier;


	/**
	 * Create a new {@link ReactiveVaultTemplate} with a {@link VaultEndpoint},
	 * {@link ClientHttpConnector}. This constructor does not use a
	 * {@link VaultTokenSupplier}. It is intended for usage with Vault Agent to
	 * inherit Vault Agent's authentication without using the
	 * {@link VaultHttpHeaders#VAULT_TOKEN authentication token header}.
	 *
	 * @param vaultEndpoint must not be {@literal null}.
	 * @param connector     must not be {@literal null}.
	 * @since 2.2.1
	 */
	public ReactiveVaultTemplate(VaultEndpoint vaultEndpoint, ClientHttpConnector connector) {
		this(SimpleVaultEndpointProvider.of(vaultEndpoint), connector);
	}

	/**
	 * Create a new {@link ReactiveVaultTemplate} with a {@link VaultEndpoint},
	 * {@link ClientHttpConnector} and {@link VaultTokenSupplier}.
	 *
	 * @param vaultEndpoint      must not be {@literal null}.
	 * @param connector          must not be {@literal null}.
	 * @param vaultTokenSupplier must not be {@literal null}.
	 */
	public ReactiveVaultTemplate(VaultEndpoint vaultEndpoint, ClientHttpConnector connector,
								 VaultTokenSupplier vaultTokenSupplier) {
		this(SimpleVaultEndpointProvider.of(vaultEndpoint), connector, vaultTokenSupplier);
	}

	/**
	 * Create a new {@link ReactiveVaultTemplate} with a
	 * {@link VaultEndpointProvider} and {@link ClientHttpConnector}. This
	 * constructor does not use a {@link VaultTokenSupplier}. It is intended for
	 * usage with Vault Agent to inherit Vault Agent's authentication without using
	 * the {@link VaultHttpHeaders#VAULT_TOKEN authentication token header}.
	 *
	 * @param endpointProvider must not be {@literal null}.
	 * @param connector        must not be {@literal null}.
	 * @since 2.2.1
	 */
	public ReactiveVaultTemplate(VaultEndpointProvider endpointProvider, ClientHttpConnector connector) {
		Assert.notNull(endpointProvider, "VaultEndpointProvider must not be null");
		Assert.notNull(connector, "ClientHttpConnector must not be null");
		WebClient webClient = doCreateWebClient(endpointProvider, connector);
		this.vaultTokenSupplier = NoTokenSupplier.INSTANCE;
		this.statelessClient = webClient;
		this.vaultClient = ReactiveVaultClient.builder(webClient).build();
		this.sessionClient = webClient;
		this.sessionVaultClient = this.vaultClient;
	}

	/**
	 * Create a new {@code ReactiveVaultTemplate} with a {@link ReactiveVaultClient}.
	 *
	 * @param client must not be {@literal null}.
	 * @since 4.1
	 */
	public ReactiveVaultTemplate(ReactiveVaultClient client) {

		Assert.notNull(client, "VaultClient must not be null");

		this.vaultTokenSupplier = NoTokenSupplier.INSTANCE;
		this.statelessClient = getWebClient(client);
		this.vaultClient = client;
		this.sessionClient = this.statelessClient;
		this.sessionVaultClient = client;
	}

	/**
	 * Create a new {@code ReactiveVaultTemplate} with a {@link ReactiveVaultClient} and
	 * {@link VaultTokenSupplier}.
	 *
	 * @param client             must not be {@literal null}.
	 * @param vaultTokenSupplier must not be {@literal null}.
	 * @since 4.1
	 */
	@SuppressWarnings("NullAway")
	public ReactiveVaultTemplate(ReactiveVaultClient client, VaultTokenSupplier vaultTokenSupplier) {

		Assert.notNull(client, "VaultEndpoint must not be null");
		Assert.notNull(vaultTokenSupplier, "VaultTokenSupplier must not be null");

		this.vaultTokenSupplier = vaultTokenSupplier;

		this.statelessClient = getWebClient(client);
		this.vaultClient = client;

		AtomicReference<WebClient> clientRef = new AtomicReference<>();
		client.mutate().configureWebClient(it -> {
			it.filter(getSessionFilter());
			clientRef.set(it.build());
		});

		this.sessionClient = clientRef.get();
		this.sessionVaultClient = client;
	}

	@SuppressWarnings("NullAway")
	private static WebClient getWebClient(ReactiveVaultClient client) {
		AtomicReference<WebClient> clientRef = new AtomicReference<>();
		client.mutate().configureWebClient(it -> {
			clientRef.set(it.build());
		});
		return clientRef.get();
	}

	/**
	 * Create a new {@link ReactiveVaultTemplate} with a
	 * {@link VaultEndpointProvider}, {@link ClientHttpConnector} and
	 * {@link VaultTokenSupplier}.
	 *
	 * @param endpointProvider   must not be {@literal null}.
	 * @param connector          must not be {@literal null}.
	 * @param vaultTokenSupplier must not be {@literal null}.
	 */
	public ReactiveVaultTemplate(VaultEndpointProvider endpointProvider, ClientHttpConnector connector,
								 VaultTokenSupplier vaultTokenSupplier) {
		Assert.notNull(endpointProvider, "VaultEndpointProvider must not be null");
		Assert.notNull(connector, "ClientHttpConnector must not be null");
		Assert.notNull(vaultTokenSupplier, "VaultTokenSupplier must not be null");
		this.vaultTokenSupplier = vaultTokenSupplier;
		this.statelessClient = doCreateWebClient(endpointProvider, connector);
		this.vaultClient = ReactiveVaultClient.builder(this.statelessClient).build();
		this.sessionClient = doCreateSessionWebClient(endpointProvider, connector);
		this.sessionVaultClient = ReactiveVaultClient.builder(this.sessionClient).build();
	}

	/**
	 * Create a new {@link ReactiveVaultTemplate} through a
	 * {@link WebClientBuilder}. This constructor does not use a
	 * {@link VaultTokenSupplier}. It is intended for usage with Vault Agent to
	 * inherit Vault Agent's authentication without using the
	 * {@link VaultHttpHeaders#VAULT_TOKEN authentication token header}.
	 *
	 * @param webClientBuilder must not be {@literal null}.
	 * @since 2.2.1
	 */
	public ReactiveVaultTemplate(WebClientBuilder webClientBuilder) {
		Assert.notNull(webClientBuilder, "WebClientBuilder must not be null");
		WebClient webClient = webClientBuilder.build();
		this.vaultTokenSupplier = NoTokenSupplier.INSTANCE;
		this.statelessClient = webClient;
		this.vaultClient = ReactiveVaultClient.builder(this.statelessClient).build();
		this.sessionClient = webClient;
		this.sessionVaultClient = this.vaultClient;
	}

	/**
	 * Create a new {@link ReactiveVaultTemplate} through a
	 * {@link WebClientBuilder}, and {@link VaultTokenSupplier}.
	 *
	 * @param webClientBuilder   must not be {@literal null}.
	 * @param vaultTokenSupplier must not be {@literal null}
	 * @since 2.2
	 */
	public ReactiveVaultTemplate(WebClientBuilder webClientBuilder, VaultTokenSupplier vaultTokenSupplier) {
		Assert.notNull(webClientBuilder, "WebClientBuilder must not be null");
		Assert.notNull(vaultTokenSupplier, "VaultTokenSupplier must not be null");
		this.vaultTokenSupplier = vaultTokenSupplier;
		this.statelessClient = webClientBuilder.build();
		this.vaultClient = ReactiveVaultClient.builder(this.statelessClient).build();
		this.sessionClient = webClientBuilder.build().mutate().filter(getSessionFilter()).build();
		this.sessionVaultClient = ReactiveVaultClient.builder(this.sessionClient).build();
	}

	/**
	 * Create a {@link WebClient} to be used by {@link ReactiveVaultTemplate} for
	 * Vault communication given {@link VaultEndpointProvider} and
	 * {@link ClientHttpConnector}. {@link VaultEndpointProvider} is used to
	 * contribute host and port details for relative URLs typically used by the
	 * Template API. Subclasses may override this method to customize the
	 * {@link WebClient}.
	 *
	 * @param endpointProvider must not be {@literal null}.
	 * @param connector        must not be {@literal null}.
	 * @return the {@link WebClient} used for Vault communication.
	 * @since 2.1
	 */
	protected WebClient doCreateWebClient(VaultEndpointProvider endpointProvider, ClientHttpConnector connector) {
		Assert.notNull(endpointProvider, "VaultEndpointProvider must not be null");
		Assert.notNull(connector, "ClientHttpConnector must not be null");
		return WebClientBuilder.builder().httpConnector(connector).endpointProvider(endpointProvider).build();
	}

	/**
	 * Create a session-bound {@link WebClient} to be used by {@link VaultTemplate}
	 * for Vault communication given {@link VaultEndpointProvider} and
	 * {@link ClientHttpConnector} for calls that require an authenticated context.
	 * {@link VaultEndpointProvider} is used to contribute host and port details for
	 * relative URLs typically used by the Template API. Subclasses may override
	 * this method to customize the {@link WebClient}.
	 *
	 * @param endpointProvider must not be {@literal null}.
	 * @param connector        must not be {@literal null}.
	 * @return the {@link WebClient} used for Vault communication.
	 * @since 2.1
	 */
	protected WebClient doCreateSessionWebClient(VaultEndpointProvider endpointProvider,
												 ClientHttpConnector connector) {
		Assert.notNull(endpointProvider, "VaultEndpointProvider must not be null");
		Assert.notNull(connector, "ClientHttpConnector must not be null");
		ExchangeFilterFunction filter = getSessionFilter();
		return WebClientBuilder.builder()
				.httpConnector(connector)
				.endpointProvider(endpointProvider)
				.filter(filter)
				.build();
	}

	private ExchangeFilterFunction getSessionFilter() {
		return ofRequestProcessor(request -> this.vaultTokenSupplier.getVaultToken().map(token -> {
			return ClientRequest.from(request).headers(headers -> {
				headers.set(VaultHttpHeaders.VAULT_TOKEN, token.getToken());
			}).build();
		}));
	}


	@Override
	public ReactiveVaultSysOperations opsForSys() {
		return new ReactiveVaultSysTemplate(this);
	}

	@Override
	public ReactiveVaultTransitOperations opsForTransit() {
		return opsForTransit("transit");
	}

	@Override
	public ReactiveVaultTransitOperations opsForTransit(String path) {
		return new ReactiveVaultTransitTemplate(this, path);
	}

	@Override
	public ReactiveVaultKeyValueOperations opsForKeyValue(String path, KeyValueBackend apiVersion) {
		return switch (apiVersion) {
		case KV_1 -> new ReactiveVaultKeyValue1Template(this, path);
		case KV_2 -> new ReactiveVaultKeyValue2Template(this, path);
		};
	}

	@Override
	public ReactiveVaultVersionedKeyValueOperations opsForVersionedKeyValue(String path) {
		return new ReactiveVaultVersionedKeyValueTemplate(this, path);
	}


	@Override
	public Mono<VaultResponse> read(String path) {
		Assert.hasText(path, "Path must not be empty");
		return doRead(path, VaultResponse.class);
	}

	@Override
	public <T> Mono<VaultResponseSupport<T>> read(String path, Class<T> responseType) {
		return doWithSessionClient(client -> {
			ParameterizedTypeReference<VaultResponseSupport<T>> ref = VaultResponses.getTypeReference(responseType);
			return client.get()
					.path(path)
					.retrieve()
					.onStatus(HttpStatusUtil::isNotFound, clientResponse -> clientResponse.releaseBody().then(Mono.empty()))
					.bodyToMono(ref);
		});
	}

	@Override
	@SuppressWarnings("unchecked")
	public Flux<String> list(String path) {
		Assert.hasText(path, "Path must not be empty");
		return doRead("%s?list=true".formatted(path.endsWith("/") ? path : (path + "/")), VaultListResponse.class)
				.filter(response -> response.getData() != null && response.getData().containsKey("keys"))
				.flatMapIterable(response -> (List<String>) response.getRequiredData().get("keys"));
	}

	@Override
	public Mono<VaultResponse> write(String path, @Nullable Object body) {
		Assert.hasText(path, "Path must not be empty");
		return doWithSessionClient(client -> {

			RequestBodySpec spec = client.post().path(path);
			if (body != null) {
				spec.bodyValue(body);
			}
			return spec.retrieve().body();
		});
	}

	@Override
	public Mono<Void> delete(String path) {
		Assert.hasText(path, "Path must not be empty");
		return doWithSessionClient(client -> client.delete()
				.path(path)
				.retrieve()
				.toBodilessEntity()).then();
	}

	@Override
	public <V, T extends Publisher<V>> T doWithVault(Function<WebClient, ? extends T> clientCallback)
			throws VaultException, WebClientException {
		Assert.notNull(clientCallback, "Client callback must not be null");
		try {
			return clientCallback.apply(this.statelessClient);
		} catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e);
		}
	}

	<V, T extends Publisher<V>> T doWithVaultClient(Function<ReactiveVaultClient, ? extends T> clientCallback)
			throws VaultException, WebClientException {

		Assert.notNull(clientCallback, "Client callback must not be null");

		return clientCallback.apply(this.vaultClient);
	}

	@Override
	public <V, T extends Publisher<V>> T doWithSession(Function<WebClient, ? extends T> sessionCallback)
			throws VaultException, WebClientException {
		Assert.notNull(sessionCallback, "Session callback must not be null");
		try {
			return sessionCallback.apply(this.sessionClient);
		} catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e);
		}
	}

	<V, T extends Publisher<V>> T doWithSessionClient(Function<ReactiveVaultClient, ? extends T> sessionCallback)
			throws VaultException, WebClientException {

		Assert.notNull(sessionCallback, "Session callback must not be null");

		if (vaultTokenSupplier == NoTokenSupplier.INSTANCE) {
			return doWithVaultClient(sessionCallback);
		}

		return sessionCallback.apply(this.sessionVaultClient);
	}

	private <T> Mono<T> doRead(String path, Class<T> responseType) {
		return doWithSessionClient(client -> client.get().path(path) //
				.retrieve()
				.onStatus(HttpStatusUtil::isNotFound, clientResponse -> clientResponse.releaseBody().then(Mono.empty()))
				.bodyToMono(responseType));
	}

	static <T> Function<ClientResponse, Mono<T>> mapResponse(Class<T> bodyType, String path, HttpMethod method) {
		return response -> isSuccess(response) ? response.bodyToMono(bodyType) : mapOtherwise(response, path, method);
	}

	static <T> Function<ClientResponse, Mono<T>> mapResponse(ParameterizedTypeReference<T> typeReference, String path,
															 HttpMethod method) {

		return response -> isSuccess(response) ? response.body(BodyExtractors.toMono(typeReference))
				: mapOtherwise(response, path, method);
	}

	private static boolean isSuccess(ClientResponse response) {
		return response.statusCode().is2xxSuccessful();
	}

	private static <T> Mono<T> mapOtherwise(ClientResponse response, String path, HttpMethod method) {

		if (HttpStatusUtil.isNotFound(response.statusCode()) && method == HttpMethod.GET) {
			return response.createError();
		}

		return response.bodyToMono(String.class).flatMap(body -> {

			String error = VaultResponses.getError(body);

			return Mono.error(VaultResponses.buildException(response.statusCode(), path, error));
		});
	}

	private enum NoTokenSupplier implements VaultTokenSupplier {

		INSTANCE;

		@Override
		public Mono<VaultToken> getVaultToken() {
			return Mono.error(new UnsupportedOperationException("Token retrieval disabled"));
		}
	}
}
