/*
 * Copyright 2017-2023 the original author or authors.
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

import org.reactivestreams.Publisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.authentication.VaultTokenSupplier;
import org.springframework.vault.client.SimpleVaultEndpointProvider;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.VaultEndpointProvider;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.client.WebClientBuilder;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.springframework.web.reactive.function.client.ExchangeFilterFunction.ofRequestProcessor;

/**
 * This class encapsulates main Vault interaction. {@link ReactiveVaultTemplate} will log
 * into Vault on initialization and use the token throughout the whole lifetime.
 *
 * @author Mark Paluch
 * @author Raoof Mohammed
 * @author James Luke
 * @see SessionManager
 * @since 2.0
 */
public class ReactiveVaultTemplate implements ReactiveVaultOperations {

	private final WebClient statelessClient;

	private final WebClient sessionClient;

	private final VaultTokenSupplier vaultTokenSupplier;

	/**
	 * Create a new {@link ReactiveVaultTemplate} with a {@link VaultEndpoint},
	 * {@link ClientHttpConnector}. This constructor does not use a
	 * {@link VaultTokenSupplier}. It is intended for usage with Vault Agent to inherit
	 * Vault Agent's authentication without using the {@link VaultHttpHeaders#VAULT_TOKEN
	 * authentication token header}.
	 * @param vaultEndpoint must not be {@literal null}.
	 * @param connector must not be {@literal null}.
	 * @since 2.2.1
	 */
	public ReactiveVaultTemplate(VaultEndpoint vaultEndpoint, ClientHttpConnector connector) {
		this(SimpleVaultEndpointProvider.of(vaultEndpoint), connector);
	}

	/**
	 * Create a new {@link ReactiveVaultTemplate} with a {@link VaultEndpoint},
	 * {@link ClientHttpConnector} and {@link VaultTokenSupplier}.
	 * @param vaultEndpoint must not be {@literal null}.
	 * @param connector must not be {@literal null}.
	 * @param vaultTokenSupplier must not be {@literal null}.
	 */
	public ReactiveVaultTemplate(VaultEndpoint vaultEndpoint, ClientHttpConnector connector,
			VaultTokenSupplier vaultTokenSupplier) {
		this(SimpleVaultEndpointProvider.of(vaultEndpoint), connector, vaultTokenSupplier);
	}

	/**
	 * Create a new {@link ReactiveVaultTemplate} with a {@link VaultEndpointProvider} and
	 * {@link ClientHttpConnector}. This constructor does not use a
	 * {@link VaultTokenSupplier}. It is intended for usage with Vault Agent to inherit
	 * Vault Agent's authentication without using the {@link VaultHttpHeaders#VAULT_TOKEN
	 * authentication token header}.
	 * @param endpointProvider must not be {@literal null}.
	 * @param connector must not be {@literal null}.
	 * @since 2.2.1
	 */
	public ReactiveVaultTemplate(VaultEndpointProvider endpointProvider, ClientHttpConnector connector) {

		Assert.notNull(endpointProvider, "VaultEndpointProvider must not be null");
		Assert.notNull(connector, "ClientHttpConnector must not be null");

		WebClient webClient = doCreateWebClient(endpointProvider, connector);

		this.vaultTokenSupplier = NoTokenSupplier.INSTANCE;
		this.statelessClient = webClient;
		this.sessionClient = webClient;
	}

	/**
	 * Create a new {@link ReactiveVaultTemplate} with a {@link VaultEndpointProvider},
	 * {@link ClientHttpConnector} and {@link VaultTokenSupplier}.
	 * @param endpointProvider must not be {@literal null}.
	 * @param connector must not be {@literal null}.
	 * @param vaultTokenSupplier must not be {@literal null}.
	 */
	public ReactiveVaultTemplate(VaultEndpointProvider endpointProvider, ClientHttpConnector connector,
			VaultTokenSupplier vaultTokenSupplier) {

		Assert.notNull(endpointProvider, "VaultEndpointProvider must not be null");
		Assert.notNull(connector, "ClientHttpConnector must not be null");
		Assert.notNull(vaultTokenSupplier, "VaultTokenSupplier must not be null");

		this.vaultTokenSupplier = vaultTokenSupplier;
		this.statelessClient = doCreateWebClient(endpointProvider, connector);
		this.sessionClient = doCreateSessionWebClient(endpointProvider, connector);
	}

