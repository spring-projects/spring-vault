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
import java.util.List;
import javax.net.ssl.*;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.eclipse.jetty.client.transport.HttpClientTransportOverHTTP;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory.Client;
import org.jspecify.annotations.Nullable;
import reactor.netty.http.client.HttpClient;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.PemObject;
import org.springframework.vault.support.SslConfiguration;

/**
 * Shared utility class to provide client configuration regardless of the used
 * facade (synchronous or reactive).
 *
 * @author Mark Paluch
 * @since 4.0
 */
class ClientConfiguration {

	@SuppressWarnings("FieldMayBeFinal") // allow setting via reflection.
	private static Log logger = LogFactory.getLog(ClientHttpRequestFactoryFactory.class);

	static SSLContext getSSLContext(SslConfiguration sslConfiguration) throws GeneralSecurityException, IOException {
		return getSSLContext(sslConfiguration.getKeyStoreConfiguration(), sslConfiguration.getKeyConfiguration(),
				getTrustManagers(sslConfiguration));
	}

	static SSLContext getSSLContext(SslConfiguration.KeyStoreConfiguration keyStoreConfiguration,
			SslConfiguration.KeyConfiguration keyConfiguration, TrustManager @Nullable [] trustManagers)
			throws GeneralSecurityException, IOException {
		KeyManager[] keyManagers = keyStoreConfiguration.isPresent()
				? createKeyManagerFactory(keyStoreConfiguration, keyConfiguration).getKeyManagers()
				: null;
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyManagers, trustManagers, null);
		return sslContext;
	}

	static KeyManagerFactory createKeyManagerFactory(SslConfiguration.KeyStoreConfiguration keyStoreConfiguration,
			SslConfiguration.KeyConfiguration keyConfiguration) throws GeneralSecurityException, IOException {
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

	static KeyStore getKeyStore(SslConfiguration.KeyStoreConfiguration keyStoreConfiguration)
			throws IOException, GeneralSecurityException {
		KeyStore keyStore = KeyStore.getInstance(getKeyStoreType(keyStoreConfiguration));
		loadKeyStore(keyStoreConfiguration, keyStore);
		return keyStore;
	}

	static TrustManager @Nullable [] getTrustManagers(SslConfiguration sslConfiguration)
			throws GeneralSecurityException, IOException {
		return sslConfiguration.getTrustStoreConfiguration().isPresent()
				? createTrustManagerFactory(sslConfiguration.getTrustStoreConfiguration()).getTrustManagers()
				: null;
	}

	private static String getKeyStoreType(SslConfiguration.KeyStoreConfiguration keyStoreConfiguration) {
		if (StringUtils.hasText(keyStoreConfiguration.getStoreType())
				&& !SslConfiguration.PEM_KEYSTORE_TYPE.equalsIgnoreCase(keyStoreConfiguration.getStoreType())) {
			return keyStoreConfiguration.getStoreType();
		}
		return KeyStore.getDefaultType();
	}

	static TrustManagerFactory createTrustManagerFactory(SslConfiguration.KeyStoreConfiguration keyStoreConfiguration)
			throws GeneralSecurityException, IOException {
		KeyStore trustStore = getKeyStore(keyStoreConfiguration);
		TrustManagerFactory trustManagerFactory = TrustManagerFactory
				.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(trustStore);
		return trustManagerFactory;
	}

	private static void loadKeyStore(SslConfiguration.KeyStoreConfiguration keyStoreConfiguration, KeyStore keyStore)
			throws IOException, GeneralSecurityException {
		if (logger.isDebugEnabled()) {
			logger.debug("Loading keystore from %s".formatted(keyStoreConfiguration.getResource()));
		}
		try (InputStream inputStream = keyStoreConfiguration.getResource().getInputStream()) {
			if (SslConfiguration.PEM_KEYSTORE_TYPE.equalsIgnoreCase(keyStoreConfiguration.getStoreType())) {
				keyStore.load(null);
				loadFromPem(keyStore, inputStream);
			} else {
				keyStore.load(inputStream, keyStoreConfiguration.getStorePassword());
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Keystore loaded with %d entries".formatted(keyStore.size()));
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
	 * Client Configuration for Apache HTTP Components.
	 *
	 * @author Mark Paluch
	 */
	public static class HttpComponents {

		public static ConnectionConfig getConnectionConfig(ClientOptions options) {
			Timeout readTimeout = Timeout.ofMilliseconds(options.getReadTimeout().toMillis());
			Timeout connectTimeout = Timeout.ofMilliseconds(options.getConnectionTimeout().toMillis());
			return ConnectionConfig.custom()
					.setConnectTimeout(connectTimeout) //
					.setSocketTimeout(readTimeout) //
					.build();
		}

		public static RequestConfig getRequestConfig(ClientOptions options) {
			Timeout readTimeout = Timeout.ofMilliseconds(options.getReadTimeout().toMillis());
			return RequestConfig.custom()
					.setResponseTimeout(readTimeout)
					.setAuthenticationEnabled(true) //
					.setRedirectsEnabled(true)
					.build();
		}

		public static SocketConfig getSocketConfig(ClientOptions options) {
			Timeout readTimeout = Timeout.ofMilliseconds(options.getReadTimeout().toMillis());
			return SocketConfig.custom() //
					.setSoTimeout(readTimeout)
					.build();
		}

	}


	/**
	 * {@link ClientHttpConnector} for Reactor Netty.
	 *
	 * @author Mark Paluch
	 */
	public static class ReactorNetty {

		public static HttpClient createClient(ClientOptions options, SslConfiguration sslConfiguration) {

			HttpClient client = HttpClient.create();

			if (hasSslConfiguration(sslConfiguration)) {
				client = client.secure(builder -> {
					SslContextBuilder sslContextBuilder = SslContextBuilder.forClient();
					configureSsl(sslConfiguration, sslContextBuilder);
					try {
						builder.sslContext(sslContextBuilder.build());
					} catch (SSLException e) {
						throw new RuntimeException(e);
					}
				});
			}

			client = client
					.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
							Math.toIntExact(options.getConnectionTimeout().toMillis()))
					.proxyWithSystemProperties();

			return client;
		}

		public static void configureSsl(SslConfiguration sslConfiguration, SslContextBuilder sslContextBuilder) {

			try {
				if (sslConfiguration.getTrustStoreConfiguration().isPresent()) {
					sslContextBuilder
							.trustManager(createTrustManagerFactory(sslConfiguration.getTrustStoreConfiguration()));
				}
				if (sslConfiguration.getKeyStoreConfiguration().isPresent()) {
					sslContextBuilder.keyManager(createKeyManagerFactory(sslConfiguration.getKeyStoreConfiguration(),
							sslConfiguration.getKeyConfiguration()));
				}
				sslConfiguration.enabledProtocols(sslContextBuilder::protocols);
				if (!sslConfiguration.getEnabledCipherSuites().isEmpty()) {
					sslContextBuilder.ciphers(sslConfiguration.getEnabledCipherSuites());
				}
			} catch (GeneralSecurityException | IOException e) {
				throw new IllegalStateException(e);
			}
		}

	}


	/**
	 * Utility methods to create {@link ClientHttpRequestFactory} using the Jetty
	 * Client.
	 *
	 * @author Mark Paluch
	 */
	public static class JettyClient {

		public static org.eclipse.jetty.client.HttpClient configureClient(
				org.eclipse.jetty.client.HttpClient httpClient, ClientOptions options) {
			httpClient.setConnectTimeout(options.getConnectionTimeout().toMillis());
			httpClient.setAddressResolutionTimeout(options.getConnectionTimeout().toMillis());
			return httpClient;
		}

		public static org.eclipse.jetty.client.HttpClient getHttpClient(SslConfiguration sslConfiguration)
				throws IOException, GeneralSecurityException {
			if (hasSslConfiguration(sslConfiguration)) {
				Client sslContextFactory = getSslContextFactory(sslConfiguration);
				ClientConnector connector = new ClientConnector();
				connector.setSslContextFactory(sslContextFactory);
				return new org.eclipse.jetty.client.HttpClient(new HttpClientTransportOverHTTP(connector));
			}
			return new org.eclipse.jetty.client.HttpClient();
		}

		public static SslContextFactory.Client getSslContextFactory(SslConfiguration sslConfiguration)
				throws IOException, GeneralSecurityException {
			Client sslContextFactory = new Client();
			if (sslConfiguration.getKeyStoreConfiguration().isPresent()) {
				KeyStore keyStore = getKeyStore(sslConfiguration.getKeyStoreConfiguration());
				sslContextFactory.setKeyStore(keyStore);
			}
			if (sslConfiguration.getTrustStoreConfiguration().isPresent()) {
				KeyStore keyStore = getKeyStore(sslConfiguration.getTrustStoreConfiguration());
				sslContextFactory.setTrustStore(keyStore);
			}
			SslConfiguration.KeyConfiguration keyConfiguration = sslConfiguration.getKeyConfiguration();
			if (keyConfiguration.getKeyAlias() != null) {
				sslContextFactory.setCertAlias(keyConfiguration.getKeyAlias());
			}
			if (keyConfiguration.getKeyPassword() != null) {
				sslContextFactory.setKeyManagerPassword(new String(keyConfiguration.getKeyPassword()));
			}
			sslConfiguration.enabledProtocols(sslContextFactory::setIncludeProtocols);
			sslConfiguration.enabledCipherSuites(sslContextFactory::setIncludeCipherSuites);
			return sslContextFactory;
		}

	}


	/**
	 * {@link ClientHttpRequestFactory} using the JDK's HttpClient.
	 *
	 * @author Mark Paluch
	 */
	public static class JdkHttpClient {

		public static java.net.http.HttpClient.Builder getBuilder(ClientOptions options,
				SslConfiguration sslConfiguration) throws GeneralSecurityException, IOException {
			java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder();
			if (hasSslConfiguration(sslConfiguration)) {
				SSLContext sslContext = getSSLContext(sslConfiguration);
				SSLParameters parameters = new SSLParameters();
				sslConfiguration.enabledProtocols(parameters::setProtocols);
				sslConfiguration.enabledCipherSuites(parameters::setCipherSuites);
				builder.sslContext(sslContext).sslParameters(parameters);
			}
			builder.proxy(ProxySelector.getDefault())
					.followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
					.connectTimeout(options.getConnectionTimeout());
			return builder;
		}

	}


	static class KeySelectingKeyManagerFactory extends KeyManagerFactory {

		KeySelectingKeyManagerFactory(KeyManagerFactory factory, SslConfiguration.KeyConfiguration keyConfiguration) {
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

						return new KeyManager[] {new KeySelectingX509KeyManager(
								(X509ExtendedKeyManager) keyManagers[0], keyConfiguration)};
					}

					return keyManagers;
				}

			}, factory.getProvider(), factory.getAlgorithm());
		}

	}


	private static class KeySelectingX509KeyManager extends X509ExtendedKeyManager {

		private final X509ExtendedKeyManager delegate;

		private final SslConfiguration.KeyConfiguration keyConfiguration;

		KeySelectingX509KeyManager(X509ExtendedKeyManager delegate,
				SslConfiguration.KeyConfiguration keyConfiguration) {
			this.delegate = delegate;
			this.keyConfiguration = keyConfiguration;
		}

		@Override
		public String[] getClientAliases(String keyType, Principal[] issuers) {
			return this.delegate.getClientAliases(keyType, issuers);
		}

		@Override
		public @Nullable String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
			return this.keyConfiguration.getKeyAlias();
		}

		public @Nullable String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
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
