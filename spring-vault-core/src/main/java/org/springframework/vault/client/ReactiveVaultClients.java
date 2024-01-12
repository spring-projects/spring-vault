/*
 * Copyright 2017-2024 the original author or authors.
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
package org.springframework.vault.client;

import java.net.URI;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.core.codec.ByteArrayDecoder;
import org.springframework.core.codec.ByteArrayEncoder;
import org.springframework.core.codec.StringDecoder;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.codec.CodecConfigurer.CustomCodecs;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilderFactory;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Vault Client factory to create {@link WebClient} configured to the needs of accessing
 * Vault.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class ReactiveVaultClients {

	/**
	 * Create a {@link WebClient} configured with {@link VaultEndpoint} and
	 * {@link ClientHttpConnector}. The client accepts relative URIs without a leading
	 * slash that are expanded to use {@link VaultEndpoint}.
	 * <p>
	 * Requires Jackson 2 for Object-to-JSON mapping.
	 * @param endpoint must not be {@literal null}.
	 * @param connector must not be {@literal null}.
	 * @return the configured {@link WebClient}.
	 */
	public static WebClient createWebClient(VaultEndpoint endpoint, ClientHttpConnector connector) {
		return createWebClient(SimpleVaultEndpointProvider.of(endpoint), connector);
	}

	/**
	 * Create a {@link WebClient} configured with {@link VaultEndpoint} and
	 * {@link ClientHttpConnector}. The client accepts relative URIs without a leading
	 * slash that are expanded to use {@link VaultEndpoint}.
	 * <p>
	 * Requires Jackson 2 for Object-to-JSON mapping. {@link VaultEndpointProvider} is
	 * called on {@link Schedulers#boundedElastic()} to ensure that I/O threads are never
	 * blocked.
	 * @param endpointProvider must not be {@literal null}.
	 * @param connector must not be {@literal null}.
	 * @return the configured {@link WebClient}.
	 */
	public static WebClient createWebClient(VaultEndpointProvider endpointProvider, ClientHttpConnector connector) {
		return createWebClient(wrap(endpointProvider), connector);
	}

	/**
	 * Create a {@link WebClient} configured with {@link VaultEndpoint} and
	 * {@link ClientHttpConnector}. The client accepts relative URIs without a leading
	 * slash that are expanded to use {@link VaultEndpoint}.
	 * <p>
	 * Requires Jackson 2 for Object-to-JSON mapping.
	 * @param endpointProvider must not be {@literal null}.
	 * @param connector must not be {@literal null}.
	 * @return the configured {@link WebClient}.
	 * @since 2.3
	 */
	public static WebClient createWebClient(ReactiveVaultEndpointProvider endpointProvider,
			ClientHttpConnector connector) {
		return createWebClientBuilder(endpointProvider, connector).build();
	}

	/**
	 * Create a {@link WebClient.Builder} configured with {@link VaultEndpoint} and
	 * {@link ClientHttpConnector}. The client accepts relative URIs without a leading
	 * slash that are expanded to use {@link VaultEndpoint}.
	 * <p>
	 * Requires Jackson 2 for Object-to-JSON mapping.
	 * @param endpointProvider must not be {@literal null}.
	 * @param connector must not be {@literal null}.
	 * @return the prepared {@link WebClient.Builder}.
	 */
	static WebClient.Builder createWebClientBuilder(ReactiveVaultEndpointProvider endpointProvider,
			ClientHttpConnector connector) {

		Assert.notNull(endpointProvider, "ReactiveVaultEndpointProvider must not be null");
		Assert.notNull(connector, "ClientHttpConnector must not be null");

		ExchangeStrategies strategies = ExchangeStrategies.builder().codecs(configurer -> {

			CustomCodecs cc = configurer.customCodecs();

			cc.register(new ByteArrayDecoder());
			cc.register(new Jackson2JsonDecoder());
			cc.register(StringDecoder.allMimeTypes());

			cc.register(new ByteArrayEncoder());
			cc.register(new Jackson2JsonEncoder());

		}).build();

		WebClient.Builder builder = WebClient.builder().exchangeStrategies(strategies).clientConnector(connector);

		boolean simpleSource = false;
		if (endpointProvider instanceof VaultEndpointProviderAdapter) {

			if (((VaultEndpointProviderAdapter) endpointProvider).source instanceof SimpleVaultEndpointProvider) {
				simpleSource = true;

				UriBuilderFactory uriBuilderFactory = VaultClients
					.createUriBuilderFactory(((VaultEndpointProviderAdapter) endpointProvider).source);
				builder.uriBuilderFactory(uriBuilderFactory);
			}
		}

		if (!simpleSource) {
			builder.filter((request, next) -> {

				URI uri = request.url();

				if (!uri.isAbsolute()) {

					return endpointProvider.getVaultEndpoint().flatMap(endpoint -> {

						UriComponents uriComponents = UriComponentsBuilder.fromUri(uri)
							.scheme(endpoint.getScheme())
							.host(endpoint.getHost())
							.port(endpoint.getPort())
							.replacePath(endpoint.getPath())
							.path(VaultClients.normalizePath(endpoint.getPath(), uri.getPath()))
							.build();

						ClientRequest requestToSend = ClientRequest.from(request).url(uriComponents.toUri()).build();

						return next.exchange(requestToSend);
					});

				}
				return next.exchange(request);
			});
		}

		return builder;
	}

	/**
	 * Create a {@link ExchangeFilterFunction} that associates each request with a
	 * {@code X-Vault-Namespace} header if the header is not present.
	 * @param namespace the Vault namespace to use. Must not be {@literal null} or empty.
	 * @return the {@link ExchangeFilterFunction} to register with {@link WebClient}.
	 * @see VaultHttpHeaders#VAULT_NAMESPACE
	 * @since 2.2
	 */
	public static ExchangeFilterFunction namespace(String namespace) {

		Assert.hasText(namespace, "Vault Namespace must not be empty!");

		return ExchangeFilterFunction.ofRequestProcessor(request -> {

			return Mono.fromSupplier(() -> {

				return ClientRequest.from(request).headers(headers -> {

					if (!headers.containsKey(VaultHttpHeaders.VAULT_NAMESPACE)) {
						headers.add(VaultHttpHeaders.VAULT_NAMESPACE, namespace);
					}
				}).build();
			});
		});
	}

	/**
	 * Wrap a {@link VaultEndpointProvider} into a {@link ReactiveVaultEndpointProvider}
	 * to invoke {@link VaultEndpointProvider#getVaultEndpoint()} on a dedicated
	 * {@link Schedulers#boundedElastic() scheduler}.
	 * @param endpointProvider must not be {@literal null}.
	 * @return {@link ReactiveVaultEndpointProvider} wrapping
	 * {@link VaultEndpointProvider}.
	 * @since 2.3
	 */
	public static ReactiveVaultEndpointProvider wrap(VaultEndpointProvider endpointProvider) {

		Assert.notNull(endpointProvider, "VaultEndpointProvider must not be null");

		return new VaultEndpointProviderAdapter(endpointProvider);
	}

	private static class VaultEndpointProviderAdapter implements ReactiveVaultEndpointProvider {

		private final VaultEndpointProvider source;

		private final Mono<VaultEndpoint> mono;

		VaultEndpointProviderAdapter(VaultEndpointProvider provider) {

			this.source = provider;
			this.mono = Mono.fromSupplier(provider::getVaultEndpoint).subscribeOn(Schedulers.boundedElastic());
		}

		@Override
		public Mono<VaultEndpoint> getVaultEndpoint() {
			return this.mono;
		}

	}

}
