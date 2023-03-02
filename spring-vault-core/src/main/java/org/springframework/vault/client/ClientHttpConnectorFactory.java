/*
 * Copyright 2017-2022 the original author or authors.
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

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import reactor.netty.http.Http11SslContextSpec;
import reactor.netty.http.client.HttpClient;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;

import static org.springframework.vault.client.ClientHttpRequestFactoryFactory.*;

/**
 * Factory for {@link ClientHttpConnector} that supports
 * {@link ReactorClientHttpConnector} and {@link JettyClientHttpConnector}.
 *
 * This factory configures a {@link ClientHttpConnector} depending on the available
 * dependencies.
 *
 * @author Mark Paluch
 * @author Ryan Gow
 * @since 2.2
 */
public class ClientHttpConnectorFactory {

	private static final boolean REACTOR_NETTY_PRESENT = isPresent("reactor.netty.http.client.HttpClient");

	private static final boolean JETTY_PRESENT = isPresent("org.eclipse.jetty.client.HttpClient");

	/**
	 * Checks for presence of all {@code classNames} using this class' classloader.
	 * @param classNames
	 * @return {@literal true} if all classes are present; {@literal false} if at least
	 * one class cannot be found.
	 */
	private static boolean isPresent(String... classNames) {

		for (String className : classNames) {
			if (!ClassUtils.isPresent(className, ClientHttpConnectorFactory.class.getClassLoader())) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Create a {@link ClientHttpConnector} for the given {@link ClientOptions} and
	 * {@link SslConfiguration}.
	 * @param options must not be {@literal null}
	 * @param sslConfiguration must not be {@literal null}
	 * @return a new {@link ClientHttpConnector}.
	 */
	public static ClientHttpConnector create(ClientOptions options, SslConfiguration sslConfiguration) {

		Assert.notNull(options, "ClientOptions must not be null");
		Assert.notNull(sslConfiguration, "SslConfiguration must not be null");

		try {
			if (REACTOR_NETTY_PRESENT) {
				return ReactorNetty.usingReactorNetty(options, sslConfiguration);
			}

			if (JETTY_PRESENT) {
				return JettyClient.usingJetty(options, sslConfiguration);
			}
		}
		catch (GeneralSecurityException | IOException e) {
			throw new IllegalStateException(e);
		}

		throw new IllegalStateException("No supported Reactive Http Client library available (Reactor Netty, Jetty)");
	}

	/**
	 * {@link ClientHttpConnector} for Reactor Netty.
	 *
	 * @author Mark Paluch
	 */
	public static class ReactorNetty {

		/**
		 * Create a {@link ClientHttpConnector} using Reactor Netty.
		 * @param options must not be {@literal null}
		 * @param sslConfiguration must not be {@literal null}
		 * @return a new and configured {@link ReactorClientHttpConnector} instance.
		 */
		public static ReactorClientHttpConnector usingReactorNetty(ClientOptions options,
				SslConfiguration sslConfiguration) {
			return new ReactorClientHttpConnector(createClient(options, sslConfiguration));
		}

		public static HttpClient createClient(ClientOptions options, SslConfiguration sslConfiguration) {

			HttpClient client = HttpClient.create();

			if (hasSslConfiguration(sslConfiguration)) {

				Http11SslContextSpec sslContextSpec = Http11SslContextSpec.forClient()
						.configure(it -> configureSsl(sslConfiguration, it)).get();

				client = client.secure(builder -> builder.sslContext(sslContextSpec));
			}

			client = client.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
					Math.toIntExact(options.getConnectionTimeout().toMillis())).proxyWithSystemProperties();

			return client;
		}

		private static void configureSsl(SslConfiguration sslConfiguration, SslContextBuilder sslContextBuilder) {

			try {

				if (sslConfiguration.getTrustStoreConfiguration().isPresent()) {
					sslContextBuilder
							.trustManager(createTrustManagerFactory(sslConfiguration.getTrustStoreConfiguration()));
				}

				if (sslConfiguration.getKeyStoreConfiguration().isPresent()) {
					sslContextBuilder.keyManager(createKeyManagerFactory(sslConfiguration.getKeyStoreConfiguration(),
							sslConfiguration.getKeyConfiguration()));
				}

				if (!sslConfiguration.getEnabledProtocols().isEmpty()) {
					sslContextBuilder.protocols(sslConfiguration.getEnabledProtocols());
				}

				if (!sslConfiguration.getEnabledCipherSuites().isEmpty()) {
					sslContextBuilder.ciphers(sslConfiguration.getEnabledCipherSuites());
				}
			}
			catch (GeneralSecurityException | IOException e) {
				throw new IllegalStateException(e);
			}
		}

	}

	/**
	 * Utility methods to create {@link ClientHttpRequestFactory} using the Jetty Client.
	 *
	 * @author Mark Paluch
	 */
	static class JettyClient {

		/**
		 * Create a {@link ClientHttpConnector} using Jetty.
		 * @param options must not be {@literal null}
		 * @param sslConfiguration must not be {@literal null}
		 * @return a new and configured {@link JettyClientHttpConnector} instance.
		 * @throws GeneralSecurityException
		 * @throws IOException
		 */
		public static JettyClientHttpConnector usingJetty(ClientOptions options, SslConfiguration sslConfiguration)
				throws GeneralSecurityException, IOException {
			return new JettyClientHttpConnector(configureClient(getHttpClient(sslConfiguration), options));
		}

		public static org.eclipse.jetty.client.HttpClient configureClient(
				org.eclipse.jetty.client.HttpClient httpClient, ClientOptions options) {

			httpClient.setConnectTimeout(options.getConnectionTimeout().toMillis());
			httpClient.setAddressResolutionTimeout(options.getConnectionTimeout().toMillis());

			return httpClient;
		}

		public static org.eclipse.jetty.client.HttpClient getHttpClient(SslConfiguration sslConfiguration)
				throws IOException, GeneralSecurityException {

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

				SslConfiguration.KeyConfiguration keyConfiguration = sslConfiguration.getKeyConfiguration();

				if (keyConfiguration.getKeyAlias() != null) {
					sslContextFactory.setCertAlias(keyConfiguration.getKeyAlias());
				}

				if (keyConfiguration.getKeyPassword() != null) {
					sslContextFactory.setKeyManagerPassword(new String(keyConfiguration.getKeyPassword()));
				}

				if (!sslConfiguration.getEnabledProtocols().isEmpty()) {
					sslContextFactory
							.setIncludeProtocols(sslConfiguration.getEnabledProtocols().toArray(new String[0]));
				}

				if (!sslConfiguration.getEnabledCipherSuites().isEmpty()) {
					sslContextFactory
							.setIncludeCipherSuites(sslConfiguration.getEnabledCipherSuites().toArray(new String[0]));
				}

				return new org.eclipse.jetty.client.HttpClient(sslContextFactory);
			}

			return new org.eclipse.jetty.client.HttpClient();
		}

	}

}
