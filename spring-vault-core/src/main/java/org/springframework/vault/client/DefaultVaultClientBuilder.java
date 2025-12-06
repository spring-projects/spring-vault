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
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriBuilderFactory;

/**
 * Default implementation of {@link VaultClient.Builder}.
 *
 * @author Mark Paluch
 * @since 4.1
 */
class DefaultVaultClientBuilder implements VaultClient.Builder {

	private final RestClient.Builder builder;

	private @Nullable VaultEndpointProvider endpointProvider;

	private @Nullable UriBuilderFactory uriBuilderFactory;


	DefaultVaultClientBuilder(RestTemplate restTemplate) {
		this.builder = RestClient.builder(restTemplate);
	}

	DefaultVaultClientBuilder(RestClient restClient) {
		this.builder = restClient.mutate();
	}

	DefaultVaultClientBuilder() {
		this(new ClientOptions(), SslConfiguration.unconfigured());
	}

	DefaultVaultClientBuilder(ClientOptions options, SslConfiguration sslConfiguration) {
		this.builder = RestClient.builder()
				.requestFactory(ClientHttpRequestFactoryFactory.create(options, sslConfiguration));
		this.builder.configureMessageConverters(VaultClients::configureConverters);
	}

	private DefaultVaultClientBuilder(DefaultVaultClientBuilder other) {
		this.builder = other.builder.clone();
		this.endpointProvider = other.endpointProvider;
		this.uriBuilderFactory = other.uriBuilderFactory;
	}


	@Override
	public VaultClient.Builder uriBuilderFactory(UriBuilderFactory uriBuilderFactory) {
		this.uriBuilderFactory = uriBuilderFactory;
		return this;
	}

	@Override
	public VaultClient.Builder defaultHeader(String header, String... values) {
		builder.defaultHeader(header, values);
		return this;
	}

	@Override
	public VaultClient.Builder endpoint(VaultEndpoint endpoint) {
		return endpoint(SimpleVaultEndpointProvider.of(endpoint));
	}

	@Override
	public VaultClient.Builder endpoint(VaultEndpointProvider endpointProvider) {
		Assert.notNull(endpointProvider, "VaultEndpointProvider not be null");
		this.endpointProvider = endpointProvider;
		uriBuilderFactory(VaultClients
				.createUriBuilderFactory(endpointProvider, false));
		return this;
	}

	@Override
	public VaultClient.Builder requestFactory(ClientHttpRequestFactory requestFactory) {
		this.builder.requestFactory(requestFactory);
		return this;
	}

	@Override
	public VaultClient.Builder configureRestClient(Consumer<RestClient.Builder> restClientBuilderConsumer) {
		restClientBuilderConsumer.accept(builder);
		return this;
	}

	@Override
	public VaultClient.Builder apply(Consumer<VaultClient.Builder> builderConsumer) {
		builderConsumer.accept(this);
		return this;
	}

	@Override
	public VaultClient.Builder clone() {
		return new DefaultVaultClientBuilder(this);
	}

	@Override
	public VaultClient build() {
		return new DefaultVaultClient(this.builder.build(), this.uriBuilderFactory, this);
	}

}
