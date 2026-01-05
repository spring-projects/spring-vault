/*
 * Copyright 2017-present the original author or authors.
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.vault.support.JacksonCompat;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriBuilderFactory;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Vault Client factory to create {@link RestTemplate} / {@link RestClient}
 * configured to the needs of accessing Vault.
 *
 * @author Mark Paluch
 * @see VaultEndpoint
 * @see RestTemplate
 */
public class VaultClients {

	/**
	 * Create a {@link RestTemplate} configured with {@link VaultEndpoint} and
	 * {@link ClientHttpRequestFactory}. The template accepts relative URIs without
	 * a leading slash that are expanded to use {@link VaultEndpoint}.
	 * {@link RestTemplate} is configured with a
	 * {@link ClientHttpRequestInterceptor} to enforce serialization to a byte array
	 * prior continuing the request. Eager serialization leads to a known request
	 * body size that is required to send a
	 * {@link org.springframework.http.HttpHeaders#CONTENT_LENGTH} request header.
	 * Otherwise, Vault will deny body processing.
	 * <p>Requires Jackson for Object-to-JSON mapping.
	 * @param endpoint must not be {@literal null}.
	 * @param requestFactory must not be {@literal null}.
	 * @return the {@link RestTemplate}.
	 */
	public static RestTemplate createRestTemplate(VaultEndpoint endpoint, ClientHttpRequestFactory requestFactory) {
		return createRestTemplate(SimpleVaultEndpointProvider.of(endpoint), requestFactory);
	}

	/**
	 * Create a {@link RestTemplate} configured with {@link VaultEndpointProvider}
	 * and {@link ClientHttpRequestFactory}. The template accepts relative URIs
	 * without a leading slash that are expanded to use {@link VaultEndpoint}.
	 * {@link RestTemplate} is configured with a
	 * {@link ClientHttpRequestInterceptor} to enforce serialization to a byte array
	 * prior continuing the request. Eager serialization leads to a known request
	 * body size that is required to send a
	 * {@link org.springframework.http.HttpHeaders#CONTENT_LENGTH} request header.
	 * Otherwise, Vault will deny body processing.
	 * <p>Requires Jackson for Object-to-JSON mapping.
	 * @param endpointProvider must not be {@literal null}.
	 * @param requestFactory must not be {@literal null}.
	 * @return the {@link RestTemplate}.
	 * @since 1.1
	 */
	public static RestTemplate createRestTemplate(VaultEndpointProvider endpointProvider,
			ClientHttpRequestFactory requestFactory) {
		RestTemplate restTemplate = createRestTemplate();
		restTemplate.setRequestFactory(requestFactory);
		restTemplate.setUriTemplateHandler(createUriBuilderFactory(endpointProvider));
		return restTemplate;
	}

	/**
	 * Create a {@link org.springframework.web.client.RestClient} configured with
	 * {@link VaultEndpointProvider} and {@link ClientHttpRequestFactory}. The
	 * client accepts relative URIs without a leading slash that are expanded to use
	 * {@link VaultEndpoint}. {@link RestClient} is configured to enforce
	 * serialization to a byte array prior continuing the request. Eager
	 * serialization leads to a known request body size that is required to send a
	 * {@link org.springframework.http.HttpHeaders#CONTENT_LENGTH} request header.
	 * Otherwise, Vault will deny body processing.
	 * <p>Requires Jackson for Object-to-JSON mapping.
	 * @param endpointProvider must not be {@literal null}.
	 * @param requestFactory must not be {@literal null}.
	 * @return the {@link RestTemplate}.
	 * @since 4.0
	 */
	public static RestClient createRestClient(VaultEndpointProvider endpointProvider,
			ClientHttpRequestFactory requestFactory, Consumer<RestClient.Builder> builderCustomizer) {
		RestClient.Builder builder = RestClient.builder()
				.requestFactory(requestFactory)
				.bufferContent((uri, httpMethod) -> true)
				.uriBuilderFactory(createUriBuilderFactory(endpointProvider))
				.configureMessageConverters(clientBuilder -> {
					AbstractHttpMessageConverter<Object> converter = JacksonCompat.instance()
							.createHttpMessageConverter();
					clientBuilder.addCustomConverter(new ByteArrayHttpMessageConverter());
					clientBuilder.addCustomConverter(new StringHttpMessageConverter());
					clientBuilder.addCustomConverter(converter);
					clientBuilder.withJsonConverter(converter);
				});
		builderCustomizer.accept(builder);
		return builder.build();
	}

