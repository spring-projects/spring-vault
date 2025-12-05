/*
 * Copyright 2025 the original author or authors.
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

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.util.Assert;
import org.springframework.vault.client.ReactiveVaultClient.Builder;
import org.springframework.vault.client.ReactiveVaultClients.VaultEndpointProviderAdapter;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilderFactory;

/**
 * Default implementation of {@link VaultClient.Builder}.
 *
 * @author Mark Paluch
 * @since 4.1
 */
class DefaultReactiveVaultClientBuilder implements ReactiveVaultClient.Builder {

	private final WebClient.Builder webClientBuilder;

	private @Nullable ReactiveVaultEndpointProvider endpointProvider;

	private @Nullable UriBuilderFactory uriBuilderFactory;


	DefaultReactiveVaultClientBuilder(WebClient webClient) {
		this.webClientBuilder = webClient.mutate();
	}

	DefaultReactiveVaultClientBuilder() {
		this(new ClientOptions(), SslConfiguration.unconfigured());
	}

	DefaultReactiveVaultClientBuilder(ClientOptions options, SslConfiguration sslConfiguration) {
		this(WebClient.builder().clientConnector(ClientHttpConnectorFactory.create(options, sslConfiguration)));
	}

	DefaultReactiveVaultClientBuilder(WebClient.Builder builder) {
		this.webClientBuilder = builder.codecs(ReactiveVaultClients::configureCodecs);
	}

	private DefaultReactiveVaultClientBuilder(DefaultReactiveVaultClientBuilder other) {
		this.webClientBuilder = other.webClientBuilder.clone();
		this.endpointProvider = other.endpointProvider;
		this.uriBuilderFactory = other.uriBuilderFactory;
	}

	@Override
	public ReactiveVaultClient.Builder uriBuilderFactory(UriBuilderFactory uriBuilderFactory) {
		this.uriBuilderFactory = uriBuilderFactory;
		return this;
	}

	@Override
	public ReactiveVaultClient.Builder defaultHeader(String header, String... values) {
		webClientBuilder.defaultHeader(header, values);
		return this;
	}

	@Override
	public ReactiveVaultClient.Builder endpoint(VaultEndpoint endpoint) {
		return endpoint(SimpleVaultEndpointProvider.of(endpoint));
	}

	@Override
	@SuppressWarnings("NullAway")
	public ReactiveVaultClient.Builder endpoint(VaultEndpointProvider endpointProvider) {

		Assert.notNull(endpointProvider, "VaultEndpointProvider not be null");

		if (endpointProvider instanceof SimpleVaultEndpointProvider) {

			UriBuilderFactory uriBuilderFactory = VaultClients
					.createUriBuilderFactory(endpointProvider);
			this.webClientBuilder.uriBuilderFactory(uriBuilderFactory);
		} else {
			endpoint(ReactiveVaultClients.wrap(endpointProvider));
			this.webClientBuilder.uriBuilderFactory(null);
		}

		return this;
	}

	@Override
	@SuppressWarnings("NullAway")
	public Builder endpoint(ReactiveVaultEndpointProvider endpointProvider) {
		Assert.notNull(endpointProvider, "ReactiveVaultEndpointProvider not be null");

		if (endpointProvider instanceof VaultEndpointProviderAdapter && ((VaultEndpointProviderAdapter) endpointProvider).getSource() instanceof SimpleVaultEndpointProvider simple) {
			endpoint(simple);
		} else {
			this.webClientBuilder.uriBuilderFactory(null);
		}

		this.endpointProvider = endpointProvider;
		return this;
	}

	@Override
	public ReactiveVaultClient.Builder clientConnector(ClientHttpConnector connector) {
		this.webClientBuilder.clientConnector(connector);
		return this;
	}

	@Override
	public ReactiveVaultClient.Builder configureWebClient(Consumer<WebClient.Builder> restClientBuilderConsumer) {
		restClientBuilderConsumer.accept(webClientBuilder);
		return this;
	}

	@Override
	public ReactiveVaultClient.Builder apply(Consumer<ReactiveVaultClient.Builder> builderConsumer) {
		builderConsumer.accept(this);
		return this;
	}

	@Override
	public ReactiveVaultClient.Builder clone() {
		return new DefaultReactiveVaultClientBuilder(this);
	}

	@Override
	public ReactiveVaultClient build() {
		return new DefaultReactiveVaultClient(this.webClientBuilder.build(), this.endpointProvider, this.uriBuilderFactory, this);
	}
}
