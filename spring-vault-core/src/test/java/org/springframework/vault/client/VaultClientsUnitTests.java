/*
 * Copyright 2018-2019 the original author or authors.
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

import org.junit.Test;

import org.springframework.vault.client.VaultClients.PrefixAwareUriBuilderFactory;
import org.springframework.vault.client.VaultClients.PrefixAwareUriTemplateHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for
 * {@link org.springframework.vault.client.VaultClients.PrefixAwareUriTemplateHandler}.
 *
 * @author Mark Paluch
 */
public class VaultClientsUnitTests {

	@Test
	public void uriHandlerShouldPrefixRelativeUrl() {

		VaultEndpoint localhost = VaultEndpoint.create("localhost", 8200);
		PrefixAwareUriTemplateHandler handler = new PrefixAwareUriTemplateHandler(
				() -> localhost);

		URI uri = handler.expand("/path/{bar}", "bar");

		assertThat(uri).hasHost("localhost").hasPort(8200).hasPath("/v1/path/bar");
	}

	@Test
	public void uriHandlerShouldNotPrefixAbsoluteUrl() {

		VaultEndpoint localhost = VaultEndpoint.create("localhost", 8200);
		PrefixAwareUriTemplateHandler handler = new PrefixAwareUriTemplateHandler(
				() -> localhost);

		URI uri = handler.expand("https://foo/path/{bar}", "bar");

		assertThat(uri).hasScheme("https").hasHost("foo").hasPort(-1)
				.hasPath("/path/bar");
	}

	@Test
	public void uriBuilderShouldPrefixRelativeUrl() {

		VaultEndpoint localhost = VaultEndpoint.create("localhost", 8200);
		PrefixAwareUriBuilderFactory handler = new PrefixAwareUriBuilderFactory(
				() -> localhost);

		URI uri = handler.expand("/path/{bar}", "bar");

		assertThat(uri).hasHost("localhost").hasPort(8200).hasPath("/v1/path/bar");
	}

	@Test
	public void uriBuilderShouldNotPrefixAbsoluteUrl() {

		VaultEndpoint localhost = VaultEndpoint.create("localhost", 8200);
		PrefixAwareUriBuilderFactory handler = new PrefixAwareUriBuilderFactory(
				() -> localhost);

		URI uri = handler.expand("https://foo/path/{bar}", "bar");

		assertThat(uri).hasScheme("https").hasHost("foo").hasPort(-1)
				.hasPath("/path/bar");
	}

	@Test
	public void shouldApplyBasepath() {

		VaultEndpoint localhost = VaultEndpoint.create("localhost", 8200);
		localhost.setPath("foo/v1");
		PrefixAwareUriTemplateHandler handler = new PrefixAwareUriTemplateHandler(
				() -> localhost);

		URI uri = handler.expand("/path/{bar}", "bar");

		assertThat(uri).hasHost("localhost").hasPort(8200).hasPath("/foo/v1/path/bar");
	}
}
