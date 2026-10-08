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

		public PrefixAwareUriBuilderFactory() {
			this.endpointProvider = null;
		}

		public PrefixAwareUriBuilderFactory(VaultEndpointProvider endpointProvider) {
			this.endpointProvider = endpointProvider;
		}

		@Override
		public UriBuilder uriString(String uriTemplate) {
			validatePathSegment(uriTemplate);
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
