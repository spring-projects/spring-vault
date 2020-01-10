/*
 * Copyright 2016-2020 the original author or authors.
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

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.vault.client.ClientHttpRequestFactoryFactory;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.web.client.RestTemplate;

/**
 * Factory for {@link RestTemplate}. The template caches the
 * {@link ClientHttpRequestFactory} once it was initialized. Changes to timeouts or the
 * SSL configuration won't be applied once a {@link ClientHttpRequestFactory} was created
 * for the first time.
 *
 * @author Mark Paluch
 */
public class TestRestTemplateFactory {

	public static final VaultEndpoint TEST_VAULT_ENDPOINT = new VaultEndpoint();

	private static final AtomicReference<ClientHttpRequestFactory> factoryCache = new AtomicReference<ClientHttpRequestFactory>();

	/**
	 * Create a new {@link RestTemplate} using the {@link SslConfiguration}. The
	 * underlying {@link ClientHttpRequestFactory} is cached. See
	 * {@link #create(ClientHttpRequestFactory)} to create {@link RestTemplate} for a
	 * given {@link ClientHttpRequestFactory}.
	 *
	 * @param sslConfiguration must not be {@literal null}.
	 * @return
	 */
	public static RestTemplate create(SslConfiguration sslConfiguration) {

		Assert.notNull(sslConfiguration, "SslConfiguration must not be null!");

		try {
			initializeClientHttpRequestFactory(sslConfiguration);
			return create(factoryCache.get());
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Create a new {@link RestTemplate} using the {@link ClientHttpRequestFactory}. The
	 * {@link RestTemplate} will throw
	 * {@link org.springframework.web.client.HttpStatusCodeException exceptions} in error
	 * cases and behave in that aspect like the regular
	 * {@link org.springframework.web.client.RestTemplate}.
	 *
	 * @param requestFactory must not be {@literal null}.
	 * @return
	 */
	private static RestTemplate create(ClientHttpRequestFactory requestFactory) {

		Assert.notNull(requestFactory, "ClientHttpRequestFactory must not be null!");

		return VaultClients.createRestTemplate(TEST_VAULT_ENDPOINT, requestFactory);
	}

	private static void initializeClientHttpRequestFactory(
			SslConfiguration sslConfiguration) throws Exception {

		if (factoryCache.get() != null) {
			return;
		}

		final ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory
				.create(new ClientOptions(), sslConfiguration);

		if (factoryCache.compareAndSet(null, clientHttpRequestFactory)) {

			if (clientHttpRequestFactory instanceof InitializingBean) {
				((InitializingBean) clientHttpRequestFactory).afterPropertiesSet();
			}

			if (clientHttpRequestFactory instanceof DisposableBean) {

				Runtime.getRuntime().addShutdownHook(
						new Thread("ClientHttpRequestFactory Shutdown Hook") {

							@Override
							public void run() {
								try {
									((DisposableBean) clientHttpRequestFactory).destroy();
								}
								catch (Exception e) {
									e.printStackTrace();
								}
							}
						});
			}
		}
	}
}
