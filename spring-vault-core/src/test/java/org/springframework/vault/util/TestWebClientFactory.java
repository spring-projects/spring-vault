/*
 * Copyright 2017-2025 the original author or authors.
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
package org.springframework.vault.util;

import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.util.Assert;
import org.springframework.vault.client.ClientHttpConnectorFactory;
import org.springframework.vault.client.ReactiveVaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Mark Paluch
 */
public class TestWebClientFactory {

	private static final VaultEndpoint TEST_VAULT_ENDPOINT = TestRestTemplateFactory.TEST_VAULT_ENDPOINT;

	/**
	 * Create a new {@link WebClient} using the {@link SslConfiguration}. See
	 * {@link ReactiveVaultClients#createWebClient(VaultEndpoint, ClientHttpConnector)} to
	 * create {@link WebClient} for a given {@link ClientHttpConnector}.
	 * @param sslConfiguration must not be {@literal null}.
	 * @return
	 */
	public static WebClient create(SslConfiguration sslConfiguration) {

		Assert.notNull(sslConfiguration, "SslConfiguration must not be null!");

		try {
			ClientHttpConnector connector = ClientHttpConnectorFactory.create(new ClientOptions(), sslConfiguration);
			return ReactiveVaultClients.createWebClient(TEST_VAULT_ENDPOINT, connector);
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
