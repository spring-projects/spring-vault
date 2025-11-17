/*
 * Copyright 2025 the original author or authors.
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
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.Assert;
import org.springframework.vault.client.ClientHttpRequestFactoryFactory;
import org.springframework.vault.client.SimpleVaultEndpointProvider;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.web.client.RestClient;

/**
 * Utility to create {@link VaultClient} instances for testing.
 *
 * @author Mark Paluch
 */
public interface TestVaultClient extends VaultClient {

	AtomicReference<ClientHttpRequestFactory> factoryCache = new AtomicReference<>();

	/**
	 * Return the underlying {@link RestClient}.
	 * @return the underlying {@link RestClient}.
	 */
	RestClient getRestClient();

	/**
	 * Create a {@link MockVaultClient} bound to {@link MockRestServiceServer}.
	 * @return the configured {@link MockVaultClient}.
	 */
	static MockVaultClient mock() {
		return MockVaultClient.create();
	}

	/**
	 * Create a new {@link VaultClient} using {@link Settings#createSslConfiguration()}.
	 * The underlying {@link ClientHttpRequestFactory} is cached.
	 * @return
	 */
	static TestVaultClient create() {
		return create(Settings::createSslConfiguration);
	}

	/**
	 * Create a new {@link VaultClient} using the {@link SslConfiguration}. The underlying
	 * {@link ClientHttpRequestFactory} is cached.
	 * <p>
	 * The initial request to obtain {@link ClientHttpRequestFactory} considers the SSL
	 * configuration. Subsequent calls do not obtain a new {@link SslConfiguration}.
	 * @param sslConfiguration must not be {@literal null}.
	 * @return
	 */
	static TestVaultClient create(Supplier<SslConfiguration> sslConfiguration) {

		Assert.notNull(sslConfiguration, "SslConfiguration must not be null!");

		return create(getClientHttpRequestFactory(sslConfiguration));
	}

	/**
	 * Create a new {@link VaultClient} applying the {@code builderConsumer} to
	 * {@code VaultClient.Builder}. The underlying {@link ClientHttpRequestFactory} is
	 * cached.
	 * @param builderConsumer must not be {@literal null}.
	 * @return
	 */
	static TestVaultClient create(Consumer<VaultClient.Builder> builderConsumer) {
		return create(getClientHttpRequestFactory(Settings::createSslConfiguration), builderConsumer);
	}

	/**
	 * Create a new {@link VaultClient} using the {@link ClientHttpRequestFactory}.
	 * @param clientHttpRequestFactory must not be {@literal null}.
	 * @return
	 */
	static TestVaultClient create(ClientHttpRequestFactory clientHttpRequestFactory) {

		Assert.notNull(clientHttpRequestFactory, "SslConfiguration must not be null!");

		return create(clientHttpRequestFactory, builder -> {
		});
	}

	/**
	 * Create a new {@link VaultClient} using the {@link ClientHttpRequestFactory} and
	 * applying the {@code builderConsumer} to {@code VaultClient.Builder}.
	 * @param clientHttpRequestFactory must not be {@literal null}.
	 * @param builderConsumer must not be {@literal null}.
	 * @return the configured {@link TestVaultClient}.
	 */
	static TestVaultClient create(ClientHttpRequestFactory clientHttpRequestFactory,
			Consumer<VaultClient.Builder> builderConsumer) {

		Assert.notNull(clientHttpRequestFactory, "SslConfiguration must not be null!");

		Builder builder = VaultClient.builder()
			.requestFactory(clientHttpRequestFactory)
			.endpoint(Settings.TEST_VAULT_ENDPOINT);
		builderConsumer.accept(builder);

		RestClient restClient = VaultClients.createRestClient(
				SimpleVaultEndpointProvider.of(Settings.TEST_VAULT_ENDPOINT), clientHttpRequestFactory, rbc -> {
				});

		return new DefaultTestVaultClient(builder.build(), restClient);
	}

	/**
	 * Obtain {@link ClientHttpRequestFactory} using the {@link SslConfiguration}. The
	 * underlying {@link ClientHttpRequestFactory} is cached.
	 * <p>
	 * The initial request to obtain {@link ClientHttpRequestFactory} considers the SSL
	 * configuration. Subsequent calls do not obtain a new {@link SslConfiguration}.
	 * @param sslConfiguration must not be {@literal null}.
	 * @return
	 */
	static ClientHttpRequestFactory getClientHttpRequestFactory(Supplier<SslConfiguration> sslConfiguration) {

		Assert.notNull(sslConfiguration, "SslConfiguration must not be null!");

		try {
			initializeClientHttpRequestFactory(sslConfiguration);
			return factoryCache.get();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static void initializeClientHttpRequestFactory(Supplier<SslConfiguration> sslConfiguration)
			throws Exception {

		if (factoryCache.get() != null) {
			return;
		}

		final ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory
			.create(new ClientOptions(), sslConfiguration.get());

		if (factoryCache.compareAndSet(null, clientHttpRequestFactory)) {

			if (clientHttpRequestFactory instanceof InitializingBean) {
				((InitializingBean) clientHttpRequestFactory).afterPropertiesSet();
			}

			if (clientHttpRequestFactory instanceof DisposableBean) {

				Runtime.getRuntime().addShutdownHook(new Thread("ClientHttpRequestFactory Shutdown Hook") {

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
