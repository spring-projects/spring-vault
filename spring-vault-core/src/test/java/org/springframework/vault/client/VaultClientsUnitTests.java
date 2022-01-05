/*
 * Copyright 2018-2022 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.vault.client.VaultClients.PrefixAwareUriBuilderFactory;
import org.springframework.vault.client.VaultClients.PrefixAwareUriTemplateHandler;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link org.springframework.vault.client.VaultClients}.
 *
 * @author Mark Paluch
 */
class VaultClientsUnitTests {

	@Test
	void uriHandlerShouldPrefixRelativeUrl() {

		VaultEndpoint localhost = VaultEndpoint.create("localhost", 8200);
		PrefixAwareUriTemplateHandler handler = new PrefixAwareUriTemplateHandler(() -> localhost);

		URI uri = handler.expand("/path/{bar}", "bar");

		assertThat(uri).hasHost("localhost").hasPort(8200).hasPath("/v1/path/bar");
	}

	@Test
	void uriHandlerShouldNotPrefixAbsoluteUrl() {

		VaultEndpoint localhost = VaultEndpoint.create("localhost", 8200);
		PrefixAwareUriTemplateHandler handler = new PrefixAwareUriTemplateHandler(() -> localhost);

		URI uri = handler.expand("https://foo/path/{bar}", "bar");

		assertThat(uri).hasScheme("https").hasHost("foo").hasPort(-1).hasPath("/path/bar");
	}

	@Test
	void uriBuilderShouldPrefixRelativeUrl() {

		VaultEndpoint localhost = VaultEndpoint.create("localhost", 8200);
		PrefixAwareUriBuilderFactory handler = new PrefixAwareUriBuilderFactory(() -> localhost);

		URI uri = handler.expand("/path/{bar}", "bar");

		assertThat(uri).hasHost("localhost").hasPort(8200).hasPath("/v1/path/bar");
	}

	@Test
	void uriBuilderShouldNotPrefixAbsoluteUrl() {

		VaultEndpoint localhost = VaultEndpoint.create("localhost", 8200);
		PrefixAwareUriBuilderFactory handler = new PrefixAwareUriBuilderFactory(() -> localhost);

		URI uri = handler.expand("https://foo/path/{bar}", "bar");

		assertThat(uri).hasScheme("https").hasHost("foo").hasPort(-1).hasPath("/path/bar");
	}

	@Test
	void shouldApplyNamespace() {

		RestTemplate restTemplate = VaultClients.createRestTemplate();
		restTemplate.getInterceptors().add(VaultClients.createNamespaceInterceptor("foo/bar"));
		restTemplate.setUriTemplateHandler(new PrefixAwareUriTemplateHandler());

		MockRestServiceServer mockRest = MockRestServiceServer.createServer(restTemplate);

		mockRest.expect(requestTo("/auth/foo")).andExpect(method(HttpMethod.GET))
				.andExpect(header(VaultHttpHeaders.VAULT_NAMESPACE, "foo/bar")).andRespond(withSuccess());

		restTemplate.getForEntity("/auth/foo", String.class);
	}

	@Test
	void shouldAllowNamespaceOverride() {

		RestTemplate restTemplate = VaultClients.createRestTemplate();
		restTemplate.getInterceptors().add(VaultClients.createNamespaceInterceptor("foo/bar"));
		restTemplate.setUriTemplateHandler(new PrefixAwareUriTemplateHandler());

		MockRestServiceServer mockRest = MockRestServiceServer.createServer(restTemplate);

		mockRest.expect(requestTo("/auth/foo")).andExpect(method(HttpMethod.GET))
				.andExpect(header(VaultHttpHeaders.VAULT_NAMESPACE, "baz")).andRespond(withSuccess());

		HttpHeaders headers = new HttpHeaders();
		headers.add(VaultHttpHeaders.VAULT_NAMESPACE, "baz");

		restTemplate.exchange("/auth/foo", HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	@Test
	void shouldApplyBasepath() {

		VaultEndpoint localhost = VaultEndpoint.create("localhost", 8200);
		localhost.setPath("foo/v1");
		PrefixAwareUriTemplateHandler handler = new PrefixAwareUriTemplateHandler(() -> localhost);

		URI uri = handler.expand("/path/{bar}", "bar");

		assertThat(uri).hasHost("localhost").hasPort(8200).hasPath("/foo/v1/path/bar");
	}

}
