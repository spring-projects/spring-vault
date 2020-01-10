/*
 * Copyright 2016-2020 the original author or authors.
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
package org.springframework.vault.support;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Arrays;

import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
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

	private static final String DEFAULT_KEYSTORE_TYPE = KeyStore.getDefaultType();

	private final KeyStoreConfiguration keyStoreConfiguration;

	private final KeyStoreConfiguration trustStoreConfiguration;

	/**
	 * Create a new {@link SslConfiguration} with the default {@link KeyStore} type.
	 *
	 * @param keyStore the key store resource, must not be {@literal null}.
	 * @param keyStorePassword the key store password.
	 * @param trustStore the trust store resource, must not be {@literal null}.
	 * @param trustStorePassword the trust store password.
	 * @deprecated Since 1.1, use
	 * {@link #SslConfiguration(KeyStoreConfiguration, KeyStoreConfiguration)} to prevent
	 * {@link String} interning and retaining passwords represented as String longer from
	 * GC than necessary.
	 */
	@Deprecated
	public SslConfiguration(Resource keyStore, @Nullable String keyStorePassword,
			Resource trustStore, @Nullable String trustStorePassword) {

		this(new KeyStoreConfiguration(keyStore, charsOrNull(keyStorePassword),
				DEFAULT_KEYSTORE_TYPE), new KeyStoreConfiguration(trustStore,
				charsOrNull(trustStorePassword), DEFAULT_KEYSTORE_TYPE));
	}

	/**
	 * Create a new {@link SslConfiguration}.
	 *
	 * @param keyStoreConfiguration the key store configuration, must not be
	 * {@literal null}.
	 * @param trustStoreConfiguration the trust store configuration, must not be
	 * {@literal null}.
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
			@Nullable String trustStorePassword) {
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
			@Nullable char[] trustStorePassword) {

		Assert.notNull(trustStore, "TrustStore must not be null");
		Assert.isTrue(trustStore.exists(),
				() -> String.format("TrustStore %s does not exist", trustStore));

		return new SslConfiguration(KeyStoreConfiguration.UNCONFIGURED,
				new KeyStoreConfiguration(trustStore, trustStorePassword,
						DEFAULT_KEYSTORE_TYPE));
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
			@Nullable String keyStorePassword) {
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
	public static SslConfiguration forKeyStore(@Nullable Resource keyStore,
			@Nullable char[] keyStorePassword) {

		Assert.notNull(keyStore, "KeyStore must not be null");
		Assert.isTrue(keyStore.exists(),
				() -> String.format("KeyStore %s does not exist", keyStore));

		return new SslConfiguration(new KeyStoreConfiguration(keyStore, keyStorePassword,
				DEFAULT_KEYSTORE_TYPE), KeyStoreConfiguration.UNCONFIGURED);
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
	public SslConfiguration create(Resource keyStore, @Nullable String keyStorePassword,
			Resource trustStore, @Nullable String trustStorePassword) {
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
	public SslConfiguration create(Resource keyStore, @Nullable char[] keyStorePassword,
			Resource trustStore, @Nullable char[] trustStorePassword) {

		Assert.notNull(keyStore, "KeyStore must not be null");
		Assert.isTrue(keyStore.exists(),
				() -> String.format("KeyStore %s does not exist", trustStore));

		Assert.notNull(trustStore, "TrustStore must not be null");
		Assert.isTrue(trustStore.exists(),
				String.format("TrustStore %s does not exist", trustStore));

		return new SslConfiguration(new KeyStoreConfiguration(keyStore, keyStorePassword,
				DEFAULT_KEYSTORE_TYPE), new KeyStoreConfiguration(trustStore,
				trustStorePassword, DEFAULT_KEYSTORE_TYPE));
	}

	/**
	 * Factory method returning an unconfigured {@link SslConfiguration} instance.
	 *
	 * @return an unconfigured {@link SslConfiguration} instance.
	 * @since 2.0
	 */
	public static SslConfiguration unconfigured() {
		return new SslConfiguration(KeyStoreConfiguration.unconfigured(),
				KeyStoreConfiguration.unconfigured());
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
	@Nullable
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
	 * Create a new {@link SslConfiguration} with {@link KeyStoreConfiguration} applied
	 * retaining the {@link #getTrustStoreConfiguration() trust store} configuration.
	 *
	 * @param configuration must not be {@literal null}.
	 * @return a new {@link SslConfiguration} with {@link KeyStoreConfiguration} applied.
	 * @since 2.0
	 */
	public SslConfiguration withKeyStore(KeyStoreConfiguration configuration) {
		return new SslConfiguration(configuration, this.trustStoreConfiguration);
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
	@Nullable
	public String getTrustStorePassword() {
		return stringOrNull(trustStoreConfiguration.getStorePassword());
	}

	/**
	 * @return the trust store configuration.
	 * @since 1.1
	 */
	public KeyStoreConfiguration getTrustStoreConfiguration() {
		return trustStoreConfiguration;
	}

	/**
	 * Create a new {@link SslConfiguration} with {@link KeyStoreConfiguration trust store
	 * configuration} applied retaining the {@link #getKeyStoreConfiguration()} key store}
	 * configuration.
	 *
	 * @param configuration must not be {@literal null}.
	 * @return a new {@link SslConfiguration} with {@link KeyStoreConfiguration trust
	 * store configuration} applied.
	 * @since 2.0
	 */
	public SslConfiguration withTrustStore(KeyStoreConfiguration configuration) {
		return new SslConfiguration(this.keyStoreConfiguration, configuration);
	}

	@Nullable
	private static String stringOrNull(@Nullable char[] storePassword) {
		return storePassword != null ? new String(storePassword) : null;
	}

	@Nullable
	private static char[] charsOrNull(@Nullable String trustStorePassword) {
		return trustStorePassword != null ? trustStorePassword.toCharArray() : null;
	}

	/**
	 * Configuration for a key store/trust store.
	 *
	 * @since 1.1
	 */
	public static class KeyStoreConfiguration {

		private static final KeyStoreConfiguration UNCONFIGURED = new KeyStoreConfiguration(
				AbsentResource.INSTANCE, null, DEFAULT_KEYSTORE_TYPE);

		/**
		 * Store that holds certificates, private keys.
		 */
		private final Resource resource;

		/**
		 * Password used to access the key store/trust store.
		 */
		@Nullable
		private final char[] storePassword;

		/**
		 * Key store/trust store type.
		 */
		private final String storeType;

		/**
		 * Create a new {@link KeyStoreConfiguration}.
		 */
		public KeyStoreConfiguration(Resource resource, @Nullable char[] storePassword,
				String storeType) {

			Assert.notNull(resource, "Resource must not be null");
			Assert.isTrue(resource instanceof AbsentResource || resource.exists(),
					() -> String.format("Resource %s does not exist", resource));
			Assert.notNull(storeType, "Keystore type must not be null");

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
		 * Create a new {@link KeyStoreConfiguration} given {@link Resource}.
		 *
		 * @param resource resource referencing the key store, must not be {@literal null}
		 * .
		 * @return the {@link KeyStoreConfiguration} for {@code resource}.
		 * @since 2.0
		 */
		public static KeyStoreConfiguration of(Resource resource) {
			return new KeyStoreConfiguration(resource, null, DEFAULT_KEYSTORE_TYPE);
		}

		/**
		 * Create a new {@link KeyStoreConfiguration} given {@link Resource} and
		 * {@code storePassword} using the default keystore type.
		 *
		 * @param resource resource referencing the key store, must not be {@literal null}
		 * .
		 * @param storePassword key store password, must not be {@literal null}.
		 * @return the {@link KeyStoreConfiguration} for {@code resource}.
		 * @since 2.0
		 */
		public static KeyStoreConfiguration of(Resource resource, char[] storePassword) {
			return new KeyStoreConfiguration(resource, storePassword,
					DEFAULT_KEYSTORE_TYPE);
		}

		/**
		 * Create an unconfigured, empty {@link KeyStoreConfiguration}.
		 *
		 * @return unconfigured, empty {@link KeyStoreConfiguration}.
		 * @since 2.0
		 */
		public static KeyStoreConfiguration unconfigured() {
			return UNCONFIGURED;
		}

		/**
		 * @return {@literal true} if the resource is present.
		 * @since 2.0
		 */
		public boolean isPresent() {
			return !(resource instanceof AbsentResource);
		}

		/**
		 * @return the {@link java.security.KeyStore key store} resource or
		 * {@literal null} if not configured.
		 */
		public Resource getResource() {
			return resource;
		}

		/**
		 * @return the key store/trust store password. Empty {@code char} array if not
		 * set.
		 */
		@Nullable
		public char[] getStorePassword() {
			return storePassword;
		}

		/**
		 * @return the trust store type.
		 */
		public String getStoreType() {
			return storeType;
		}
	}

	static class AbsentResource extends AbstractResource {

		static final AbsentResource INSTANCE = new AbsentResource();

		private AbsentResource() {
		}

		@Override
		public String getDescription() {
			return getClass().getSimpleName();
		}

		@Override
		public InputStream getInputStream() throws IOException {
			throw new UnsupportedOperationException("Empty resource");
		}
	}
}
