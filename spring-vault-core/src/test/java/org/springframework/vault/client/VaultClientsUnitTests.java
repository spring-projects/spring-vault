/*
 * Copyright 2018-present the original author or authors.
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
import org.springframework.vault.client.VaultClients.VaultEndpointUriBuilderFactory;
import org.springframework.vault.util.MockVaultClient;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link org.springframework.vault.client.VaultClients}.
 *
 * @author Mark Paluch
 */
class VaultClientsUnitTests {

	VaultEndpoint localhost = VaultEndpoint.create("localhost", 8200);

	@Test
	void endpointUriHandlerShouldPrefixRelativeUrl() {
		VaultEndpointUriBuilderFactory handler = new VaultEndpointUriBuilderFactory(localhost);
		URI uri = handler.expand("/path/{bar}?list=true", "bar");

		assertThat(uri).hasHost("localhost").hasPort(8200).hasPath("/v1/path/bar").hasQuery("list=true");
	}

	@Test
	void uriHandlerShouldPrefixRelativeUrl() {
		PrefixAwareUriBuilderFactory handler = new PrefixAwareUriBuilderFactory(() -> localhost);
		URI uri = handler.expand("/path/{bar}?list=true", "bar");

		assertThat(uri).hasHost("localhost").hasPort(8200).hasPath("/v1/path/bar").hasQuery("list=true");
	}

	@Test
	void uriHandlerShouldNotPrefixAbsoluteUrl() {
		VaultEndpoint localhost = VaultEndpoint.create("localhost", 8200);
		PrefixAwareUriBuilderFactory handler = new PrefixAwareUriBuilderFactory(() -> localhost);
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
	void shouldApplyNamespaceWithRestTemplate() {
		RestTemplate restTemplate = VaultClients.createRestTemplate();
		restTemplate.getInterceptors().add(VaultClients.createNamespaceInterceptor("foo/bar"));
		restTemplate.setUriTemplateHandler(new PrefixAwareUriBuilderFactory());

		MockRestServiceServer mockRest = MockRestServiceServer.createServer(restTemplate);

		mockRest.expect(requestTo("/auth/foo"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(VaultHttpHeaders.VAULT_NAMESPACE, "foo/bar"))
				.andRespond(withSuccess());

		restTemplate.getForEntity("auth/foo", String.class);
	}

	@Test
	void shouldApplyNamespace() {
		MockVaultClient client = MockVaultClient.create(it -> it.defaultNamespace("foo/bar")
				.configureRestClient(rcb -> rcb.uriBuilderFactory(new PrefixAwareUriBuilderFactory())));

		client.expect(requestTo("/auth/foo"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(VaultHttpHeaders.VAULT_NAMESPACE, "foo/bar"))
				.andRespond(withSuccess());

		client.get().path("auth/foo").retrieve().body();
	}

	@Test
	void uriHandlerShouldRejectTraversalInPath() {

		PrefixAwareUriBuilderFactory handler = new PrefixAwareUriBuilderFactory(() -> localhost);

		assertThatIllegalArgumentException().isThrownBy(() -> handler.uriString("secret/../sys/mounts"));
	}

	@Test
	void uriHandlerShouldRejectEncodedTraversalInPath() {

		PrefixAwareUriBuilderFactory handler = new PrefixAwareUriBuilderFactory(() -> localhost);

		assertThatIllegalArgumentException().isThrownBy(() -> handler.uriString("secret/data/..%2fmetadata%2ffoo"));
	}

	@Test
	void uriHandlerShouldAcceptLegitimateUriTemplates() {

		PrefixAwareUriBuilderFactory handler = new PrefixAwareUriBuilderFactory(() -> localhost);

		assertThat(handler.expand("secret/data/foo?list=true")).hasPath("/v1/secret/data/foo").hasQuery("list=true");
		assertThat(handler.expand("auth/{provider}/login", "approle")).hasPath("/v1/auth/approle/login");
	}

	@Test
	void endpointUriHandlerShouldRejectTraversalInPath() {

		VaultEndpointUriBuilderFactory handler = new VaultEndpointUriBuilderFactory(localhost);

		assertThatIllegalArgumentException().isThrownBy(() -> handler.uriString("secret/../sys/mounts"));
		assertThatIllegalArgumentException().isThrownBy(() -> handler.uriString("secret/data/..%2fmetadata%2ffoo"));
	}

	@Test
	void endpointUriHandlerShouldAcceptLegitimateUriTemplates() {

		VaultEndpointUriBuilderFactory handler = new VaultEndpointUriBuilderFactory(localhost);

		assertThat(handler.expand("secret/data/foo?list=true")).hasPath("/v1/secret/data/foo").hasQuery("list=true");
		assertThat(handler.expand("auth/{provider}/login", "approle")).hasPath("/v1/auth/approle/login");
	}

	@Test
	void uriComponentsShouldRejectTraversalInPath() {

		assertThatIllegalArgumentException()
			.isThrownBy(() -> VaultClients.getUriComponents(localhost, "secret/../sys/mounts"));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> VaultClients.getUriComponents(localhost, "secret/data/..%2fmetadata%2ffoo"));
	}

	@Test
	void uriComponentsShouldAcceptLegitimatePath() {

		assertThat(VaultClients.getUriComponents(localhost, "secret/data/foo").build().toUri())
			.hasPath("/v1/secret/data/foo");
	}

	@Test
	void shouldAllowNamespaceOverride() {
		RestTemplate restTemplate = VaultClients.createRestTemplate();
		restTemplate.getInterceptors().add(VaultClients.createNamespaceInterceptor("foo/bar"));
		restTemplate.setUriTemplateHandler(new PrefixAwareUriBuilderFactory());

		MockRestServiceServer mockRest = MockRestServiceServer.createServer(restTemplate);

		mockRest.expect(requestTo("/auth/foo"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(VaultHttpHeaders.VAULT_NAMESPACE, "baz"))
				.andRespond(withSuccess());

		HttpHeaders headers = new HttpHeaders();
		headers.add(VaultHttpHeaders.VAULT_NAMESPACE, "baz");

		restTemplate.exchange("auth/foo", HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

}
