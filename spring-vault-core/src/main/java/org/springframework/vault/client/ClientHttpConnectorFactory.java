/*
 * Copyright 2017-2020 the original author or authors.
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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import reactor.netty.http.client.HttpClient;

import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;

import static org.springframework.vault.client.ClientHttpRequestFactoryFactory.createKeyManagerFactory;
import static org.springframework.vault.client.ClientHttpRequestFactoryFactory.createTrustManagerFactory;
import static org.springframework.vault.client.ClientHttpRequestFactoryFactory.hasSslConfiguration;

/**
 * Factory for {@link ClientHttpConnector} that supports
 * {@link ReactorClientHttpConnector} and {@link JettyClientHttpConnector}.
 *
 * This factory configures a {@link ClientHttpConnector} depending on the available
 * dependencies.
 *
 * @author Mark Paluch
 * @since 2.2
 */
public class ClientHttpConnectorFactory {

	private static final boolean REACTOR_NETTY_PRESENT = isPresent(
			"reactor.netty.http.client.HttpClient");

	private static final boolean JETTY_PRESENT = isPresent(
			"org.eclipse.jetty.client.HttpClient");

	/**
	 * Checks for presence of all {@code classNames} using this class' classloader.
	 *
	 * @param classNames
	 * @return {@literal true} if all classes are present; {@literal false} if at least
	 * one class cannot be found.
	 */
	private static boolean isPresent(String... classNames) {

		for (String className : classNames) {
			if (!ClassUtils.isPresent(className,
					ClientHttpConnectorFactory.class.getClassLoader())) {
				return false;
			}
		}

		return true;
	}

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

		Assert.notNull(options, "ClientOptions must not be null");
		Assert.notNull(sslConfiguration, "SslConfiguration must not be null");

		if (REACTOR_NETTY_PRESENT) {
			return ReactorNetty.usingReactorNetty(options, sslConfiguration);
		}

		if (JETTY_PRESENT) {
			return JettyClient.usingJetty(options, sslConfiguration);
		}

		throw new IllegalStateException(
				"No supported Reactive Http Client library available (Reactor Netty, Jetty)");
	}

	private static void configureSsl(SslConfiguration sslConfiguration,
			SslContextBuilder sslContextBuilder) {

		try {

			if (sslConfiguration.getTrustStoreConfiguration().isPresent()) {
				sslContextBuilder.trustManager(createTrustManagerFactory(
						sslConfiguration.getTrustStoreConfiguration()));
			}

			if (sslConfiguration.getKeyStoreConfiguration().isPresent()) {
				sslContextBuilder.keyManager(createKeyManagerFactory(
						sslConfiguration.getKeyStoreConfiguration(),
						sslConfiguration.getKeyConfiguration()));
			}
		}
		catch (GeneralSecurityException | IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * {@link ClientHttpConnector} for Reactor Netty.
	 *
	 * @author Mark Paluch
	 */
	static class ReactorNetty {

		static ClientHttpConnector usingReactorNetty(ClientOptions options,
				SslConfiguration sslConfiguration) {
			HttpClient client = HttpClient.create();

			if (hasSslConfiguration(sslConfiguration)) {

				SslContextBuilder sslContextBuilder = SslContextBuilder.forClient();
				configureSsl(sslConfiguration, sslContextBuilder);

				client = client.secure(builder -> {
					builder.sslContext(sslContextBuilder);
				});
			}

			client = client.tcpConfiguration(
					it -> it.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
							Math.toIntExact(options.getConnectionTimeout().toMillis())));

			return new ReactorClientHttpConnector(client);
		}
	}

	static class JettyClient {
		static ClientHttpConnector usingJetty(ClientOptions options,
				SslConfiguration sslConfiguration) {

			try {
				return new JettyClientHttpConnector(
						configureClient(getHttpClient(sslConfiguration), options));
			}
			catch (GeneralSecurityException | IOException e) {
				throw new IllegalStateException(e);
			}
		}

		private static org.eclipse.jetty.client.HttpClient configureClient(
				org.eclipse.jetty.client.HttpClient httpClient, ClientOptions options) {

			httpClient.setConnectTimeout(options.getConnectionTimeout().toMillis());
			httpClient.setAddressResolutionTimeout(
					options.getConnectionTimeout().toMillis());

			return httpClient;
		}

		private static org.eclipse.jetty.client.HttpClient getHttpClient(
				SslConfiguration sslConfiguration) throws KeyStoreException, IOException,
				NoSuchAlgorithmException, CertificateException {

			if (hasSslConfiguration(sslConfiguration)) {

				SslContextFactory sslContextFactory = new SslContextFactory();

				if (sslConfiguration.getKeyStoreConfiguration().isPresent()) {
					KeyStore keyStore = ClientHttpRequestFactoryFactory
							.getKeyStore(sslConfiguration.getKeyStoreConfiguration());
					sslContextFactory.setKeyStore(keyStore);
				}

				if (sslConfiguration.getTrustStoreConfiguration().isPresent()) {
					KeyStore keyStore = ClientHttpRequestFactoryFactory
							.getKeyStore(sslConfiguration.getTrustStoreConfiguration());
					sslContextFactory.setTrustStore(keyStore);
				}

				SslConfiguration.KeyConfiguration keyConfiguration = sslConfiguration
						.getKeyConfiguration();

				if (keyConfiguration.getKeyAlias() != null) {
					sslContextFactory.setCertAlias(keyConfiguration.getKeyAlias());
				}

				if (keyConfiguration.getKeyPassword() != null) {
					sslContextFactory.setKeyManagerPassword(
							new String(keyConfiguration.getKeyPassword()));
				}

				return new org.eclipse.jetty.client.HttpClient(sslContextFactory);
			}

			return new org.eclipse.jetty.client.HttpClient();
		}
	}
}
