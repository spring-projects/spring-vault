/*
 * Copyright 2025-present the original author or authors.
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

	private final WebClient.Builder builder;

	private @Nullable ReactiveVaultEndpointProvider endpointProvider;

	private @Nullable UriBuilderFactory uriBuilderFactory;


	DefaultReactiveVaultClientBuilder(WebClient webClient) {
		this.builder = webClient.mutate();
	}

	DefaultReactiveVaultClientBuilder() {
		this(new ClientOptions(), SslConfiguration.unconfigured());
	}

	DefaultReactiveVaultClientBuilder(ClientOptions options, SslConfiguration sslConfiguration) {
		this.builder = WebClient.builder()
				.clientConnector(ClientHttpConnectorFactory.create(options, sslConfiguration));
		this.builder.codecs(ReactiveVaultClients::configureCodecs);
	}

	private DefaultReactiveVaultClientBuilder(DefaultReactiveVaultClientBuilder other) {
		this.builder = other.builder.clone();
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
		builder.defaultHeader(header, values);
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
		endpoint(ReactiveVaultClients.wrap(endpointProvider));
		return this;
	}

	@Override
	@SuppressWarnings("NullAway")
	public Builder endpoint(ReactiveVaultEndpointProvider endpointProvider) {

		Assert.notNull(endpointProvider, "ReactiveVaultEndpointProvider not be null");
		this.endpointProvider = endpointProvider;

		if (endpointProvider instanceof ReactiveVaultClients.VaultEndpointProviderAdapter adapter &&
				adapter.getSource() instanceof SimpleVaultEndpointProvider simpleSource) {
			UriBuilderFactory uriBuilderFactory = VaultClients
					.createUriBuilderFactory(simpleSource, false);
			uriBuilderFactory(uriBuilderFactory);
		} else {
			this.uriBuilderFactory = null;
		}

		return this;
	}

	@Override
	public ReactiveVaultClient.Builder clientConnector(ClientHttpConnector connector) {
		this.builder.clientConnector(connector);
		return this;
	}

	@Override
	public ReactiveVaultClient.Builder configureWebClient(Consumer<WebClient.Builder> restClientBuilderConsumer) {
		restClientBuilderConsumer.accept(builder);
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
		return new DefaultReactiveVaultClient(this.builder.build(), this.endpointProvider,
				this.uriBuilderFactory, this);
	}

}