	/**
	 * Create a {@link RestTemplate} for Vault interaction. {@link RestTemplate} is
	 * configured with a {@link ClientHttpRequestInterceptor} to enforce
	 * serialization to a byte array prior continuing the request. Eager
	 * serialization leads to a known request body size that is required to send a
	 * {@link org.springframework.http.HttpHeaders#CONTENT_LENGTH} request header.
	 * Otherwise, Vault will deny body processing.
	 * <p>Requires Jackson for Object-to-JSON mapping.
	 * @return the {@link RestTemplate}.
	 */
	public static RestTemplate createRestTemplate() {
		List<HttpMessageConverter<?>> messageConverters = new ArrayList<>(3);
		messageConverters.add(new ByteArrayHttpMessageConverter());
		messageConverters.add(new StringHttpMessageConverter());
		messageConverters.add(JacksonCompat.instance().createHttpMessageConverter());
		RestTemplate restTemplate = new RestTemplate(messageConverters);
		restTemplate.getInterceptors().add((request, body, execution) -> execution.execute(request, body));
		return restTemplate;
	}

	/**
	 * Create a {@link ClientHttpRequestInterceptor} that associates each request
	 * with a {@code X-Vault-Namespace} header if the header is not present.
	 * @param namespace the Vault namespace to use. Must not be {@literal null} or
	 * empty.
	 * @return the {@link ClientHttpRequestInterceptor} to register with
	 * {@link RestTemplate}.
	 * @see VaultHttpHeaders#VAULT_NAMESPACE
	 * @since 2.2
	 */
	public static ClientHttpRequestInterceptor createNamespaceInterceptor(String namespace) {
		Assert.hasText(namespace, "Vault Namespace must not be empty!");
		return (request, body, execution) -> {
			HttpHeaders headers = request.getHeaders();
			if (!headers.containsHeader(VaultHttpHeaders.VAULT_NAMESPACE)) {
				headers.add(VaultHttpHeaders.VAULT_NAMESPACE, namespace);
			}
			return execution.execute(request, body);
		};
	}

	public static UriBuilderFactory createUriBuilderFactory(VaultEndpointProvider endpointProvider) {
		return new PrefixAwareUriBuilderFactory(endpointProvider);
	}

	/**
	 * @since 2.0
	 */
	public static class PrefixAwareUriBuilderFactory extends DefaultUriBuilderFactory {

		private final @Nullable VaultEndpointProvider endpointProvider;

		public PrefixAwareUriBuilderFactory() {
			this.endpointProvider = null;
		}

		public PrefixAwareUriBuilderFactory(VaultEndpointProvider endpointProvider) {
			this.endpointProvider = endpointProvider;
		}

		@Override
		public UriBuilder uriString(String uriTemplate) {
			if (uriTemplate.startsWith("http:") || uriTemplate.startsWith("https:")) {
				return UriComponentsBuilder.fromUriString(uriTemplate);
			}
			if (endpointProvider != null) {
				VaultEndpoint endpoint = this.endpointProvider.getVaultEndpoint();

				String baseUri = toBaseUri(endpoint);
				UriComponents uriComponents = UriComponentsBuilder
						.fromUriString(prepareUriTemplate(baseUri, uriTemplate))
						.build();
				return UriComponentsBuilder.fromUriString(baseUri).uriComponents(uriComponents);
			}
			return UriComponentsBuilder.fromUriString(uriTemplate.startsWith("/") ? uriTemplate : "/" + uriTemplate);
		}

	}

	private static String toBaseUri(VaultEndpoint endpoint) {
		return "%s://%s:%s/%s".formatted(endpoint.getScheme(), endpoint.getHost(), endpoint.getPort(),
				endpoint.getPath());
	}

	/**
	 * Strip/add leading slashes from {@code uriTemplate} depending on whether the
	 * base url has a trailing slash.
	 * @param uriTemplate
	 * @return
	 */
	static String prepareUriTemplate(@Nullable String baseUrl, String uriTemplate) {
		if (uriTemplate.startsWith("http:") || uriTemplate.startsWith("https:")) {
			return uriTemplate;
		}
		if (baseUrl != null) {
			return normalizePath(baseUrl, uriTemplate);
		}
		try {
			URI uri = URI.create(uriTemplate);
			if (uri.getHost() != null) {
				return uriTemplate;
			}
		} catch (IllegalArgumentException ignored) {
		}
		if (!uriTemplate.startsWith("/")) {
			return "/" + uriTemplate;
		}

		return uriTemplate;
	}

	/**
	 * Normalize the URI {@code path} so that it can be combined with
	 * {@code prefix}.
	 * @param prefix
	 * @param path
	 * @return
	 */
	static String normalizePath(String prefix, String path) {
		if (path.startsWith("/") && prefix.endsWith("/")) {
			return path.substring(1);
		}
		if (!path.startsWith("/") && !prefix.endsWith("/")) {
			return "/" + path;
		}
		return path;
	}

}
