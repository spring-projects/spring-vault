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
package org.springframework.vault.support;

import java.security.KeyStore;
import java.util.Arrays;

import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * SSL configuration.
 * <p>
 * Provides configuration for a key store and trust store for TLS certificate
 * verification. Key store and trust store may be left unconfigured if the JDK trust store
 * contains all necessary certificates to verify TLS certificates. The key store is used
 * for Client Certificate authentication.
 *
 * @author Mark Paluch
 * @see Resource
 * @see java.security.KeyStore
 * @see org.springframework.vault.authentication.ClientCertificateAuthentication
 */
public class SslConfiguration {

	/**
	 * Default {@link SslConfiguration} without a KeyStore/TrustStore configured.
	 */
	public static final SslConfiguration NONE = new SslConfiguration(
			KeyStoreConfiguration.EMPTY, KeyStoreConfiguration.EMPTY);

	private final KeyStoreConfiguration keyStoreConfiguration;

	private final KeyStoreConfiguration trustStoreConfiguration;

	/**
	 * Create a new {@link SslConfiguration} with the default {@link KeyStore} type.
	 *
	 * @param keyStore the key store resource.
	 * @param keyStorePassword the key store password.
	 * @param trustStore the trust store resource.
	 * @param trustStorePassword the trust store password.
	 * @deprecated Since 1.1, use
	 * {@link #SslConfiguration(KeyStoreConfiguration, KeyStoreConfiguration)} to prevent
	 * {@link String} interning and retaining passwords represented as String longer from
	 * GC than necessary.
	 */
	@Deprecated
	public SslConfiguration(Resource keyStore, String keyStorePassword,
			Resource trustStore, String trustStorePassword) {

		this(new KeyStoreConfiguration(keyStore, charsOrNull(keyStorePassword),
				KeyStore.getDefaultType()),
				new KeyStoreConfiguration(trustStore, charsOrNull(trustStorePassword),
						KeyStore.getDefaultType()));
	}

	/**
	 * Create a new {@link SslConfiguration}.
	 *
	 * @param keyStoreConfiguration the key store configuration.
	 * @param trustStoreConfiguration the trust store configuration.
	 * @since 1.1
	 */
	public SslConfiguration(KeyStoreConfiguration keyStoreConfiguration,
			KeyStoreConfiguration trustStoreConfiguration) {

		Assert.notNull(keyStoreConfiguration, "KeyStore configuration must not be null");
		Assert.notNull(trustStoreConfiguration,
				"TrustStore configuration must not be null");

		this.keyStoreConfiguration = keyStoreConfiguration;
		this.trustStoreConfiguration = trustStoreConfiguration;
	}

	/**
	 * Create a new {@link SslConfiguration} for the given trust store with the default
	 * {@link KeyStore} type.
	 *
	 * @param trustStore resource pointing to an existing trust store, must not be
	 * {@literal null}.
	 * @param trustStorePassword may be {@literal null}.
	 * @return the created {@link SslConfiguration}.
	 * @see java.security.KeyStore
	 * @deprecated Since 1.1, use {@link #forTrustStore(Resource, char[])} to prevent
	 * {@link String} interning and retaining passwords represented as String longer from
	 * GC than necessary.
	 */
	@Deprecated
	public static SslConfiguration forTrustStore(Resource trustStore,
			String trustStorePassword) {
		return forTrustStore(trustStore, charsOrNull(trustStorePassword));
	}

	/**
	 * Create a new {@link SslConfiguration} for the given trust store with the default
	 * {@link KeyStore} type.
	 *
	 * @param trustStore resource pointing to an existing trust store, must not be
	 * {@literal null}.
	 * @param trustStorePassword may be {@literal null}.
	 * @return the created {@link SslConfiguration}.
	 * @see java.security.KeyStore
	 */
	public static SslConfiguration forTrustStore(Resource trustStore,
			char[] trustStorePassword) {

		Assert.notNull(trustStore, "TrustStore must not be null");
		Assert.notNull(trustStore.exists(),
				String.format("TrustStore %s does not exist", trustStore));

		return new SslConfiguration(KeyStoreConfiguration.EMPTY,
				new KeyStoreConfiguration(trustStore, trustStorePassword,
						KeyStore.getDefaultType()));
	}

	/**
	 * Create a new {@link SslConfiguration} for the given key store with the default
	 * {@link KeyStore} type.
	 *
	 * @param keyStore resource pointing to an existing key store, must not be
	 * {@literal null}.
	 * @param keyStorePassword may be {@literal null}.
	 * @return the created {@link SslConfiguration}.
	 * @see java.security.KeyStore
	 * @deprecated Since 1.1, use {@link #forKeyStore(Resource, char[])} to prevent
	 * {@link String} interning and retaining passwords represented as String longer from
	 * GC than necessary.
	 */
	@Deprecated
	public static SslConfiguration forKeyStore(Resource keyStore,
			String keyStorePassword) {
		return forKeyStore(keyStore, charsOrNull(keyStorePassword));
	}

	/**
	 * Create a new {@link SslConfiguration} for the given key store with the default
	 * {@link KeyStore} type.
	 *
	 * @param keyStore resource pointing to an existing key store, must not be
	 * {@literal null}.
	 * @param keyStorePassword may be {@literal null}.
	 * @return the created {@link SslConfiguration}.
	 * @see java.security.KeyStore
	 */
	public static SslConfiguration forKeyStore(Resource keyStore,
			char[] keyStorePassword) {

		Assert.notNull(keyStore, "KeyStore must not be null");
		Assert.notNull(keyStore.exists(),
				String.format("KeyStore %s does not exist", keyStore));

		return new SslConfiguration(new KeyStoreConfiguration(keyStore, keyStorePassword,
				KeyStore.getDefaultType()), KeyStoreConfiguration.EMPTY);
	}

