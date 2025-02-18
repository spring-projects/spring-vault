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
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient.Builder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.ssl.HttpsSupport;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.PemObject;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.SslConfiguration.KeyConfiguration;
import org.springframework.vault.support.SslConfiguration.KeyStoreConfiguration;

/**
 * Factory for {@link ClientHttpRequestFactory} that supports Apache HTTP Components,
 * OkHttp, Netty and the JDK HTTP client (in that order). This factory configures a
 * {@link ClientHttpRequestFactory} depending on the available dependencies.
 *
 * @author Mark Paluch
 * @author Ryan Gow
 * @author Spencer Gibb
 * @since 2.2
 */
public class ClientHttpRequestFactoryFactory {

	@SuppressWarnings("FieldMayBeFinal") // allow setting via reflection.
	private static Log logger = LogFactory.getLog(ClientHttpRequestFactoryFactory.class);

	private static final boolean httpComponentsPresent = ClassUtils.isPresent(
			"org.apache.hc.client5.http.impl.classic.HttpClientBuilder",
			ClientHttpRequestFactoryFactory.class.getClassLoader());

	private static final boolean okHttp3Present = ClassUtils.isPresent("okhttp3.OkHttpClient",
			ClientHttpRequestFactoryFactory.class.getClassLoader());

	/**
	 * Create a {@link ClientHttpRequestFactory} for the given {@link ClientOptions} and
	 * {@link SslConfiguration}.
	 * @param options must not be {@literal null}
	 * @param sslConfiguration must not be {@literal null}
	 * @return a new {@link ClientHttpRequestFactory}. Lifecycle beans must be initialized
	 * after obtaining.
	 */
	public static ClientHttpRequestFactory create(ClientOptions options, SslConfiguration sslConfiguration) {

		Assert.notNull(options, "ClientOptions must not be null");
		Assert.notNull(sslConfiguration, "SslConfiguration must not be null");

		try {

			if (httpComponentsPresent) {
				return HttpComponents.usingHttpComponents(options, sslConfiguration);
			}

			if (okHttp3Present) {
				return OkHttp3.usingOkHttp3(options, sslConfiguration);
			}
		}
		catch (GeneralSecurityException | IOException e) {
			throw new IllegalStateException(e);
		}

		if (hasSslConfiguration(sslConfiguration)) {
			logger.warn("VaultProperties has SSL configured but the SSL configuration "
					+ "must be applied outside the Vault Client to use the JDK HTTP client");
		}

		return new SimpleClientHttpRequestFactory();
	}

	static SSLContext getSSLContext(SslConfiguration sslConfiguration) throws GeneralSecurityException, IOException {

		return getSSLContext(sslConfiguration.getKeyStoreConfiguration(), sslConfiguration.getKeyConfiguration(),
				getTrustManagers(sslConfiguration));
	}

	static SSLContext getSSLContext(KeyStoreConfiguration keyStoreConfiguration, KeyConfiguration keyConfiguration,
			@Nullable TrustManager[] trustManagers) throws GeneralSecurityException, IOException {

		KeyManager[] keyManagers = keyStoreConfiguration.isPresent()
				? createKeyManagerFactory(keyStoreConfiguration, keyConfiguration).getKeyManagers() : null;

		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyManagers, trustManagers, null);

