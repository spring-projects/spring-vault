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
import org.springframework.http.converter.HttpMessageConverters;
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
	 * @return the {@link RestClient}.
	 * @since 4.0
	 */
	public static RestClient createRestClient(VaultEndpointProvider endpointProvider,
			ClientHttpRequestFactory requestFactory, Consumer<RestClient.Builder> builderCustomizer) {
		RestClient.Builder builder = RestClient.builder()
				.requestFactory(requestFactory)
				.bufferContent((uri, httpMethod) -> true)
				.uriBuilderFactory(createUriBuilderFactory(endpointProvider))
				.configureMessageConverters(VaultClients::configureConverters);
		builderCustomizer.accept(builder);
		return builder.build();
	}

	/**
	 * Configure {@link HttpMessageConverter}s for Vault interaction. The used
	 * converters are:
	 * <ul>
	 * <li>{@link ByteArrayHttpMessageConverter}</li>
	 * <li>{@link StringHttpMessageConverter}</li>
	 * <li>If Jackson 3 is on the class path:
	 * {@link org.springframework.http.converter.json.JacksonJsonHttpMessageConverter}</li>
	 * <li>Alternatively, if Jackson 2 is on the class path:
	 * {@link org.springframework.http.converter.json.MappingJackson2HttpMessageConverter}</li>
	 * </ul>
	 * @since 4.1
	 */
	public static void configureConverters(HttpMessageConverters.ClientBuilder clientBuilder) {
		configureConverters(clientBuilder::addCustomConverter);
	}

	/**
	 * Configure {@link HttpMessageConverter}s for Vault interaction. The used
	 * converters are:
	 * <ul>
	 * <li>{@link ByteArrayHttpMessageConverter}</li>
	 * <li>{@link StringHttpMessageConverter}</li>
	 * <li>If Jackson 3 is on the class path:
	 * {@link org.springframework.http.converter.json.JacksonJsonHttpMessageConverter}</li>
	 * <li>Alternatively, if Jackson 2 is on the class path:
	 * {@link org.springframework.http.converter.json.MappingJackson2HttpMessageConverter}</li>
	 * </ul>
	 * @since 4.1
	 */
	public static void configureConverters(Consumer<? super HttpMessageConverter<?>> converterConsumer) {
		AbstractHttpMessageConverter<Object> converter = JacksonCompat.instance().createHttpMessageConverter();
		converterConsumer.accept(new ByteArrayHttpMessageConverter());
		converterConsumer.accept(new StringHttpMessageConverter());
		converterConsumer.accept(converter);
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
	 * @deprecated since 4.1 in favor of {@link VaultClient}.
	 */
	@Deprecated(since = "4.1")
	public static RestTemplate createRestTemplate() {
		List<HttpMessageConverter<?>> messageConverters = new ArrayList<>(3);
		configureConverters(messageConverters::add);
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
	 * @deprecated since 4.1 in favor of
	 * {@link VaultClient.Builder#defaultNamespace(String)} and
	 * {@link org.springframework.vault.client.VaultClient.RequestHeadersSpec#namespace(String)}.
	 */
	@Deprecated(since = "4.1")
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
		return new PrefixAwareUriBuilderFactory(endpointProvider, true);
	}

	static UriBuilderFactory createUriBuilderFactory(VaultEndpointProvider endpointProvider,
			boolean allowAbsolutePath) {
		if (!allowAbsolutePath && endpointProvider instanceof SimpleVaultEndpointProvider) {
			return createUriBuilderFactory(endpointProvider.getVaultEndpoint());
		}
		return new PrefixAwareUriBuilderFactory(endpointProvider, allowAbsolutePath);
	}

	static UriBuilderFactory createUriBuilderFactory(VaultEndpoint endpoint) {
		return new VaultEndpointUriBuilderFactorySupport(endpoint);
	}

	static URI expandUri(UriBuilderFactory factory, URI uri) {
		return expandUri(factory.expand(""), uri);
	}

	static URI expandUri(VaultEndpoint endpoint, URI uri) {
		return expandUri(endpoint.createUri(""), uri);
	}

	static URI expandUri(URI base, URI uri) {
		if (uri.isAbsolute()) {
			return uri;
		}
		return UriComponentsBuilder.fromUri(base).path(uri.getPath()).query(uri.getQuery()).buildAndExpand().toUri();
	}

	/**
	 * @since 4.1
	 */
	public static class VaultEndpointUriBuilderFactorySupport extends DefaultUriBuilderFactory {

		private final UriComponentsBuilder builder;


		public VaultEndpointUriBuilderFactorySupport(VaultEndpoint endpoint) {
			super();
			this.builder = UriComponentsBuilder.fromUriString(toBaseUri(endpoint));
		}


		@Override
		public UriComponentsBuilder builder() {
			return builder.cloneBuilder();
		}

		@Override
		public UriBuilder uriString(String uriTemplate) {
			UriComponents uriComponents = builder()
					.path(VaultEndpoint.stripLeadingSlashes(uriTemplate))
					.build();
			return UriComponentsBuilder.newInstance().uriComponents(uriComponents);
		}

	}

	/**
	 * @since 2.0
	 */
	public static class PrefixAwareUriBuilderFactory extends DefaultUriBuilderFactory {

		private final @Nullable VaultEndpointProvider endpointProvider;

		private final boolean allowAbsolutePath;


		public PrefixAwareUriBuilderFactory() {
			this.endpointProvider = null;
			this.allowAbsolutePath = true;
		}

		public PrefixAwareUriBuilderFactory(VaultEndpointProvider endpointProvider) {
			this(endpointProvider, true);
		}

		PrefixAwareUriBuilderFactory(VaultEndpointProvider endpointProvider, boolean allowAbsolutePath) {
			this.endpointProvider = endpointProvider;
			this.allowAbsolutePath = allowAbsolutePath;
		}


		@Override
		public UriBuilder uriString(String uriTemplate) {
			if (allowAbsolutePath || endpointProvider == null) {
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
			}
			return getUriComponents(endpointProvider != null ? endpointProvider.getVaultEndpoint() : null, uriTemplate);
		}

	}

	static UriComponentsBuilder getUriComponents(@Nullable VaultEndpoint endpoint, String uriTemplate) {
		String normalizedUriTemplate = uriTemplate.startsWith("/") ? uriTemplate : "/" + uriTemplate;
		if (endpoint != null) {
			UriComponents uriComponents = UriComponentsBuilder.fromUriString(toBaseUri(endpoint))
					.path(normalizedUriTemplate)
					.build();
			return UriComponentsBuilder.newInstance().uriComponents(uriComponents);
		}
		return UriComponentsBuilder.fromUriString(normalizedUriTemplate);
	}

	private static String toBaseUri(VaultEndpoint endpoint) {
		return endpoint.createUriString("");
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
		return VaultEndpoint.stripLeadingSlashes(uriTemplate);
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
