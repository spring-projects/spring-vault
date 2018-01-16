/*
 * Copyright 2016-2018 the original author or authors.
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
import java.io.InputStream;
import java.net.ProxySelector;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import okhttp3.OkHttpClient.Builder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.Netty4ClientHttpRequestFactory;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.SslConfiguration.KeyStoreConfiguration;

/**
 * Factory for {@link ClientHttpRequestFactory} that supports Apache HTTP Components,
 * OkHttp, Netty and the JDK HTTP client (in that order). This factory configures a
 * {@link ClientHttpRequestFactory} depending on the available dependencies.
 *
 * @author Mark Paluch
 */
public class ClientHttpRequestFactoryFactory {

	private static final Log logger = LogFactory
			.getLog(ClientHttpRequestFactoryFactory.class);

	private static final boolean HTTP_COMPONENTS_PRESENT = ClassUtils.isPresent(
			"org.apache.http.client.HttpClient",
			ClientHttpRequestFactoryFactory.class.getClassLoader());

	private static final boolean OKHTTP3_PRESENT = ClassUtils.isPresent(
			"okhttp3.OkHttpClient",
			ClientHttpRequestFactoryFactory.class.getClassLoader());

	private static final boolean NETTY_PRESENT = ClassUtils.isPresent(
			"io.netty.channel.nio.NioEventLoopGroup",
			ClientHttpRequestFactoryFactory.class.getClassLoader());

	/**
	 * Create a {@link ClientHttpRequestFactory} for the given {@link ClientOptions} and
	 * {@link SslConfiguration}.
	 *
	 * @param options must not be {@literal null}
	 * @param sslConfiguration must not be {@literal null}
	 * @return a new {@link ClientHttpRequestFactory}. Lifecycle beans must be initialized
	 * after obtaining.
	 */
	public static ClientHttpRequestFactory create(ClientOptions options,
			SslConfiguration sslConfiguration) {

		Assert.notNull(options, "ClientOptions must not be null");
		Assert.notNull(sslConfiguration, "SslConfiguration must not be null");

		try {

			if (HTTP_COMPONENTS_PRESENT) {
				return HttpComponents.usingHttpComponents(options, sslConfiguration);
			}

			if (OKHTTP3_PRESENT) {
				return OkHttp3.usingOkHttp3(options, sslConfiguration);
			}

			if (NETTY_PRESENT) {
				return Netty.usingNetty(options, sslConfiguration);
			}
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}

		if (hasSslConfiguration(sslConfiguration)) {
			logger.warn("VaultProperties has SSL configured but the SSL configuration "
					+ "must be applied outside the Vault Client to use the JDK HTTP client");
		}

		return new SimpleClientHttpRequestFactory();
	}

	static SSLContext getSSLContext(SslConfiguration sslConfiguration)
			throws GeneralSecurityException, IOException {

		KeyManager[] keyManagers = sslConfiguration.getKeyStoreConfiguration()
				.isPresent() ? createKeyManagerFactory(
				sslConfiguration.getKeyStoreConfiguration()).getKeyManagers() : null;

		TrustManager[] trustManagers = sslConfiguration.getTrustStoreConfiguration()
				.isPresent() ? createTrustManagerFactory(
				sslConfiguration.getTrustStoreConfiguration()).getTrustManagers() : null;

		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyManagers, trustManagers, null);

