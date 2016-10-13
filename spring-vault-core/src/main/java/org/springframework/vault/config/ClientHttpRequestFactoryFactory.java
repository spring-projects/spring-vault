/*
 * Copyright 2016 the original author or authors.
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
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import com.squareup.okhttp.OkHttpClient;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.Resource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.Netty4ClientHttpRequestFactory;
import org.springframework.http.client.OkHttpClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;

/**
 * Factory for {@link ClientHttpRequestFactory} that supports Apache HTTP Components,
 * OkHttp, Netty and the JDK HTTP client (in that order). This factory configures a
 * {@link ClientHttpRequestFactory} depending on the available dependencies.
 *
 * @author Mark Paluch
 */
public class ClientHttpRequestFactoryFactory {

	private static final Logger logger = LoggerFactory
			.getLogger(ClientHttpRequestFactoryFactory.class);

	private static final boolean HTTP_COMPONENTS_PRESENT = ClassUtils.isPresent(
			"org.apache.http.client.HttpClient",
			ClientHttpRequestFactoryFactory.class.getClassLoader());

	private static final boolean OKHTTP_PRESENT = ClassUtils.isPresent(
			"com.squareup.okhttp.OkHttpClient",
			ClientHttpRequestFactoryFactory.class.getClassLoader());

	private static final boolean NETTY_PRESENT = ClassUtils.isPresent(
			"io.netty.channel.nio.NioEventLoopGroup",
			ClientHttpRequestFactoryFactory.class.getClassLoader());

	/**
	 * Creates a {@link ClientHttpRequestFactory} for the given {@link ClientOptions} and
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

			if (OKHTTP_PRESENT) {
				return OkHttp.usingOkHttp(options, sslConfiguration);
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

	private static SSLContext getSSLContext(SslConfiguration sslConfiguration)
			throws GeneralSecurityException, IOException {

		KeyManager[] keyManagers = sslConfiguration.getKeyStore() != null ? createKeyManagerFactory(
				sslConfiguration.getKeyStore(), sslConfiguration.getKeyStorePassword())
				.getKeyManagers() : null;

		TrustManager[] trustManagers = sslConfiguration.getTrustStore() != null ? createTrustManagerFactory(
				sslConfiguration.getTrustStore(),
				sslConfiguration.getTrustStorePassword()).getTrustManagers()
				: null;

		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyManagers, trustManagers, null);

		return sslContext;
	}

	private static KeyManagerFactory createKeyManagerFactory(Resource keystoreFile,
			String storePassword) throws GeneralSecurityException, IOException {

		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

		loadKeyStore(keystoreFile, storePassword, keyStore);

		KeyManagerFactory keyManagerFactory = KeyManagerFactory
				.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(keyStore,
				StringUtils.hasText(storePassword) ? storePassword.toCharArray()
						: new char[0]);

		return keyManagerFactory;
	}

	private static TrustManagerFactory createTrustManagerFactory(Resource trustFile,
			String storePassword) throws GeneralSecurityException, IOException {

		KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());

		loadKeyStore(trustFile, storePassword, trustStore);

		TrustManagerFactory trustManagerFactory = TrustManagerFactory
				.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(trustStore);

		return trustManagerFactory;
	}

	private static void loadKeyStore(Resource keyStoreResource, String storePassword,
			KeyStore keyStore) throws IOException, NoSuchAlgorithmException,
			CertificateException {

		InputStream inputStream = null;
		try {
			inputStream = keyStoreResource.getInputStream();
			keyStore.load(inputStream,
					StringUtils.hasText(storePassword) ? storePassword.toCharArray()
							: null);
		}
		finally {
			if (inputStream != null) {
				inputStream.close();
			}
		}
	}

	private static boolean hasSslConfiguration(SslConfiguration sslConfiguration) {
		return sslConfiguration.getTrustStore() != null
				|| sslConfiguration.getKeyStore() != null;
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

			if (hasSslConfiguration(sslConfiguration)) {

				SSLContext sslContext = getSSLContext(sslConfiguration);
				SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
						sslContext);
				httpClientBuilder.setSSLSocketFactory(sslSocketFactory);
				httpClientBuilder.setSSLContext(sslContext);
			}

			RequestConfig requestConfig = RequestConfig.custom() //
					.setConnectTimeout(options.getConnectionTimeout()) //
					.setSocketTimeout(options.getReadTimeout()) //
					.setAuthenticationEnabled(true) //
					.build();

			httpClientBuilder.setDefaultRequestConfig(requestConfig);

			return new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
		}
	}

	/**
	 * {@link ClientHttpRequestFactory} for the {@link OkHttpClient}.
	 *
	 * @author Mark Paluch
	 */
	static class OkHttp {

		static ClientHttpRequestFactory usingOkHttp(ClientOptions options,
				SslConfiguration sslConfiguration) throws GeneralSecurityException,
				IOException {

			final OkHttpClient okHttpClient = new OkHttpClient();

			OkHttpClientHttpRequestFactory requestFactory = new OkHttpClientHttpRequestFactory(
					okHttpClient) {

				@Override
				public void destroy() throws IOException {

					if (okHttpClient.getCache() != null) {
						okHttpClient.getCache().close();
					}

					okHttpClient.getDispatcher().getExecutorService().shutdown();
				}
			};

			if (hasSslConfiguration(sslConfiguration)) {
				okHttpClient.setSslSocketFactory(getSSLContext(sslConfiguration)
						.getSocketFactory());
			}

			requestFactory.setConnectTimeout(options.getConnectionTimeout());
			requestFactory.setReadTimeout(options.getReadTimeout());

			return requestFactory;
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

				if (sslConfiguration.getTrustStore() != null) {
					sslContextBuilder.trustManager(createTrustManagerFactory(
							sslConfiguration.getTrustStore(),
							sslConfiguration.getTrustStorePassword()));
				}

				if (sslConfiguration.getKeyStore() != null) {
					sslContextBuilder.keyManager(createKeyManagerFactory(
							sslConfiguration.getKeyStore(),
							sslConfiguration.getKeyStorePassword()));
				}

				requestFactory.setSslContext(sslContextBuilder.sslProvider(
						SslProvider.JDK).build());
			}

			requestFactory.setConnectTimeout(options.getConnectionTimeout());
			requestFactory.setReadTimeout(options.getReadTimeout());

			return requestFactory;
		}
	}
}
