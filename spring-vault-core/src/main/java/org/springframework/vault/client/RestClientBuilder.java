/*
 * Copyright 2019-present the original author or authors.
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

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.util.Assert;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Builder that can be used to configure and create a {@link RestClient}.
 * Provides convenience methods to configure
 * {@link #requestFactory(ClientHttpRequestFactory) ClientHttpRequestFactory},
 * {@link #errorHandler(ResponseErrorHandler) error handlers} and
 * {@link #defaultHeader(String, String) default headers}.
 * <p>By default, the built {@link RestClient} will attempt to use the most
 * suitable {@link ClientHttpRequestFactory} using
 * {@link ClientHttpRequestFactoryFactory#create}.
 *
 * @author Mark Paluch
 * @see ClientHttpRequestFactoryFactory
 * @see RestClientCustomizer
 * @since 4.0
 */
public class RestClientBuilder {

	@Nullable
	VaultEndpointProvider endpointProvider;

	Supplier<ClientHttpRequestFactory> requestFactory = () -> ClientHttpRequestFactoryFactory
			.create(new ClientOptions(), SslConfiguration.unconfigured());

	@Nullable
	ResponseErrorHandler errorHandler;

	final Map<String, String> defaultHeaders = new LinkedHashMap<>();

	final List<RestClientCustomizer> customizers = new ArrayList<>();

	final Set<ClientHttpRequestInitializer> requestInitializers = new LinkedHashSet<>();


	private RestClientBuilder() {
	}

	private RestClientBuilder(@Nullable VaultEndpointProvider endpointProvider,
			Supplier<ClientHttpRequestFactory> requestFactory, @Nullable ResponseErrorHandler errorHandler) {
		this.endpointProvider = endpointProvider;
		this.requestFactory = requestFactory;
		this.errorHandler = errorHandler;
	}


	/**
	 * Create a new {@code RestClientBuilder}.
	 * @return a new {@code RestClientBuilder}.
	 */
	public static RestClientBuilder builder() {
		return new RestClientBuilder();
	}

	/**
	 * Create a new {@link RestClientBuilder} initialized from
	 * {@link RestTemplateBuilder}.
	 * @return a new {@link RestClientBuilder}.
	 * @since 4.1
	 */
	public static RestClientBuilder builder(RestTemplateBuilder restTemplateBuilder) {
		RestClientBuilder builder = new RestClientBuilder(restTemplateBuilder.endpointProvider,
				restTemplateBuilder.requestFactory, restTemplateBuilder.errorHandler);
		builder.defaultHeaders.putAll(restTemplateBuilder.defaultHeaders);
		builder.requestInitializers.addAll(restTemplateBuilder.requestCustomizers);
		return builder;
	}


	/**
	 * Set the {@link VaultEndpoint} that should be used with the
	 * {@link RestClient}.
	 * @param endpoint the {@link VaultEndpoint} provider.
	 * @return this builder.
	 */
	public RestClientBuilder endpoint(VaultEndpoint endpoint) {
		return endpointProvider(SimpleVaultEndpointProvider.of(endpoint));
	}

	/**
	 * Set the {@link VaultEndpointProvider} that should be used with the
	 * {@link RestClient}.
	 * @param provider the {@link VaultEndpoint} provider.
	 * @return this builder.
	 */
	public RestClientBuilder endpointProvider(VaultEndpointProvider provider) {
		Assert.notNull(provider, "VaultEndpointProvider must not be null");
		this.endpointProvider = provider;
		return this;
	}

	/**
	 * Set the {@link ClientHttpRequestFactory} that should be used with the
	 * {@link RestClient}.
	 * @param requestFactory the request factory.
	 * @return this builder.
	 */
	public RestClientBuilder requestFactory(ClientHttpRequestFactory requestFactory) {
		Assert.notNull(requestFactory, "ClientHttpRequestFactory must not be null");
		return requestFactory(() -> requestFactory);
	}

	/**
	 * Set the {@link Supplier} of {@link ClientHttpRequestFactory} that should be
	 * called each time we {@link #build()} a new {@link RestClient} instance.
	 * @param requestFactory the supplier for the request factory.
	 * @return this builder.
	 */
	public RestClientBuilder requestFactory(Supplier<ClientHttpRequestFactory> requestFactory) {
		Assert.notNull(requestFactory, "Supplier of ClientHttpRequestFactory must not be null");
		this.requestFactory = requestFactory;
		return this;
	}

	/**
	 * Set the {@link ResponseErrorHandler} that should be used with the
	 * {@link RestClient}.
	 * @param errorHandler the error handler to use.
	 * @return this builder.
	 */
	public RestClientBuilder errorHandler(ResponseErrorHandler errorHandler) {
		Assert.notNull(errorHandler, "ErrorHandler must not be null");
		this.errorHandler = errorHandler;
		return this;
	}

	/**
	 * Add a default header that will be set if not already present on the outgoing
	 * {@link HttpRequest}.
	 * @param name the name of the header.
	 * @param value the header value.
	 * @return this builder.
	 */
	public RestClientBuilder defaultHeader(String name, String value) {
		Assert.hasText(name, "Header name must not be null or empty");
		this.defaultHeaders.put(name, value);
		return this;
	}

	/**
	 * Add the {@link RestClientCustomizer RestClientCustomizers} that should be
	 * applied to the {@link RestClient}. Customizers are applied in the order that
	 * they were added.
	 * @param customizer the template customizers to add.
	 * @return this builder.
	 */
	public RestClientBuilder customizers(RestClientCustomizer... customizer) {
		this.customizers.addAll(Arrays.asList(customizer));
		return this;
	}

	/**
	 * Add the {@link ClientHttpRequestInitializer ClientHttpRequestInitializers}
	 * that should be applied to {@link ClientHttpRequest} initialization.
	 * Customizers are applied in the order that they were added.
	 * @param requestInitializer the request initializers to add.
	 * @return this builder.
	 */
	public RestClientBuilder requestInitializers(ClientHttpRequestInitializer... requestInitializer) {
		Assert.notNull(requestInitializer, "RequestCustomizers must not be null");
		this.requestInitializers.addAll(Arrays.asList(requestInitializer));
		return this;
	}

	/**
	 * Build a new {@link RestClient}. {@link VaultEndpoint} must be set.
	 * <p>Applies also {@link ResponseErrorHandler} and
	 * {@link RestTemplateCustomizer} if configured.
	 * @return a new {@link RestClient}.
	 */
	public RestClient build() {
		Assert.state(this.endpointProvider != null, "VaultEndpointProvider must not be null");
		return createClient();
	}

	/**
	 * Create the {@link RestClient} to use.
	 * @return the {@link RestClient} to use.
	 */
	protected RestClient createClient() {
		Assert.notNull(this.endpointProvider, "VaultEndpointProvider must not be null");
		ClientHttpRequestFactory requestFactory = this.requestFactory.get();
		LinkedHashMap<String, String> defaultHeaders = new LinkedHashMap<>(this.defaultHeaders);
		return VaultClients.createRestClient(this.endpointProvider, requestFactory, builder -> {
			builder.defaultHeaders(headers -> {
				defaultHeaders.forEach((key, value) -> {
					if (!headers.containsHeader(key)) {
						headers.add(key, value);
					}
				});
			});
			builder.requestInitializers(
					clientHttpRequestInitializers -> clientHttpRequestInitializers.addAll(requestInitializers));
			if (this.errorHandler != null) {
				builder.defaultStatusHandler(this.errorHandler);
			}
			this.customizers.forEach(customizer -> customizer.customize(builder));
		});
	}

}