		return sslContext;
	}

	static KeyManagerFactory createKeyManagerFactory(
			KeyStoreConfiguration keyStoreConfiguration) throws GeneralSecurityException,
			IOException {

		KeyStore keyStore = KeyStore.getInstance(StringUtils
				.hasText(keyStoreConfiguration.getStoreType()) ? keyStoreConfiguration
				.getStoreType() : KeyStore.getDefaultType());

		loadKeyStore(keyStoreConfiguration, keyStore);

		KeyManagerFactory keyManagerFactory = KeyManagerFactory
				.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(keyStore,
				keyStoreConfiguration.getStorePassword() == null ? new char[0]
						: keyStoreConfiguration.getStorePassword());

		return keyManagerFactory;
	}

	static TrustManagerFactory createTrustManagerFactory(
			KeyStoreConfiguration keyStoreConfiguration) throws GeneralSecurityException,
			IOException {

		KeyStore trustStore = KeyStore.getInstance(StringUtils
				.hasText(keyStoreConfiguration.getStoreType()) ? keyStoreConfiguration
				.getStoreType() : KeyStore.getDefaultType());

		loadKeyStore(keyStoreConfiguration, trustStore);

		TrustManagerFactory trustManagerFactory = TrustManagerFactory
				.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(trustStore);

		return trustManagerFactory;
	}

	private static void loadKeyStore(KeyStoreConfiguration keyStoreConfiguration,
			KeyStore keyStore) throws IOException, NoSuchAlgorithmException,
			CertificateException {

		InputStream inputStream = null;
		try {
			inputStream = keyStoreConfiguration.getResource().getInputStream();
			keyStore.load(inputStream, keyStoreConfiguration.getStorePassword());
		}
		finally {
			if (inputStream != null) {
				inputStream.close();
			}
		}
	}

	static boolean hasSslConfiguration(SslConfiguration sslConfiguration) {
		return sslConfiguration.getTrustStoreConfiguration().isPresent()
				|| sslConfiguration.getKeyStoreConfiguration().isPresent();
	}

	/**
	 * {@link ClientHttpRequestFactory} for Apache Http Components.
	 *
	 * @author Mark Paluch
	 */
	static class HttpComponents {

		static ClientHttpRequestFactory usingHttpComponents(ClientOptions options,
				SslConfiguration sslConfiguration) throws GeneralSecurityException,
				IOException {

			HttpClientBuilder httpClientBuilder = HttpClients.custom();

			httpClientBuilder.setRoutePlanner(new SystemDefaultRoutePlanner(
					DefaultSchemePortResolver.INSTANCE, ProxySelector.getDefault()));

			if (hasSslConfiguration(sslConfiguration)) {

				SSLContext sslContext = getSSLContext(sslConfiguration);
				SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
						sslContext);
				httpClientBuilder.setSSLSocketFactory(sslSocketFactory);
				httpClientBuilder.setSSLContext(sslContext);
			}

			RequestConfig requestConfig = RequestConfig
					.custom()
					//
					.setConnectTimeout(
							Math.toIntExact(options.getConnectionTimeout().toMillis())) //
					.setSocketTimeout(
							Math.toIntExact(options.getReadTimeout().toMillis())) //
					.setAuthenticationEnabled(true) //
					.build();

			httpClientBuilder.setDefaultRequestConfig(requestConfig);

			// Support redirects
			httpClientBuilder.setRedirectStrategy(new LaxRedirectStrategy());

			return new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
		}
	}

	/**
	 * {@link ClientHttpRequestFactory} for the {@link okhttp3.OkHttpClient}.
	 *
	 * @author Mark Paluch
	 */
	static class OkHttp3 {

		static ClientHttpRequestFactory usingOkHttp3(ClientOptions options,
				SslConfiguration sslConfiguration) throws GeneralSecurityException,
				IOException {

			Builder builder = new Builder();

			if (hasSslConfiguration(sslConfiguration)) {
				builder.sslSocketFactory(getSSLContext(sslConfiguration)
						.getSocketFactory());
			}

			builder.connectTimeout(options.getConnectionTimeout().toMillis(),
					TimeUnit.MILLISECONDS).readTimeout(
					options.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS);

			return new OkHttp3ClientHttpRequestFactory(builder.build());
		}
	}

	/**
	 * {@link ClientHttpRequestFactory} for Netty.
	 *
	 * @author Mark Paluch
	 */
	static class Netty {

		static ClientHttpRequestFactory usingNetty(ClientOptions options,
				SslConfiguration sslConfiguration) throws GeneralSecurityException,
				IOException {

			final Netty4ClientHttpRequestFactory requestFactory = new Netty4ClientHttpRequestFactory();

			if (hasSslConfiguration(sslConfiguration)) {

				SslContextBuilder sslContextBuilder = SslContextBuilder //
						.forClient();

				if (sslConfiguration.getTrustStoreConfiguration().isPresent()) {
					sslContextBuilder
							.trustManager(createTrustManagerFactory(sslConfiguration
									.getTrustStoreConfiguration()));
				}

				if (sslConfiguration.getKeyStoreConfiguration().isPresent()) {
					sslContextBuilder.keyManager(createKeyManagerFactory(sslConfiguration
							.getKeyStoreConfiguration()));
				}

				requestFactory.setSslContext(sslContextBuilder.sslProvider(
						SslProvider.JDK).build());
			}

			requestFactory.setConnectTimeout(Math.toIntExact(options
					.getConnectionTimeout().toMillis()));
			requestFactory.setReadTimeout(Math.toIntExact(options.getReadTimeout()
					.toMillis()));

			return requestFactory;
		}
	}
}
