/*
 * Copyright 2018-2025 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.vault.client.VaultClients.PrefixAwareUriBuilderFactory;
import org.springframework.vault.util.MockVaultClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link VaultClient}.
 *
 * @author Mark Paluch
 */
class VaultClientUnitTests {

	VaultEndpoint localhost = VaultEndpoint.create("localhost", 8200);

	@Test
	void shouldExpandPath() {

		MockVaultClient client = MockVaultClient.create(it -> it.endpoint(localhost));

		client.expect(requestTo("https://localhost:8200/v1/auth/foo"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess());

		client.get().path("auth/foo").retrieve().body();
	}

	@Test
	void shouldExpandAbsolutePath() {

		MockVaultClient client = MockVaultClient.create(it -> it.endpoint(localhost));

		client.expect(requestTo("https://localhost:8200/v1/auth/foo"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess());

		client.get().path("/auth/foo").retrieve().body();
	}

	@Test
	void shouldEscapeAbsoluteUri() {

		MockVaultClient client = MockVaultClient.create(it -> it.endpoint(localhost));

		client.expect(requestTo("https://localhost:8200/v1/https:/some.other.server:8200/v1/auth/foo"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess());

		client.get().path("https://some.other.server:8200/v1/auth/foo").retrieve().body();
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

}
