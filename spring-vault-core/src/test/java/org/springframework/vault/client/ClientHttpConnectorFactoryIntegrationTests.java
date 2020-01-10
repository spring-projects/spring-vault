/*
 * Copyright 2019-2020 the original author or authors.
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

import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.util.Settings;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.vault.client.ClientHttpConnectorFactory.JettyClient;
import static org.springframework.vault.client.ClientHttpConnectorFactory.ReactorNetty;

/**
 * Integration tests for {@link ClientHttpConnectorFactory}.
 *
 * @author Mark Paluch
 */
class ClientHttpConnectorFactoryIntegrationTests {

	final String url = new VaultEndpoint().createUriString("sys/health");

	@Test
	void reactorNettyClientShouldWork() {

		ClientHttpConnector factory = ReactorNetty.usingReactorNetty(new ClientOptions(),
				Settings.createSslConfiguration());

		WebClient webClient = WebClient.builder().clientConnector(factory).build();

		String response = request(webClient);

		assertThat(response).isNotNull().contains("initialized");
	}

	@Test
	void jettyClientShouldWork() {

		ClientHttpConnector factory = JettyClient.usingJetty(new ClientOptions(),
				Settings.createSslConfiguration());

		WebClient webClient = WebClient.builder().clientConnector(factory).build();

		String response = request(webClient);

		assertThat(response).isNotNull().contains("initialized");
	}

	private String request(WebClient webClient) {

		// Uninitialized and sealed can cause status 500
		try {
			return webClient.get().uri(url).retrieve().bodyToMono(String.class).block();
		}
		catch (WebClientResponseException e) {
			return e.getResponseBodyAsString();
		}
	}
}
