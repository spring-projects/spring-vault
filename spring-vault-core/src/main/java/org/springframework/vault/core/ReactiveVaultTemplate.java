/*
 * Copyright 2017-2019 the original author or authors.
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.authentication.VaultTokenSupplier;
import org.springframework.vault.client.ReactiveVaultClients;
import org.springframework.vault.client.SimpleVaultEndpointProvider;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.VaultEndpointProvider;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;

import static org.springframework.web.reactive.function.client.ExchangeFilterFunction.ofRequestProcessor;

/**
 * This class encapsulates main Vault interaction. {@link ReactiveVaultTemplate} will log
 * into Vault on initialization and use the token throughout the whole lifetime.
 *
 * @author Mark Paluch
 * @see SessionManager
 * @since 2.0
 */
public class ReactiveVaultTemplate implements ReactiveVaultOperations {

	private final WebClient statelessClient;

	private final WebClient sessionClient;

	private final VaultTokenSupplier vaultTokenSupplier;

	/**
	 * Create a new {@link ReactiveVaultTemplate} with a {@link VaultEndpoint},
	 * {@link ClientHttpConnector} and {@link VaultTokenSupplier}.
	 *
	 * @param vaultEndpoint must not be {@literal null}.
	 * @param connector must not be {@literal null}.
	 * @param vaultTokenSupplier must not be {@literal null}.
	 */
	public ReactiveVaultTemplate(VaultEndpoint vaultEndpoint,
			ClientHttpConnector connector, VaultTokenSupplier vaultTokenSupplier) {
		this(SimpleVaultEndpointProvider.of(vaultEndpoint), connector, vaultTokenSupplier);
	}

	/**
	 * Create a new {@link ReactiveVaultTemplate} with a {@link VaultEndpointProvider},
	 * {@link ClientHttpConnector} and {@link VaultTokenSupplier}.
	 *
	 * @param endpointProvider must not be {@literal null}.
	 * @param connector must not be {@literal null}.
	 * @param vaultTokenSupplier must not be {@literal null}.
	 */
	public ReactiveVaultTemplate(VaultEndpointProvider endpointProvider,
			ClientHttpConnector connector, VaultTokenSupplier vaultTokenSupplier) {

		Assert.notNull(endpointProvider, "VaultEndpointProvider must not be null");
		Assert.notNull(connector, "ClientHttpConnector must not be null");
		Assert.notNull(vaultTokenSupplier, "AuthenticationSupplier must not be null");

		this.vaultTokenSupplier = vaultTokenSupplier;
		this.statelessClient = doCreateWebClient(endpointProvider, connector);
		this.sessionClient = doCreateSessionWebClient(endpointProvider, connector);
	}

	/**
	 * Create a {@link WebClient} to be used by {@link ReactiveVaultTemplate} for Vault
	 * communication given {@link VaultEndpointProvider} and {@link ClientHttpConnector}.
	 * {@link VaultEndpointProvider} is used to contribute host and port details for
	 * relative URLs typically used by the Template API. Subclasses may override this
	 * method to customize the {@link WebClient}.
	 *
	 * @param endpointProvider must not be {@literal null}.
	 * @param connector must not be {@literal null}.
	 * @return the {@link WebClient} used for Vault communication.
	 * @since 2.1
	 */
	protected WebClient doCreateWebClient(VaultEndpointProvider endpointProvider,
			ClientHttpConnector connector) {

		Assert.notNull(endpointProvider, "VaultEndpointProvider must not be null");
		Assert.notNull(connector, "ClientHttpConnector must not be null");

		return ReactiveVaultClients.createWebClient(endpointProvider, connector);
	}

	/**
	 * Create a session-bound {@link WebClient} to be used by {@link VaultTemplate} for
	 * Vault communication given {@link VaultEndpointProvider} and
	 * {@link ClientHttpConnector} for calls that require an authenticated context.
	 * {@link VaultEndpointProvider} is used to contribute host and port details for
	 * relative URLs typically used by the Template API. Subclasses may override this
	 * method to customize the {@link WebClient}.
	 *
	 * @param endpointProvider must not be {@literal null}.
	 * @param connector must not be {@literal null}.
	 * @return the {@link WebClient} used for Vault communication.
	 * @since 2.1
	 */
	protected WebClient doCreateSessionWebClient(VaultEndpointProvider endpointProvider,
			ClientHttpConnector connector) {

		Assert.notNull(endpointProvider, "VaultEndpointProvider must not be null");
		Assert.notNull(connector, "ClientHttpConnector must not be null");

		ExchangeFilterFunction filter = ofRequestProcessor(request -> vaultTokenSupplier
				.getVaultToken().map(token -> {

					return ClientRequest.from(request).headers(headers -> {
						headers.set(VaultHttpHeaders.VAULT_TOKEN, token.getToken());
					}).build();
				}));

		return doCreateWebClient(endpointProvider, connector).mutate().filter(filter)
				.build();
	}

