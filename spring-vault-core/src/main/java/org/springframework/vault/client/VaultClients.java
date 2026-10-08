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
import java.util.Locale;
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
import org.springframework.util.StringUtils;
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
		return new VaultEndpointUriBuilderFactory(endpoint);
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
	public static class VaultEndpointUriBuilderFactory extends DefaultUriBuilderFactory {

		private final UriComponentsBuilder builder;


		public VaultEndpointUriBuilderFactory(VaultEndpoint endpoint) {
			super();
			this.builder = UriComponentsBuilder.fromUriString(toBaseUri(endpoint));
		}


		@Override
		public UriComponentsBuilder builder() {
			return builder.cloneBuilder();
		}

		@Override
		public UriBuilder uriString(String uriTemplate) {
			validatePathSegment(uriTemplate);
			UriComponents components = parseUri(uriTemplate).build();
			if (components.toUri().isAbsolute() || StringUtils.hasText(components.getHost())
					|| components.getPort() != -1) {
				return builder().path(uriTemplate);
			}
			return builder().uriComponents(components);
		}

		private UriComponentsBuilder parseUri(String uriTemplate) {
			return (getParserType() != null ? UriComponentsBuilder.fromUriString(uriTemplate, getParserType())
					: UriComponentsBuilder.fromUriString(uriTemplate));
		}

	}

	/**
	 * Concatenate and validate the given path segments using {@code /} as
	 * separator.
	 * @param segments path segments to validate and join. Must not be
	 * {@literal null} and must not contain {@literal null} elements.
	 * @return the concatenated, validated path.
	 * @throws IllegalArgumentException if any segment contains path traversal
	 * sequences.
	 * @since 3.1.5
	 */
	public static String concatPath(String... segments) {
		Assert.notNull(segments, "Path segments must not be null");
		StringBuilder builder = new StringBuilder();
		for (String segment : segments) {
			if (!builder.isEmpty()) {
				builder.append('/');
			}
			builder.append(validateConcat(normalizePath(segment)));
		}

		return builder.toString();
	}

	/**
	 * Validate the given path segment to not contain any path traversal
	 * sequences.
	 * @param segment path segment to validate.
	 * @return the segment, unchanged.
	 * @throws IllegalArgumentException if the segment violates a rule listed in
	 * the class-level Javadoc.
	 * @since 3.1.5
	 */
	public static String validatePathSegment(String segment) {
		Assert.notNull(segment, "Path segment must not be null");
		for (int i = 0; i < segment.length(); i++) {
			char c = segment.charAt(i);
			if (isControlChar(c)) {
				throw new IllegalArgumentException(
						"Path segment must not contain control characters: '%s'".formatted(segment));
			}
		}

		return validateSegment(segment);
	}

	/**
	 * Validate the given path segment to not contain any path traversal
	 * sequences.
	 */
	private static String validateConcat(String segment) {
		Assert.notNull(segment, "Path segment must not be null");
		for (int i = 0; i < segment.length(); i++) {
			char c = segment.charAt(i);
			if (isControlChar(c) || c == '?' || c == '#') {
				throw new IllegalArgumentException(
						"Path segment must not contain control characters: '%s'".formatted(segment));
			}
		}

		return validateSegment(segment);
	}

	private static boolean isControlChar(char c) {
		return c < 0x20 || c == 0x7f || c == '\\';
	}

	private static String validateSegment(String segment) {
		String lower = segment.toLowerCase(Locale.ROOT);
		if (lower.contains("%2e")) {
			throw new IllegalArgumentException("Encoded '.' (%%2e) is not allowed in path: '%s'".formatted(segment));
		}
		if (lower.contains("%2f")) {
			throw new IllegalArgumentException("Encoded '/' (%%2f) is not allowed in path: '%s'".formatted(segment));
		}
		if (lower.contains("%5c")) {
			throw new IllegalArgumentException("Encoded '\\\\' (%%5c) is not allowed in path: '%s'".formatted(segment));
		}
		if (lower.startsWith("../") || lower.endsWith("/..") || lower.contains("/../") || lower.equals("..")
				|| lower.contains("..?")) {
			throw new IllegalArgumentException("Traversal '..' is not allowed in path: '%s'".formatted(segment));
		}

		return segment;
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


		@Nullable
		VaultEndpointProvider getEndpointProvider() {
			return this.endpointProvider;
		}

		@Override
		public UriBuilder uriString(String uriTemplate) {
			validatePathSegment(uriTemplate);
			VaultEndpointProvider endpointProvider = getEndpointProvider();
			if (allowAbsolutePath || endpointProvider == null) {
				if (uriTemplate.startsWith("http:") || uriTemplate.startsWith("https:")) {
					return UriComponentsBuilder.fromUriString(uriTemplate);
				}
				if (endpointProvider != null) {
					VaultEndpoint endpoint = endpointProvider.getVaultEndpoint();
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
		validatePathSegment(uriTemplate);
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

	private static String normalizePath(String path) {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}

		return path;
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
