/*
 * Copyright 2016-2019 the original author or authors.
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
package org.springframework.vault.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.vault.client.ClientHttpRequestFactoryFactory;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Settings;

/**
 * Integration tests for {@link VaultTemplate} through Vault Agent.
 *
 * @author Mark Paluch
 */
class VaultTemplateAgentIntegrationTests extends IntegrationTestSupport {

	ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryFactory
			.create(new ClientOptions(), Settings.createSslConfiguration());

	VaultEndpoint endpoint = VaultEndpoint.create("localhost", 8202);

	@BeforeEach
	void setUp() {

		try (Socket socket = new Socket()) {

			socket.connect(new InetSocketAddress(endpoint.getHost(), endpoint.getPort()),
					(int) new ClientOptions().getConnectionTimeout().toMillis());
		}
		catch (IOException e) {
			throw new TestAbortedException(
					"Vault Agent not available: " + e.getMessage());
		}
	}

	@Test
	void shouldUseAgentAuthentication() {

		VaultTemplate vaultTemplate = new VaultTemplate(endpoint, requestFactory);

		vaultTemplate.write("secret/foo", Collections.singletonMap("key", "value"));
	}

	@Test
	void shouldUseAgentAuthenticationWithBuilder() {

		RestTemplateBuilder builder = RestTemplateBuilder.builder().endpoint(endpoint)
				.requestFactory(requestFactory);

		VaultTemplate vaultTemplate = new VaultTemplate(builder);

		vaultTemplate.write("secret/foo", Collections.singletonMap("key", "value"));
	}
}
