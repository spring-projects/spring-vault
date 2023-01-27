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
import java.net.ProxySelector;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.core5.http.nio.ssl.BasicClientTlsStrategy;
import org.apache.hc.core5.util.Timeout;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import reactor.netty.http.Http11SslContextSpec;
import reactor.netty.http.client.HttpClient;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.HttpComponentsClientHttpConnector;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
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

	private static final boolean REACTOR_NETTY_PRESENT = ClassUtils.isPresent("reactor.netty.http.client.HttpClient",
			ClientHttpConnectorFactory.class.getClassLoader());

	private static final boolean HTTP_COMPONENTS_PRESENT = ClassUtils.isPresent("org.apache.hc.client5.http.impl.async",
			ClientHttpConnectorFactory.class.getClassLoader());

	private static final boolean JETTY_PRESENT = ClassUtils.isPresent("org.eclipse.jetty.client.HttpClient",
			ClientHttpConnectorFactory.class.getClassLoader());

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

			if (HTTP_COMPONENTS_PRESENT) {
				return HttpComponents.usingHttpComponents(options, sslConfiguration);

			}

			if (JETTY_PRESENT) {
				return JettyClient.usingJetty(options, sslConfiguration);
			}

			return JdkHttpClient.usingJdkHttpClient(options, sslConfiguration);
		}
		catch (GeneralSecurityException | IOException e) {
			throw new IllegalStateException(e);
		}
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

	/**
	 * {@link ClientHttpConnector} for Reactor Netty.
	 *
	 * @author Mark Paluch
	 */
	static class ReactorNetty {

		static ClientHttpConnector usingReactorNetty(ClientOptions options, SslConfiguration sslConfiguration) {
			HttpClient client = HttpClient.create();

			if (hasSslConfiguration(sslConfiguration)) {

				Http11SslContextSpec sslContextSpec = Http11SslContextSpec.forClient()
						.configure(it -> configureSsl(sslConfiguration, it)).get();

				client = client.secure(builder -> builder.sslContext(sslContextSpec));
			}

			client = client.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
					Math.toIntExact(options.getConnectionTimeout().toMillis())).proxyWithSystemProperties();

			return new ReactorClientHttpConnector(client);
		}

	}

	/**
	 * {@link ClientHttpRequestFactory} for Apache Http Components.
	 *
	 * @author Mark Paluch
	 */
	static class HttpComponents {

		static ClientHttpConnector usingHttpComponents(ClientOptions options, SslConfiguration sslConfiguration)
				throws GeneralSecurityException, IOException {

			HttpAsyncClientBuilder httpClientBuilder = HttpAsyncClientBuilder.create();

			httpClientBuilder.setRoutePlanner(
					new SystemDefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE, ProxySelector.getDefault()));

			if (hasSslConfiguration(sslConfiguration)) {

				SSLContext sslContext = getSSLContext(sslConfiguration, getTrustManagers(sslConfiguration));

				String[] enabledProtocols = !sslConfiguration.getEnabledProtocols().isEmpty()
						? sslConfiguration.getEnabledProtocols().toArray(new String[0]) : null;

				String[] enabledCipherSuites = !sslConfiguration.getEnabledCipherSuites().isEmpty()
						? sslConfiguration.getEnabledCipherSuites().toArray(new String[0]) : null;

				BasicClientTlsStrategy tlsStrategy = new BasicClientTlsStrategy(sslContext, (endpoint, sslEngine) -> {

					if (enabledProtocols != null) {
						sslEngine.setEnabledProtocols(enabledProtocols);
					}

					if (enabledCipherSuites != null) {
						sslEngine.setEnabledCipherSuites(enabledCipherSuites);
					}
				}, null);

				PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder //
						.create().setTlsStrategy(tlsStrategy) //
						.build(); //
				httpClientBuilder.setConnectionManager(connectionManager);
			}

			RequestConfig requestConfig = RequestConfig.custom()
					.setConnectTimeout(Timeout.ofMilliseconds(options.getConnectionTimeout().toMillis()))
					.setResponseTimeout(Timeout.ofMilliseconds(options.getReadTimeout().toMillis()))
					.setAuthenticationEnabled(true) //
					.setRedirectsEnabled(true).build();

			httpClientBuilder.setDefaultRequestConfig(requestConfig);

			return new HttpComponentsClientHttpConnector(httpClientBuilder.build());
		}

	}

	static class JettyClient {

		static ClientHttpConnector usingJetty(ClientOptions options, SslConfiguration sslConfiguration) {

			try {
				return new JettyClientHttpConnector(configureClient(getHttpClient(sslConfiguration), options));
			}
			catch (GeneralSecurityException | IOException e) {
				throw new IllegalStateException(e);
			}
		}

		private static org.eclipse.jetty.client.HttpClient configureClient(
				org.eclipse.jetty.client.HttpClient httpClient, ClientOptions options) {

			httpClient.setConnectTimeout(options.getConnectionTimeout().toMillis());
			httpClient.setAddressResolutionTimeout(options.getConnectionTimeout().toMillis());

			return httpClient;
		}

		private static org.eclipse.jetty.client.HttpClient getHttpClient(SslConfiguration sslConfiguration)
				throws IOException, GeneralSecurityException {

			if (hasSslConfiguration(sslConfiguration)) {

				SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();

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

				ClientConnector connector = new ClientConnector();
				connector.setSslContextFactory(sslContextFactory);

				return new org.eclipse.jetty.client.HttpClient(new HttpClientTransportOverHTTP(connector));
			}

			return new org.eclipse.jetty.client.HttpClient();
		}

	}

	/**
	 * {@link ClientHttpRequestFactory} using the JDK's HttpClient.
	 *
	 * @author Mark Paluch
	 */
	static class JdkHttpClient {

		static ClientHttpConnector usingJdkHttpClient(ClientOptions options, SslConfiguration sslConfiguration)
				throws GeneralSecurityException, IOException {

			java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder();

			if (hasSslConfiguration(sslConfiguration)) {

				SSLContext sslContext = getSSLContext(sslConfiguration, getTrustManagers(sslConfiguration));

				String[] enabledProtocols = !sslConfiguration.getEnabledProtocols().isEmpty()
						? sslConfiguration.getEnabledProtocols().toArray(new String[0]) : null;

				String[] enabledCipherSuites = !sslConfiguration.getEnabledCipherSuites().isEmpty()
						? sslConfiguration.getEnabledCipherSuites().toArray(new String[0]) : null;

				BasicClientTlsStrategy tlsStrategy = new BasicClientTlsStrategy(sslContext, (endpoint, sslEngine) -> {

					if (enabledProtocols != null) {
						sslEngine.setEnabledProtocols(enabledProtocols);
					}

					if (enabledCipherSuites != null) {
						sslEngine.setEnabledCipherSuites(enabledCipherSuites);
					}
				}, null);

				SSLParameters parameters = new SSLParameters();
				parameters.setProtocols(enabledProtocols);
				parameters.setCipherSuites(enabledCipherSuites);

				builder.sslContext(sslContext).sslParameters(parameters);
			}

			builder.proxy(ProxySelector.getDefault()).followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
					.connectTimeout(options.getConnectionTimeout());

			return new JdkClientHttpConnector(builder.build());
		}

	}

}
