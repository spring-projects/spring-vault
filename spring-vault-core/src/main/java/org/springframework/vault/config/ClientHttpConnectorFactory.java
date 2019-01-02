/*
 * Copyright 2017-2019 the original author or authors.
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
package org.springframework.vault.config;

import java.io.IOException;
import java.security.GeneralSecurityException;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import reactor.netty.http.client.HttpClient;

import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;

import static org.springframework.vault.config.ClientHttpRequestFactoryFactory.createKeyManagerFactory;
import static org.springframework.vault.config.ClientHttpRequestFactoryFactory.createTrustManagerFactory;
import static org.springframework.vault.config.ClientHttpRequestFactoryFactory.hasSslConfiguration;

/**
 * Factory for {@link ClientHttpConnector} that supports
 * {@link ReactorClientHttpConnector}.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class ClientHttpConnectorFactory {

	/**
	 * Create a {@link ClientHttpConnector} for the given {@link ClientOptions} and
	 * {@link SslConfiguration}.
	 *
	 * @param options must not be {@literal null}
	 * @param sslConfiguration must not be {@literal null}
	 * @return a new {@link ClientHttpConnector}.
	 */
	public static ClientHttpConnector create(ClientOptions options,
			SslConfiguration sslConfiguration) {

		HttpClient client = HttpClient.create();

		if (hasSslConfiguration(sslConfiguration)) {

			SslContextBuilder sslContextBuilder = SslContextBuilder.forClient();
			configureSsl(sslConfiguration, sslContextBuilder);

			client = client.secure(builder -> {
				builder.sslContext(sslContextBuilder);
			});
		}

		client = client.tcpConfiguration(it -> it.option(
				ChannelOption.CONNECT_TIMEOUT_MILLIS,
				Math.toIntExact(options.getConnectionTimeout().toMillis())));

		return new ReactorClientHttpConnector(client);
	}

	private static void configureSsl(SslConfiguration sslConfiguration,
			SslContextBuilder sslContextBuilder) {

		try {

			if (sslConfiguration.getTrustStoreConfiguration().isPresent()) {
				sslContextBuilder.trustManager(createTrustManagerFactory(sslConfiguration
						.getTrustStoreConfiguration()));
			}

			if (sslConfiguration.getKeyStoreConfiguration().isPresent()) {
				sslContextBuilder.keyManager(createKeyManagerFactory(sslConfiguration
						.getKeyStoreConfiguration()));
			}
		}
		catch (GeneralSecurityException | IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