	/**
	 * Create a new {@link ReactiveVaultTemplate} through a {@link WebClientBuilder}. This
	 * constructor does not use a {@link VaultTokenSupplier}. It is intended for usage
	 * with Vault Agent to inherit Vault Agent's authentication without using the
	 * {@link VaultHttpHeaders#VAULT_TOKEN authentication token header}.
	 * @param webClientBuilder must not be {@literal null}.
	 * @since 2.2.1
	 */
	public ReactiveVaultTemplate(WebClientBuilder webClientBuilder) {

		Assert.notNull(webClientBuilder, "WebClientBuilder must not be null");

		WebClient webClient = webClientBuilder.build();

		this.vaultTokenSupplier = NoTokenSupplier.INSTANCE;
		this.statelessClient = webClient;
		this.sessionClient = webClient;
	}

	/**
	 * Create a new {@link ReactiveVaultTemplate} through a {@link WebClientBuilder}, and
	 * {@link VaultTokenSupplier}.
	 * @param webClientBuilder must not be {@literal null}.
	 * @param vaultTokenSupplier must not be {@literal null}
	 * @since 2.2
	 */
	public ReactiveVaultTemplate(WebClientBuilder webClientBuilder, VaultTokenSupplier vaultTokenSupplier) {

		Assert.notNull(webClientBuilder, "WebClientBuilder must not be null");
		Assert.notNull(vaultTokenSupplier, "VaultTokenSupplier must not be null");

		this.vaultTokenSupplier = vaultTokenSupplier;
		this.statelessClient = webClientBuilder.build();
		this.sessionClient = webClientBuilder.build().mutate().filter(getSessionFilter()).build();
	}

	/**
	 * Create a {@link WebClient} to be used by {@link ReactiveVaultTemplate} for Vault
	 * communication given {@link VaultEndpointProvider} and {@link ClientHttpConnector}.
	 * {@link VaultEndpointProvider} is used to contribute host and port details for
	 * relative URLs typically used by the Template API. Subclasses may override this
	 * method to customize the {@link WebClient}.
	 * @param endpointProvider must not be {@literal null}.
	 * @param connector must not be {@literal null}.
	 * @return the {@link WebClient} used for Vault communication.
	 * @since 2.1
	 */
	protected WebClient doCreateWebClient(VaultEndpointProvider endpointProvider, ClientHttpConnector connector) {

		Assert.notNull(endpointProvider, "VaultEndpointProvider must not be null");
		Assert.notNull(connector, "ClientHttpConnector must not be null");

		return WebClientBuilder.builder().httpConnector(connector).endpointProvider(endpointProvider).build();
	}

