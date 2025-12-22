/*
 * Copyright 2019-2025 the original author or authors.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builder that can be used to configure and create a {@link WebClient}.
 * Provides convenience methods to configure
 * {@link #httpConnector(ClientHttpConnector) ClientHttpConnector} and
 * {@link #defaultHeader(String, String) default headers}.
 *
 * By default the built {@link WebClient} will attempt to use the most suitable
 * {@link ClientHttpConnector} using {@link ClientHttpConnectorFactory#create}.
 *
 * @author Mark Paluch
 * @since 2.2
 * @see ClientHttpConnectorFactory
 * @see WebClientCustomizer
 */
public class WebClientBuilder {

	private @Nullable ReactiveVaultEndpointProvider endpointProvider;

	private Supplier<ClientHttpConnector> httpConnector = () -> ClientHttpConnectorFactory.create(new ClientOptions(),
			SslConfiguration.unconfigured());

	private final Map<String, String> defaultHeaders = new LinkedHashMap<>();

	private final List<WebClientCustomizer> customizers = new ArrayList<>();

	private final Set<ExchangeFilterFunction> filterFunctions = new LinkedHashSet<>();


	private WebClientBuilder() {
	}


	/**
	 * Create a new {@link WebClientBuilder}.
	 * @return a new {@link WebClientBuilder}.
	 */
	public static WebClientBuilder builder() {
		return new WebClientBuilder();
	}


	/**
	 * Set the {@link VaultEndpoint} that should be used with the {@link WebClient}.
	 * @param endpoint the {@link VaultEndpoint} provider.
	 * @return this builder.
	 */
	public WebClientBuilder endpoint(VaultEndpoint endpoint) {
		return endpointProvider(SimpleVaultEndpointProvider.of(endpoint));
	}

	/**
	 * Set the {@link VaultEndpointProvider} that should be used with the
	 * {@link WebClient}. {@link VaultEndpointProvider#getVaultEndpoint()} is called
	 * on {@link reactor.core.scheduler.Schedulers#boundedElastic() a dedicated
	 * Thread} to ensure that I/O threads are never blocked.
	 * @param provider the {@link VaultEndpoint} provider.
	 * @return this builder.
	 */
	public WebClientBuilder endpointProvider(VaultEndpointProvider provider) {
		return endpointProvider(ReactiveVaultClients.wrap(provider));
	}

	/**
	 * Set the {@link ReactiveVaultEndpointProvider} that should be used with the
	 * {@link WebClient}.
	 * @param provider the {@link VaultEndpoint} provider.
	 * @return this builder.
	 */
	public WebClientBuilder endpointProvider(ReactiveVaultEndpointProvider provider) {
		Assert.notNull(provider, "ReactiveVaultEndpointProvider must not be null");
		this.endpointProvider = provider;
		return this;
	}

	/**
	 * Set the {@link ClientHttpConnector} that should be used with the
	 * {@link WebClient}.
	 * @param httpConnector the HTTP connector.
	 * @return this builder.
	 */
	public WebClientBuilder httpConnector(ClientHttpConnector httpConnector) {
		Assert.notNull(httpConnector, "ClientHttpConnector must not be null");
		return httpConnectorFactory(() -> httpConnector);
	}

	/**
	 * Set the {@link Supplier} of {@link ClientHttpConnector} that should be called
	 * each time we {@link #build()} a new {@link WebClient} instance.
	 * @param httpConnector the supplier for the HTTP connector.
	 * @return this builder.
	 * @since 2.2.1
	 */
	public WebClientBuilder httpConnectorFactory(Supplier<ClientHttpConnector> httpConnector) {
		Assert.notNull(httpConnector, "Supplier of ClientHttpConnector must not be null");
		this.httpConnector = httpConnector;
		return this;
	}

	/**
	 * Add a default header that will be set if not already present on the outgoing
	 * {@link HttpRequest}.
	 * @param name the name of the header.
	 * @param value the header value.
	 * @return this builder.
	 */
	public WebClientBuilder defaultHeader(String name, String value) {
		Assert.hasText(name, "Header name must not be null or empty");
		this.defaultHeaders.put(name, value);
		return this;
	}

	/**
	 * Add the {@link WebClientCustomizer WebClientCustomizers} that should be
	 * applied to the {@link WebClient}. Customizers are applied in the order that
	 * they were added.
	 * @param customizer the client customizers to add.
	 * @return this builder.
	 */
	public WebClientBuilder customizers(WebClientCustomizer... customizer) {
		this.customizers.addAll(Arrays.asList(customizer));
		return this;
	}

	/**
	 * Add the {@link ExchangeFilterFunction ExchangeFilterFunctions} that should be
	 * applied to the {@link ClientRequest}. {@link ExchangeFilterFunction}s are
	 * applied in the order that they were added.
	 * @param filterFunctions the request customizers to add.
	 * @return this builder.
	 */
	public WebClientBuilder filter(ExchangeFilterFunction... filterFunctions) {
		Assert.notNull(filterFunctions, "ExchangeFilterFunctions must not be null");
		this.filterFunctions.addAll(Arrays.asList(filterFunctions));
		return this;
	}

	/**
	 * Build a new {@link WebClient}. {@link VaultEndpoint} must be set.
	 *
	 * Applies also {@link ExchangeFilterFunction} and {@link WebClientCustomizer}
	 * if configured.
	 * @return a new {@link WebClient}.
	 */
	public WebClient build() {
		WebClient.Builder builder = createWebClientBuilder();
		if (!this.defaultHeaders.isEmpty()) {
			Map<String, String> defaultHeaders = this.defaultHeaders;
			builder.filter((request, next) -> {
				return next
						.exchange(
								ClientRequest.from(request).headers(headers -> defaultHeaders.forEach((key, value) -> {
									if (!headers.containsKey(key)) {
										headers.add(key, value);
									}
								})).build());
			});
		}

		builder.filters(exchangeFilterFunctions -> exchangeFilterFunctions.addAll(this.filterFunctions));
		this.customizers.forEach(customizer -> customizer.customize(builder));
		return builder.build();
	}

	/**
	 * Create the {@link WebClient.Builder} to use.
	 * @return the {@link WebClient.Builder} to use.
	 */
	protected WebClient.Builder createWebClientBuilder() {
		Assert.state(this.endpointProvider != null, "VaultEndpointProvider must not be null");
		ClientHttpConnector connector = this.httpConnector.get();
		return ReactiveVaultClients.createWebClientBuilder(this.endpointProvider, connector);
	}

}
