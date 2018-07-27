/*
 * Copyright 2017-2018 the original author or authors.
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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriTemplateHandler;

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
		return createRestTemplate(SimpleVaultEndpointProvider.of(endpoint),
				requestFactory);
	}

	/**
	 * Create a {@link RestTemplate} configured with {@link VaultEndpointProvider} and
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
	 * @param endpointProvider must not be {@literal null}.
	 * @param requestFactory must not be {@literal null}.
	 * @return the {@link RestTemplate}.
	 * @see org.springframework.http.client.Netty4ClientHttpRequestFactory
	 * @see MappingJackson2HttpMessageConverter
	 * @since 1.1
	 */
	public static RestTemplate createRestTemplate(VaultEndpointProvider endpointProvider,
			ClientHttpRequestFactory requestFactory) {

		RestTemplate restTemplate = createRestTemplate();

		restTemplate.setRequestFactory(requestFactory);
		restTemplate.setUriTemplateHandler(createUriTemplateHandler(endpointProvider));

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

		List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>(
				3);
		messageConverters.add(new ByteArrayHttpMessageConverter());
		messageConverters.add(new StringHttpMessageConverter());
		messageConverters.add(new MappingJackson2HttpMessageConverter());

		RestTemplate restTemplate = new RestTemplate(messageConverters);

		restTemplate.getInterceptors().add(new ClientHttpRequestInterceptor() {

			@Override
			public ClientHttpResponse intercept(HttpRequest request, byte[] body,
					ClientHttpRequestExecution execution) throws IOException {
				return execution.execute(request, body);
			}
		});

		return restTemplate;
	}

	private static DefaultUriTemplateHandler createUriTemplateHandler(
			VaultEndpointProvider endpointProvider) {

		return new PrefixAwareUriTemplateHandler(endpointProvider);
	}

	public static class PrefixAwareUriTemplateHandler extends DefaultUriTemplateHandler {

		private final VaultEndpointProvider endpointProvider;

		public PrefixAwareUriTemplateHandler() {
			this.endpointProvider = null;
		}

		public PrefixAwareUriTemplateHandler(VaultEndpointProvider endpointProvider) {
			this.endpointProvider = endpointProvider;
		}

		@Override
		public URI expand(String uriTemplate, Map<String, ?> uriVariables) {
			return super.expand(prepareUriTemplate(uriTemplate), uriVariables);
		}

		@Override
		public URI expand(String uriTemplate, Object... uriVariableValues) {
			return super.expand(prepareUriTemplate(uriTemplate), uriVariableValues);
		}

		@Override
		public String getBaseUrl() {

			if (endpointProvider != null) {

				VaultEndpoint endpoint = endpointProvider.getVaultEndpoint();

				return endpoint.getScheme() + "://" + endpoint.getHost() + ":"
						+ endpoint.getPort() + "/v1";
			}

			return super.getBaseUrl();
		}

		/**
		 * Strip/add leading slashes from {@code uriTemplate} depending on whetner the
		 * base url has a trailing slash.
		 *
		 * @param uriTemplate
		 * @return
		 */
		private String prepareUriTemplate(String uriTemplate) {

			if (uriTemplate.startsWith("http:") || uriTemplate.startsWith("https:")) {
				return uriTemplate;
			}

			if (getBaseUrl() != null) {
				if (uriTemplate.startsWith("/") && getBaseUrl().endsWith("/")) {
					return uriTemplate.substring(1);
				}

				if (!uriTemplate.startsWith("/") && !getBaseUrl().endsWith("/")) {
					return "/" + uriTemplate;
				}

				return uriTemplate;
			}

			if (!uriTemplate.startsWith("/")) {
				return "/" + uriTemplate;
			}

			return uriTemplate;
		}
	}
}
