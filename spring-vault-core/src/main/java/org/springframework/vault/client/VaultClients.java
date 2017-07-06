/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.Map;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.DefaultUriTemplateHandler;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriBuilderFactory;

/**
 * Vault Client factory to create {@link RestTemplate} configured to the needs of
 * accessing Vault.
 *
 * @author Mark Paluch
 * @see VaultEndpoint
 * @see RestTemplate
 */
public class VaultClients {

	/**
	 * Create a {@link RestTemplate} configured with {@link VaultEndpoint} and
	 * {@link ClientHttpRequestFactory}. The template accepts relative URIs without a
	 * leading slash that are expanded to use {@link VaultEndpoint}. {@link RestTemplate}
	 * is configured with a {@link ClientHttpRequestInterceptor} to enforce serialization
	 * to a byte array prior continuing the request. Eager serialization leads to a known
	 * request body size that is required to send a
	 * {@link org.springframework.http.HttpHeaders#CONTENT_LENGTH} request header.
	 * Otherwise, Vault will deny body processing.
	 * <p>
	 * Requires Jackson 2 for Object-to-JSON mapping.
	 *
	 * @param endpoint must not be {@literal null}.
	 * @param requestFactory must not be {@literal null}.
	 * @return the {@link RestTemplate}.
	 * @see org.springframework.http.client.Netty4ClientHttpRequestFactory
	 * @see MappingJackson2HttpMessageConverter
	 */
	public static RestTemplate createRestTemplate(VaultEndpoint endpoint,
			ClientHttpRequestFactory requestFactory) {

		RestTemplate restTemplate = createRestTemplate();

		restTemplate.setRequestFactory(requestFactory);
		restTemplate.setUriTemplateHandler(createUriTemplateHandler(endpoint));

		return restTemplate;
	}

	/**
	 * Create a {@link RestTemplate} for Vault interaction. {@link RestTemplate} is
	 * configured with a {@link ClientHttpRequestInterceptor} to enforce serialization to
	 * a byte array prior continuing the request. Eager serialization leads to a known
	 * request body size that is required to send a
	 * {@link org.springframework.http.HttpHeaders#CONTENT_LENGTH} request header.
	 * Otherwise, Vault will deny body processing.
	 * <p>
	 * Requires Jackson 2 for Object-to-JSON mapping.
	 *
	 * @return the {@link RestTemplate}.
	 * @see org.springframework.http.client.Netty4ClientHttpRequestFactory
	 * @see MappingJackson2HttpMessageConverter
	 */
	public static RestTemplate createRestTemplate() {

		List<HttpMessageConverter<?>> messageConverters = new ArrayList<>(3);
		messageConverters.add(new ByteArrayHttpMessageConverter());
		messageConverters.add(new StringHttpMessageConverter());
		messageConverters.add(new MappingJackson2HttpMessageConverter());

		RestTemplate restTemplate = new RestTemplate(messageConverters);

		restTemplate.getInterceptors().add(
				(request, body, execution) -> execution.execute(request, body));

		return restTemplate;
	}

	private static DefaultUriTemplateHandler createUriTemplateHandler(
			VaultEndpoint endpoint) {

		String baseUrl = String.format("%s://%s:%s/%s/", endpoint.getScheme(),
				endpoint.getHost(), endpoint.getPort(), "v1");

		DefaultUriTemplateHandler defaultUriTemplateHandler = new PrefixAwareUriTemplateHandler();
		defaultUriTemplateHandler.setBaseUrl(baseUrl);
		return defaultUriTemplateHandler;
	}

	public static UriBuilderFactory createUriBuilderFactory(VaultEndpoint endpoint) {

		String baseUrl = String.format("%s://%s:%s/%s/", endpoint.getScheme(),
				endpoint.getHost(), endpoint.getPort(), "v1");

		return new PrefixAwareUriBuilderFactory(baseUrl);
	}

	public static class PrefixAwareUriTemplateHandler extends DefaultUriTemplateHandler {

		@Override
		protected URI expandInternal(String uriTemplate, Map<String, ?> uriVariables) {
			return super.expandInternal(prepareUriTemplate(getBaseUrl(), uriTemplate),
					uriVariables);
		}

		@Override
		protected URI expandInternal(String uriTemplate, Object... uriVariables) {
			return super.expandInternal(prepareUriTemplate(getBaseUrl(), uriTemplate),
					uriVariables);
		}
	}

	/**
	 * @since 2.0
	 */
	public static class PrefixAwareUriBuilderFactory extends DefaultUriBuilderFactory {

		private final String baseUri;

		public PrefixAwareUriBuilderFactory(String baseUri) {
			super(baseUri);
			this.baseUri = baseUri;
		}

		@Override
		public UriBuilder uriString(String uriTemplate) {
			return super.uriString(prepareUriTemplate(baseUri, uriTemplate));
		}
	}

	/**
	 * Strip/add leading slashes from {@code uriTemplate} depending on wheter the base url
	 * has a trailing slash.
	 *
	 * @param uriTemplate
	 * @return
	 */
	static String prepareUriTemplate(String baseUrl, String uriTemplate) {

		if (baseUrl != null) {
			if (uriTemplate.startsWith("/") && baseUrl.endsWith("/")) {
				return uriTemplate.substring(1);
			}

			if (!uriTemplate.startsWith("/") && !baseUrl.endsWith("/")) {
				return "/" + uriTemplate;
			}

			return uriTemplate;
		}

		try {
			URI uri = URI.create(uriTemplate);

			if (uri.getHost() != null) {
				return uriTemplate;
			}
		}
		catch (IllegalArgumentException e) {
		}

		if (!uriTemplate.startsWith("/")) {
			return "/" + uriTemplate;
		}

		return uriTemplate;
	}
}