		return sslContext;
	}

	static KeyManagerFactory createKeyManagerFactory(KeyStoreConfiguration keyStoreConfiguration,
			KeyConfiguration keyConfiguration) throws GeneralSecurityException, IOException {

		KeyStore keyStore = getKeyStore(keyStoreConfiguration);

		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

		char[] keyPasswordToUse = keyConfiguration.getKeyPassword();

		if (keyPasswordToUse == null) {
			keyPasswordToUse = keyStoreConfiguration.getStorePassword() == null ? new char[0]
					: keyStoreConfiguration.getStorePassword();
		}

		keyManagerFactory.init(keyStore, keyPasswordToUse);

		if (StringUtils.hasText(keyConfiguration.getKeyAlias())) {
			return new KeySelectingKeyManagerFactory(keyManagerFactory, keyConfiguration);
		}

		return keyManagerFactory;
	}

	static KeyStore getKeyStore(KeyStoreConfiguration keyStoreConfiguration)
			throws IOException, GeneralSecurityException {

		KeyStore keyStore = KeyStore.getInstance(getKeyStoreType(keyStoreConfiguration));

		loadKeyStore(keyStoreConfiguration, keyStore);
		return keyStore;
	}

	@Nullable
	static TrustManager[] getTrustManagers(SslConfiguration sslConfiguration)
			throws GeneralSecurityException, IOException {

		return sslConfiguration.getTrustStoreConfiguration().isPresent()
				? createTrustManagerFactory(sslConfiguration.getTrustStoreConfiguration()).getTrustManagers() : null;
	}

	private static String getKeyStoreType(KeyStoreConfiguration keyStoreConfiguration) {

		if (StringUtils.hasText(keyStoreConfiguration.getStoreType())
				&& !SslConfiguration.PEM_KEYSTORE_TYPE.equalsIgnoreCase(keyStoreConfiguration.getStoreType())) {
			return keyStoreConfiguration.getStoreType();
		}

		return KeyStore.getDefaultType();
	}

	static TrustManagerFactory createTrustManagerFactory(KeyStoreConfiguration keyStoreConfiguration)
			throws GeneralSecurityException, IOException {

		KeyStore trustStore = getKeyStore(keyStoreConfiguration);

		TrustManagerFactory trustManagerFactory = TrustManagerFactory
			.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(trustStore);

		return trustManagerFactory;
	}

	private static void loadKeyStore(KeyStoreConfiguration keyStoreConfiguration, KeyStore keyStore)
			throws IOException, GeneralSecurityException {

		if (logger.isDebugEnabled()) {
			logger.debug("Loading keystore from %s".formatted(keyStoreConfiguration.getResource()));
		}

		InputStream inputStream = null;
		try {
			inputStream = keyStoreConfiguration.getResource().getInputStream();

			if (SslConfiguration.PEM_KEYSTORE_TYPE.equalsIgnoreCase(keyStoreConfiguration.getStoreType())) {

				keyStore.load(null);
				loadFromPem(keyStore, inputStream);
			}
			else {
				keyStore.load(inputStream, keyStoreConfiguration.getStorePassword());
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Keystore loaded with %d entries".formatted(keyStore.size()));
			}
		}
		finally {
			if (inputStream != null) {
				inputStream.close();
			}
		}
	}

	private static void loadFromPem(KeyStore keyStore, InputStream inputStream) throws IOException, KeyStoreException {

		List<PemObject> pemObjects = PemObject.parse(new String(FileCopyUtils.copyToByteArray(inputStream)));

		for (PemObject pemObject : pemObjects) {
			if (pemObject.isCertificate()) {
				X509Certificate cert = pemObject.getCertificate();
				String alias = cert.getSubjectX500Principal().getName();

				if (logger.isDebugEnabled()) {
					logger.debug("Adding certificate with alias %s".formatted(alias));
				}

				keyStore.setCertificateEntry(alias, cert);
			}
		}
	}

	static boolean hasSslConfiguration(SslConfiguration sslConfiguration) {
		return sslConfiguration.getTrustStoreConfiguration().isPresent()
				|| sslConfiguration.getKeyStoreConfiguration().isPresent();
	}

	/**
	 * Utilities to create a {@link ClientHttpRequestFactory} for Apache Http Components.
	 *
	 * @author Mark Paluch
	 */
	public static class HttpComponents {

		/**
		 * Create a {@link ClientHttpRequestFactory} using Apache Http Components.
		 * @param options must not be {@literal null}
		 * @param sslConfiguration must not be {@literal null}
		 * @return a new and configured {@link HttpComponentsClientHttpRequestFactory}
		 * instance.
		 * @throws GeneralSecurityException
		 * @throws IOException
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

			if (hasSslConfiguration(sslConfiguration)) {

				SSLContext sslContext = getSSLContext(sslConfiguration);

				String[] enabledProtocols = null;

				if (!sslConfiguration.getEnabledProtocols().isEmpty()) {
					enabledProtocols = sslConfiguration.getEnabledProtocols().toArray(new String[0]);
				}

				String[] enabledCipherSuites = null;

				if (!sslConfiguration.getEnabledCipherSuites().isEmpty()) {
					enabledCipherSuites = sslConfiguration.getEnabledCipherSuites().toArray(new String[0]);
				}

				SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext,
						enabledProtocols, enabledCipherSuites, HttpsSupport.getDefaultHostnameVerifier());
				connectionManagerBuilder.setSSLSocketFactory(sslSocketFactory);
			}

			httpClientBuilder.setDefaultRequestConfig(requestConfig);
			httpClientBuilder.setConnectionManager(connectionManagerBuilder.build());

			return httpClientBuilder;
		}

	}

	/**
	 * Utilities to create a {@link ClientHttpRequestFactory} for the
	 * {@link okhttp3.OkHttpClient}.
	 *
	 * @author Mark Paluch
	 */
	public static class OkHttp3 {

		/**
		 * Create a {@link ClientHttpRequestFactory} using {@link okhttp3.OkHttpClient}.
		 * @param options must not be {@literal null}
		 * @param sslConfiguration must not be {@literal null}
		 * @return a new and configured {@link OkHttp3ClientHttpRequestFactory} instance.
		 * @throws GeneralSecurityException
		 * @throws IOException
		 */
		public static OkHttp3ClientHttpRequestFactory usingOkHttp3(ClientOptions options,
				SslConfiguration sslConfiguration) throws GeneralSecurityException, IOException {

			Builder builder = getBuilder(options, sslConfiguration);

			return new OkHttp3ClientHttpRequestFactory(builder.build());
		}

		public static Builder getBuilder(ClientOptions options, SslConfiguration sslConfiguration)
				throws GeneralSecurityException, IOException {

			Builder builder = new Builder();

			ConnectionSpec sslConnectionSpec = ConnectionSpec.MODERN_TLS;

			if (hasSslConfiguration(sslConfiguration)) {

				TrustManager[] trustManagers = getTrustManagers(sslConfiguration);

				if (trustManagers == null || trustManagers.length != 1
						|| !(trustManagers[0] instanceof X509TrustManager)) {
					throw new IllegalStateException(
							"Unexpected default trust managers:" + Arrays.toString(trustManagers));
				}

				SSLContext sslContext = getSSLContext(sslConfiguration.getKeyStoreConfiguration(),
						sslConfiguration.getKeyConfiguration(), trustManagers);

				ConnectionSpec.Builder sslConnectionSpecBuilder = new ConnectionSpec.Builder(sslConnectionSpec);

				if (!sslConfiguration.getEnabledProtocols().isEmpty()) {
					sslConnectionSpecBuilder.tlsVersions(sslConfiguration.getEnabledProtocols().toArray(new String[0]));
				}

				if (!sslConfiguration.getEnabledCipherSuites().isEmpty()) {
					sslConnectionSpecBuilder
						.cipherSuites(sslConfiguration.getEnabledCipherSuites().toArray(new String[0]));
				}

				sslConnectionSpec = sslConnectionSpecBuilder.build();

				builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0]);
			}

			builder.connectionSpecs(Arrays.asList(sslConnectionSpec, ConnectionSpec.CLEARTEXT));

			builder.connectTimeout(options.getConnectionTimeout().toMillis(), TimeUnit.MILLISECONDS)
				.readTimeout(options.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS);
			return builder;
		}

	}

	static class KeySelectingKeyManagerFactory extends KeyManagerFactory {

		KeySelectingKeyManagerFactory(KeyManagerFactory factory, KeyConfiguration keyConfiguration) {
			super(new KeyManagerFactorySpi() {
				@Override
				protected void engineInit(KeyStore keyStore, char[] chars)
						throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
					factory.init(keyStore, chars);
				}

				@Override
				protected void engineInit(ManagerFactoryParameters managerFactoryParameters)
						throws InvalidAlgorithmParameterException {
					factory.init(managerFactoryParameters);
				}

				@Override
				protected KeyManager[] engineGetKeyManagers() {

					KeyManager[] keyManagers = factory.getKeyManagers();

					if (keyManagers.length == 1 && keyManagers[0] instanceof X509ExtendedKeyManager) {

						return new KeyManager[] { new KeySelectingX509KeyManager(
								(X509ExtendedKeyManager) keyManagers[0], keyConfiguration) };
					}

					return keyManagers;
				}
			}, factory.getProvider(), factory.getAlgorithm());
		}

	}

	private static class KeySelectingX509KeyManager extends X509ExtendedKeyManager {

		private final X509ExtendedKeyManager delegate;

		private final KeyConfiguration keyConfiguration;

		KeySelectingX509KeyManager(X509ExtendedKeyManager delegate, KeyConfiguration keyConfiguration) {
			this.delegate = delegate;
			this.keyConfiguration = keyConfiguration;
		}

		@Override
		public String[] getClientAliases(String keyType, Principal[] issuers) {
			return this.delegate.getClientAliases(keyType, issuers);
		}

		@Override
		public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
			return this.keyConfiguration.getKeyAlias();
		}

		public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
			return this.keyConfiguration.getKeyAlias();
		}

		@Override
		public String[] getServerAliases(String keyType, Principal[] issuers) {
			return this.delegate.getServerAliases(keyType, issuers);
		}

		@Override
		public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
			return this.delegate.chooseServerAlias(keyType, issuers, socket);
		}

		@Override
		public X509Certificate[] getCertificateChain(String alias) {
			return this.delegate.getCertificateChain(alias);
		}

		@Override
		public PrivateKey getPrivateKey(String alias) {
			return this.delegate.getPrivateKey(alias);
		}

	}

}
