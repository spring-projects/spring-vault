/*
 * Copyright 2016-2025 the original author or authors.
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
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.HttpsSupport;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.util.Timeout;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.JettyClientHttpRequestFactory;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;

/**
 * Factory for {@link ClientHttpRequestFactory} that supports Apache HTTP
 * Components, Netty and the JDK HTTP client (in that order). This factory
 * configures a {@link ClientHttpRequestFactory} depending on the available
 * dependencies.
 *
 * @author Mark Paluch
 * @author Ryan Gow
 * @author Spencer Gibb
 * @author Luciano Canales
 * @since 2.2
 */
public class ClientHttpRequestFactoryFactory {

	private static final boolean reactorNettyPresent = ClassUtils.isPresent("reactor.netty.http.client.HttpClient",
			ClientHttpConnectorFactory.class.getClassLoader());

	private static final boolean httpComponentsPresent = ClassUtils.isPresent(
			"org.apache.hc.client5.http.impl.classic.HttpClientBuilder",
			ClientHttpRequestFactoryFactory.class.getClassLoader());

	private static final boolean jettyPresent = ClassUtils.isPresent("org.eclipse.jetty.client.HttpClient",
			ClientHttpConnectorFactory.class.getClassLoader());


	/**
	 * Create a {@link ClientHttpRequestFactory} for the given {@link ClientOptions}
	 * and {@link SslConfiguration}.
	 * @param options must not be {@literal null}
	 * @param sslConfiguration must not be {@literal null}
	 * @return a new {@link ClientHttpRequestFactory}. Lifecycle beans must be
	 * initialized after obtaining.
	 */
	public static ClientHttpRequestFactory create(ClientOptions options, SslConfiguration sslConfiguration) {
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
		} catch (GeneralSecurityException | IOException e) {
			throw new IllegalStateException(e);
		}
	}


	/**
	 * {@link ClientHttpConnector} for Reactor Netty.
	 *
	 * @author Mark Paluch
	 * @since 4.0
	 */
	public static class ReactorNetty {

		/**
		 * Create a {@link ReactorClientHttpRequestFactory} using Reactor Netty.
		 * @param options must not be {@literal null}
		 * @param sslConfiguration must not be {@literal null}
		 * @return a new and configured {@link ReactorClientHttpRequestFactory}
		 * instance.
		 */
		public static ReactorClientHttpRequestFactory usingReactorNetty(ClientOptions options,
				SslConfiguration sslConfiguration) {
			return new ReactorClientHttpRequestFactory(
					ClientConfiguration.ReactorNetty.createClient(options, sslConfiguration));
		}

	}


	/**
	 * Utilities to create a {@link ClientHttpRequestFactory} for Apache Http
	 * Components.
	 *
	 * @author Mark Paluch
	 */
	public static class HttpComponents {

		/**
		 * Create a {@link ClientHttpRequestFactory} using Apache Http Components.
		 * @param options must not be {@literal null}.
		 * @param sslConfiguration must not be {@literal null}.
		 * @return a new and configured {@link HttpComponentsClientHttpRequestFactory}
		 * instance.
		 * @throws GeneralSecurityException in case of SSL configuration errors.
		 * @throws IOException in case of I/O errors.
		 */
		public static HttpComponentsClientHttpRequestFactory usingHttpComponents(ClientOptions options,
				SslConfiguration sslConfiguration) throws GeneralSecurityException, IOException {
			HttpClientBuilder httpClientBuilder = getHttpClientBuilder(options, sslConfiguration);
			return new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
		}

		public static HttpClientBuilder getHttpClientBuilder(ClientOptions options, SslConfiguration sslConfiguration)
				throws GeneralSecurityException, IOException {
			HttpClientBuilder httpClientBuilder = HttpClients.custom();
			httpClientBuilder.setRoutePlanner(
					new SystemDefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE, ProxySelector.getDefault()));
			Timeout readTimeout = Timeout.ofMilliseconds(options.getReadTimeout().toMillis());
			Timeout connectTimeout = Timeout.ofMilliseconds(options.getConnectionTimeout().toMillis());
			ConnectionConfig connectionConfig = ConnectionConfig.custom()
					.setConnectTimeout(connectTimeout) //
					.setSocketTimeout(readTimeout) //
					.build();
			RequestConfig requestConfig = RequestConfig.custom()
					.setConnectionRequestTimeout(connectTimeout)
					.setResponseTimeout(readTimeout)
					.setAuthenticationEnabled(true) //
					.setRedirectsEnabled(true)
					.build();
			PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder //
					.create()
					.setDefaultConnectionConfig(connectionConfig) //
					.setDefaultSocketConfig(SocketConfig.custom() //
							.setSoTimeout(readTimeout)
							.build());

			if (ClientConfiguration.hasSslConfiguration(sslConfiguration)) {
				SSLContext sslContext = ClientConfiguration.getSSLContext(sslConfiguration);
				String[] enabledProtocols = null;
				if (!sslConfiguration.getEnabledProtocols().isEmpty()) {
					enabledProtocols = sslConfiguration.getEnabledProtocols().toArray(new String[0]);
				}
				String[] enabledCipherSuites = null;
				if (!sslConfiguration.getEnabledCipherSuites().isEmpty()) {
					enabledCipherSuites = sslConfiguration.getEnabledCipherSuites().toArray(new String[0]);
				}
				DefaultClientTlsStrategy tlsStrategy = new DefaultClientTlsStrategy(sslContext, enabledProtocols,
						enabledCipherSuites, SSLBufferMode.STATIC, HttpsSupport.getDefaultHostnameVerifier());
				connectionManagerBuilder.setTlsSocketStrategy(tlsStrategy);
			}
			httpClientBuilder.setDefaultRequestConfig(requestConfig);
			httpClientBuilder.setConnectionManager(connectionManagerBuilder.build());
			return httpClientBuilder;
		}

	}

	/**
	 * Utility methods to create {@link ClientHttpRequestFactory} using the Jetty
	 * Client.
	 *
	 * @author Mark Paluch
	 * @since 4.5
	 */
	public static class JettyClient {

		/**
		 * Create a {@link JettyClientHttpRequestFactory} using Jetty.
		 * @param options must not be {@literal null}.
		 * @param sslConfiguration must not be {@literal null}.
		 * @return a new and configured {@link JettyClientHttpConnector} instance.
		 * @throws GeneralSecurityException in case of SSL configuration errors.
		 * @throws IOException in case of I/O errors.
		 */
		public static JettyClientHttpRequestFactory usingJetty(ClientOptions options, SslConfiguration sslConfiguration)
				throws GeneralSecurityException, IOException {
			return new JettyClientHttpRequestFactory(configureClient(getHttpClient(sslConfiguration), options));
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
	 * @since 4.5
	 */
	public static class JdkHttpClient {

		/**
		 * Create a {@link JdkClientHttpRequestFactory} using the JDK's HttpClient.
		 * @param options must not be {@literal null}.
		 * @param sslConfiguration must not be {@literal null}.
		 * @return a new and configured {@link JdkClientHttpRequestFactory} instance.
		 * @throws GeneralSecurityException in case of SSL configuration errors.
		 * @throws IOException in case of I/O errors.
		 */
		public static JdkClientHttpRequestFactory usingJdkHttpClient(ClientOptions options,
				SslConfiguration sslConfiguration) throws GeneralSecurityException, IOException {
			java.net.http.HttpClient.Builder builder = getBuilder(options, sslConfiguration);
			return new JdkClientHttpRequestFactory(builder.build());
		}

		public static java.net.http.HttpClient.Builder getBuilder(ClientOptions options,
				SslConfiguration sslConfiguration) throws GeneralSecurityException, IOException {
			return ClientConfiguration.JdkHttpClient.getBuilder(options, sslConfiguration);
		}

	}

}
