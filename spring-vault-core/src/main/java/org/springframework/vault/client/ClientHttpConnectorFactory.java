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
package org.springframework.vault.client;

import java.io.IOException;
import java.net.ProxySelector;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.core5.http.nio.ssl.BasicClientTlsStrategy;
import org.apache.hc.core5.util.Timeout;
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

/**
 * Factory for {@link ClientHttpConnector} that supports
 * {@link ReactorClientHttpConnector} and {@link JettyClientHttpConnector}. This factory
 * configures a {@link ClientHttpConnector} depending on the available dependencies.
 *
 * @author Mark Paluch
 * @author Ryan Gow
 * @since 2.2
 */
public class ClientHttpConnectorFactory {

	private static final boolean reactorNettyPresent = ClassUtils.isPresent("reactor.netty.http.client.HttpClient",
			ClientHttpConnectorFactory.class.getClassLoader());

	private static final boolean httpComponentsPresent = ClassUtils.isPresent("org.apache.hc.client5.http.impl.async",
			ClientHttpConnectorFactory.class.getClassLoader());

	private static final boolean jettyPresent = ClassUtils.isPresent("org.eclipse.jetty.client.HttpClient",
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

			if (httpComponentsPresent) {
				return HttpComponents.usingHttpComponents(options, sslConfiguration);
			}

			if (reactorNettyPresent) {
				return ReactorNetty.usingReactorNetty(options, sslConfiguration);
			}

			if (jettyPresent) {
				return JettyClient.usingJetty(options, sslConfiguration);
			}

			return JdkHttpClient.usingJdkHttpClient(options, sslConfiguration);
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
			return ClientConfiguration.ReactorNetty.createClient(options, sslConfiguration);
		}

	}

	/**
	 * Utility methods to create {@link ClientHttpRequestFactory} using Apache Http
	 * Components.
	 *
	 * @author Mark Paluch
	 */
	public static class HttpComponents {

		/**
		 * Create a {@link ClientHttpConnector} using Apache Http Components.
		 * @param options must not be {@literal null}
		 * @param sslConfiguration must not be {@literal null}
		 * @return a new and configured {@link HttpComponentsClientHttpConnector}
		 * instance.
		 * @throws GeneralSecurityException
		 * @throws IOException
		 */
		public static HttpComponentsClientHttpConnector usingHttpComponents(ClientOptions options,
				SslConfiguration sslConfiguration) throws GeneralSecurityException, IOException {

			HttpAsyncClientBuilder httpClientBuilder = createHttpAsyncClientBuilder(options, sslConfiguration);

			return new HttpComponentsClientHttpConnector(httpClientBuilder.build());
		}

		public static HttpAsyncClientBuilder createHttpAsyncClientBuilder(ClientOptions options,
				SslConfiguration sslConfiguration) throws GeneralSecurityException, IOException {

			HttpAsyncClientBuilder httpClientBuilder = HttpAsyncClientBuilder.create();

			httpClientBuilder.setRoutePlanner(
					new SystemDefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE, ProxySelector.getDefault()));

			Timeout readTimeout = Timeout.ofMilliseconds(options.getReadTimeout().toMillis());
			Timeout connectTimeout = Timeout.ofMilliseconds(options.getConnectionTimeout().toMillis());

			ConnectionConfig connectionConfig = ConnectionConfig.custom()
				.setConnectTimeout(connectTimeout) //
				.setSocketTimeout(readTimeout) //
				.build();

			RequestConfig requestConfig = RequestConfig.custom()
				.setResponseTimeout(Timeout.ofMilliseconds(options.getReadTimeout().toMillis()))
				.setAuthenticationEnabled(true) //
				.setRedirectsEnabled(true)
				.build();

			PoolingAsyncClientConnectionManagerBuilder connectionManagerBuilder = PoolingAsyncClientConnectionManagerBuilder //
				.create()
				.setDefaultConnectionConfig(connectionConfig);

			if (ClientConfiguration.hasSslConfiguration(sslConfiguration)) {

				SSLContext sslContext = ClientConfiguration.getSSLContext(sslConfiguration);

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

				connectionManagerBuilder.setTlsStrategy(tlsStrategy);
			}

			httpClientBuilder.setDefaultRequestConfig(requestConfig);
			httpClientBuilder.setConnectionManager(connectionManagerBuilder.build());

			return httpClientBuilder;
		}

	}

	/**
	 * Utility methods to create {@link ClientHttpRequestFactory} using the Jetty Client.
	 *
	 * @author Mark Paluch
	 */
	public static class JettyClient {

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
			return ClientConfiguration.JettyClient.configureClient(httpClient, options);
		}

		public static org.eclipse.jetty.client.HttpClient getHttpClient(SslConfiguration sslConfiguration)
				throws IOException, GeneralSecurityException {
			return ClientConfiguration.JettyClient.getHttpClient(sslConfiguration);
		}

	}

	/**
	 * {@link ClientHttpRequestFactory} using the JDK's HttpClient.
	 *
	 * @author Mark Paluch
	 */
	public static class JdkHttpClient {

		/**
		 * Create a {@link JdkClientHttpConnector} using the JDK's HttpClient.
		 * @param options must not be {@literal null}
		 * @param sslConfiguration must not be {@literal null}
		 * @return a new and configured {@link JdkClientHttpConnector} instance.
		 * @throws GeneralSecurityException
		 * @throws IOException
		 */
		public static JdkClientHttpConnector usingJdkHttpClient(ClientOptions options,
				SslConfiguration sslConfiguration) throws GeneralSecurityException, IOException {

			java.net.http.HttpClient.Builder builder = getBuilder(options, sslConfiguration);

			return new JdkClientHttpConnector(builder.build());
		}

		public static java.net.http.HttpClient.Builder getBuilder(ClientOptions options,
				SslConfiguration sslConfiguration) throws GeneralSecurityException, IOException {
			return ClientConfiguration.JdkHttpClient.getBuilder(options, sslConfiguration);
		}

	}

}