	@Override
	public Mono<VaultResponse> read(String path) {

		Assert.hasText(path, "Path must not be empty");

		return doRead(path, VaultResponse.class);
	}

	@Override
	public <T> Mono<VaultResponseSupport<T>> read(String path, Class<T> responseType) {

		ParameterizedTypeReference<VaultResponseSupport<T>> ref = VaultResponses
				.getTypeReference(responseType);

		return sessionClient.get().uri(path).exchange().flatMap(mapResponse(ref, path));
	}

	@Override
	@SuppressWarnings("unchecked")
	public Flux<String> list(String path) {

		Assert.hasText(path, "Path must not be empty");

		Mono<VaultListResponse> read = doRead(
				String.format("%s?list=true", path.endsWith("/") ? path : (path + "/")),
				VaultListResponse.class);

		return read
				.filter(response -> response.getData() != null
						&& response.getData().containsKey("keys"))
				//
				.flatMapIterable(
						response -> (List<String>) response.getRequiredData().get("keys"));
	}

	@Override
	public Mono<VaultResponse> write(String path, @Nullable Object body) {

		Assert.hasText(path, "Path must not be empty");

		RequestBodySpec uri = sessionClient.post().uri(path);
		Mono<ClientResponse> exchange;
		if (body != null) {
			exchange = uri.syncBody(body).exchange();
		}
		else {
			exchange = uri.exchange();
		}
		return exchange.flatMap(mapResponse(VaultResponse.class, path));
	}

	@Override
	public Mono<Void> delete(String path) {

		Assert.hasText(path, "Path must not be empty");

		return sessionClient.delete().uri(path).exchange()
				.flatMap(mapResponse(String.class, path)).then();
	}

	@Override
	public <V, T extends Publisher<V>> T doWithVault(
			Function<WebClient, ? extends T> clientCallback) throws VaultException,
			WebClientException {

		Assert.notNull(clientCallback, "Client callback must not be null");

		try {
			return (T) clientCallback.apply(statelessClient);
		}
		catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e);
		}
	}

	@Override
	public <V, T extends Publisher<V>> T doWithSession(
			Function<WebClient, ? extends T> sessionCallback) throws VaultException,
			WebClientException {

		Assert.notNull(sessionCallback, "Session callback must not be null");

		try {
			return (T) sessionCallback.apply(sessionClient);
		}
		catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e);
		}
	}

	private <T> Mono<T> doRead(String path, Class<T> responseType) {

		return doWithSession(client -> client.get() //
				.uri(path).exchange().flatMap(mapResponse(responseType, path)));
	}

	private static <T> Function<ClientResponse, Mono<? extends T>> mapResponse(
			Class<T> bodyType, String path) {
		return response -> isSuccess(response) ? response.bodyToMono(bodyType)
				: mapOtherwise(response, path);
	}

	private static <T> Function<ClientResponse, Mono<? extends T>> mapResponse(
			ParameterizedTypeReference<T> typeReference, String path) {

		return response -> isSuccess(response) ? response.body(BodyExtractors
				.toMono(typeReference)) : mapOtherwise(response, path);
	}

	private static boolean isSuccess(ClientResponse response) {
		return response.statusCode().is2xxSuccessful();
	}

	private static <T> Mono<? extends T> mapOtherwise(ClientResponse response, String path) {

		if (response.statusCode() == HttpStatus.NOT_FOUND) {
			return Mono.empty();
		}

		return response.bodyToMono(String.class).flatMap(
				body -> {

					String error = VaultResponses.getError(body);

					return Mono.error(VaultResponses.buildException(
							response.statusCode(), path, error));
				});
	}

	private static class VaultListResponse extends
			VaultResponseSupport<Map<String, Object>> {
	}
}