	/**
	 * Create a session-bound {@link WebClient} to be used by {@link VaultTemplate} for
	 * Vault communication given {@link VaultEndpointProvider} and
	 * {@link ClientHttpConnector} for calls that require an authenticated context.
	 * {@link VaultEndpointProvider} is used to contribute host and port details for
	 * relative URLs typically used by the Template API. Subclasses may override this
	 * method to customize the {@link WebClient}.
	 * @param endpointProvider must not be {@literal null}.
	 * @param connector must not be {@literal null}.
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
	public Mono<VaultResponse> read(String path) {

		Assert.hasText(path, "Path must not be empty");

		return doRead(path, VaultResponse.class);
	}

	@Override
	public <T> Mono<VaultResponseSupport<T>> read(String path, Class<T> responseType) {

		return doWithSession(webClient -> {

			ParameterizedTypeReference<VaultResponseSupport<T>> ref = VaultResponses.getTypeReference(responseType);

			return webClient.get().uri(path).exchangeToMono(mapResponse(ref, path, HttpMethod.GET));
		});
	}

	@Override
	@SuppressWarnings("unchecked")
	public Flux<String> list(String path) {

		Assert.hasText(path, "Path must not be empty");

		Mono<VaultListResponse> read = doRead(String.format("%s?list=true", path.endsWith("/") ? path : (path + "/")),
				VaultListResponse.class);

		return read.filter(response -> response.getData() != null && response.getData().containsKey("keys"))
			.flatMapIterable(response -> (List<String>) response.getRequiredData().get("keys"));
	}

	@Override
	public Mono<VaultResponse> write(String path, @Nullable Object body) {

		Assert.hasText(path, "Path must not be empty");

		return doWithSession(webClient -> {

			RequestBodySpec uri = webClient.post().uri(path);

			if (body != null) {
				return uri.bodyValue(body).exchangeToMono(mapResponse(VaultResponse.class, path, HttpMethod.POST));
			}

			return uri.exchangeToMono(mapResponse(VaultResponse.class, path, HttpMethod.POST));
		});
	}

	@Override
	public Mono<Void> delete(String path) {

		Assert.hasText(path, "Path must not be empty");

		return doWithSession(webClient -> webClient.delete()
			.uri(path)
			.exchangeToMono(mapResponse(String.class, path, HttpMethod.DELETE))).then();
	}

	@Override
	public <V, T extends Publisher<V>> T doWithVault(Function<WebClient, ? extends T> clientCallback)
			throws VaultException, WebClientException {

		Assert.notNull(clientCallback, "Client callback must not be null");

		try {
			return (T) clientCallback.apply(this.statelessClient);
		}
		catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e);
		}
	}

	@Override
	public <V, T extends Publisher<V>> T doWithSession(Function<WebClient, ? extends T> sessionCallback)
			throws VaultException, WebClientException {

		Assert.notNull(sessionCallback, "Session callback must not be null");

		try {
			return (T) sessionCallback.apply(this.sessionClient);
		}
		catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e);
		}
	}

	private <T> Mono<T> doRead(String path, Class<T> responseType) {

		return doWithSession(client -> client.get() //
			.uri(path)
			.exchangeToMono(mapResponse(responseType, path, HttpMethod.GET)));
	}

	private static <T> Function<ClientResponse, Mono<T>> mapResponse(Class<T> bodyType, String path,
			HttpMethod method) {
		return response -> isSuccess(response) ? response.bodyToMono(bodyType) : mapOtherwise(response, path, method);
	}

	private static <T> Function<ClientResponse, Mono<T>> mapResponse(ParameterizedTypeReference<T> typeReference,
			String path, HttpMethod method) {

		return response -> isSuccess(response) ? response.body(BodyExtractors.toMono(typeReference))
				: mapOtherwise(response, path, method);
	}

	private static boolean isSuccess(ClientResponse response) {
		return response.statusCode().is2xxSuccessful();
	}

	private static <T> Mono<T> mapOtherwise(ClientResponse response, String path, HttpMethod method) {

		if (HttpStatusUtil.isNotFound(response.statusCode()) && method == HttpMethod.GET) {
			return response.releaseBody().then(Mono.empty());
		}

		return response.bodyToMono(String.class).flatMap(body -> {

			String error = VaultResponses.getError(body);

			return Mono.error(VaultResponses.buildException(response.statusCode(), path, error));
		});
	}

	private static class VaultListResponse extends VaultResponseSupport<Map<String, Object>> {

	}

	private enum NoTokenSupplier implements VaultTokenSupplier {

		INSTANCE;

		@Override
		public Mono<VaultToken> getVaultToken() {
			return Mono.error(new UnsupportedOperationException("Token retrieval disabled"));
		}

	}

}