	/**
	 * Create a new {@link SslConfiguration} for the given truststore with the default
	 * {@link KeyStore} type.
	 *
	 * @param keyStore resource pointing to an existing keystore, must not be
	 * {@literal null}.
	 * @param keyStorePassword may be {@literal null}.
	 * @param trustStore resource pointing to an existing trust store, must not be
	 * {@literal null}.
	 * @param trustStorePassword may be {@literal null}.
	 * @return the created {@link SslConfiguration}.
	 * @see java.security.KeyStore
	 * @deprecated Since 1.1, use {@link #create(Resource, char[], Resource, char[])} to
	 * prevent {@link String} interning and retaining passwords represented as String
	 * longer from GC than necessary.
	 */
	@Deprecated
	public SslConfiguration create(Resource keyStore, String keyStorePassword,
			Resource trustStore, String trustStorePassword) {
		return create(keyStore, charsOrNull(keyStorePassword), trustStore,
				charsOrNull(trustStorePassword));
	}

	/**
	 * Create a new {@link SslConfiguration} for the given truststore with the default
	 * {@link KeyStore} type.
	 *
	 * @param keyStore resource pointing to an existing keystore, must not be
	 * {@literal null}.
	 * @param keyStorePassword may be {@literal null}.
	 * @param trustStore resource pointing to an existing trust store, must not be
	 * {@literal null}.
	 * @param trustStorePassword may be {@literal null}.
	 * @return the created {@link SslConfiguration}.
	 * @see java.security.KeyStore
	 */
	public SslConfiguration create(Resource keyStore, char[] keyStorePassword,
			Resource trustStore, char[] trustStorePassword) {

		Assert.notNull(keyStore, "KeyStore must not be null");
		Assert.notNull(keyStore.exists(),
				String.format("KeyStore %s does not exist", trustStore));

		Assert.notNull(trustStore, "TrustStore must not be null");
		Assert.notNull(trustStore.exists(),
				String.format("TrustStore %s does not exist", trustStore));

		return new SslConfiguration(
				new KeyStoreConfiguration(keyStore, keyStorePassword,
						KeyStore.getDefaultType()),
				new KeyStoreConfiguration(trustStore, trustStorePassword,
						KeyStore.getDefaultType()));
	}

	/**
	 * @return the {@link java.security.KeyStore key store} resource or {@literal null} if
	 * not configured.
	 */
	public Resource getKeyStore() {
		return keyStoreConfiguration.getResource();
	}

	/**
	 * @return the key store password or {@literal null} if not configured.
	 * @deprecated Since 1.1, use {@link KeyStoreConfiguration#getStorePassword()} to
	 * prevent {@link String} interning and retaining passwords represented as String
	 * longer from GC than necessary.
	 */
	@Deprecated
	public String getKeyStorePassword() {
		return stringOrNull(keyStoreConfiguration.getStorePassword());
	}

	/**
	 * @return the key store configuration.
	 * @since 1.1
	 */
	public KeyStoreConfiguration getKeyStoreConfiguration() {
		return keyStoreConfiguration;
	}

	/**
	 * @return the {@link java.security.KeyStore key store} resource or {@literal null} if
	 * not configured.
	 */
	public Resource getTrustStore() {
		return trustStoreConfiguration.getResource();
	}

	/**
	 * @return the trust store password or {@literal null} if not configured.
	 * @deprecated Since 1.1, use {@link KeyStoreConfiguration#getStorePassword()} to
	 * prevent {@link String} interning and retaining passwords represented as String
	 * longer from GC than necessary.
	 */
	@Deprecated
	public String getTrustStorePassword() {
		return stringOrNull(trustStoreConfiguration.getStorePassword());
	}

	/**
	 * @return the key store configuration.
	 * @since 1.1
	 */
	public KeyStoreConfiguration getTrustStoreConfiguration() {
		return trustStoreConfiguration;
	}

	private static String stringOrNull(char[] storePassword) {
		return storePassword != null ? new String(storePassword) : null;
	}

	private static char[] charsOrNull(String trustStorePassword) {
		return trustStorePassword == null ? null : trustStorePassword.toCharArray();
	}

	/**
	 * Configuration for a key store/trust store.
	 *
	 * @since 1.1
	 */
	public static class KeyStoreConfiguration {

		public static final KeyStoreConfiguration EMPTY = new KeyStoreConfiguration(null,
				null, null);

		/**
		 * Store that holds certificates, private keys, ….
		 */
		private final Resource resource;

		/**
		 * Password used to access the key store/trust store.
		 */
		private final char[] storePassword;

		/**
		 * Key store/trust store type.
		 */
		private final String storeType;

		/**
		 * Create a new {@link KeyStoreConfiguration}.
		 */
		public KeyStoreConfiguration(Resource resource, char[] storePassword,
				String storeType) {

			this.resource = resource;
			this.storeType = storeType;

			if (storePassword == null) {
				this.storePassword = null;
			}
			else {
				this.storePassword = Arrays.copyOf(storePassword, storePassword.length);
			}
		}

		/**
		 * @return the {@link java.security.KeyStore key store} resource or
		 * {@literal null} if not configured.
		 */
		public Resource getResource() {
			return resource;
		}

		/**
		 * @return the key store/trust store password or {@literal null} if not
		 * configured.
		 */
		public char[] getStorePassword() {
			return storePassword;
		}

		/**
		 * @return the trust store type or {@literal null} if not configured.
		 */
		public String getStoreType() {
			return storeType;
		}
	}
}
