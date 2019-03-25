/*
 * Copyright 2016 the original author or authors.
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
	public static final SslConfiguration NONE = new SslConfiguration(null, null, null,
			null);

	/**
	 * Trust store that holds certificates and private keys.
	 */
	private final Resource keyStore;

	/**
	 * Password used to access the key store.
	 */
	private final String keyStorePassword;

	/**
	 * Trust store that holds SSL certificates.
	 */
	private final Resource trustStore;

	/**
	 * Password used to access the trust store.
	 */
	private final String trustStorePassword;

	/**
	 * Create a new {@link SslConfiguration}.
	 *
	 * @param keyStore the keystore resource.
	 * @param keyStorePassword the keystore password.
	 * @param trustStore the truststore resource.
	 * @param trustStorePassword the truststore password.
	 */
	public SslConfiguration(Resource keyStore, String keyStorePassword,
			Resource trustStore, String trustStorePassword) {

		this.keyStore = keyStore;
		this.keyStorePassword = keyStorePassword;
		this.trustStore = trustStore;
		this.trustStorePassword = trustStorePassword;
	}

	/**
	 * Create a new {@link SslConfiguration} for the given trust store.
	 *
	 * @param trustStore resource pointing to an existing trust store, must not be
	 * {@literal null}.
	 * @param trustStorePassword may be {@literal null}.
	 * @return the created {@link SslConfiguration}.
	 * @see java.security.KeyStore
	 */
	public static SslConfiguration forTrustStore(Resource trustStore,
			String trustStorePassword) {

		Assert.notNull(trustStore, "TrustStore must not be null");
		Assert.notNull(trustStore.exists(),
				String.format("TrustStore %s does not exist", trustStore));

		return new SslConfiguration(null, null, trustStore, trustStorePassword);
	}

	/**
	 * Create a new {@link SslConfiguration} for the given key store.
	 *
	 * @param keyStore resource pointing to an existing key store, must not be
	 * {@literal null}.
	 * @param keyStorePassword may be {@literal null}.
	 * @return the created {@link SslConfiguration}.
	 * @see java.security.KeyStore
	 */
	public static SslConfiguration forKeyStore(Resource keyStore, String keyStorePassword) {

		Assert.notNull(keyStore, "KeyStore must not be null");
		Assert.notNull(keyStore.exists(),
				String.format("KeyStore %s does not exist", keyStore));

		return new SslConfiguration(keyStore, keyStorePassword, null, null);
	}

	/**
	 * Create a new {@link SslConfiguration} for the given truststore.
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
	public SslConfiguration create(Resource keyStore, String keyStorePassword,
			Resource trustStore, String trustStorePassword) {

		Assert.notNull(keyStore, "KeyStore must not be null");
		Assert.notNull(keyStore.exists(),
				String.format("KeyStore %s does not exist", trustStore));

		Assert.notNull(trustStore, "TrustStore must not be null");
		Assert.notNull(trustStore.exists(),
				String.format("TrustStore %s does not exist", trustStore));

		return new SslConfiguration(keyStore, keyStorePassword, trustStore,
				trustStorePassword);
	}

	/**
	 * @return the {@link java.security.KeyStore key store} resource or {@literal null} if
	 * not configured.
	 */
	public Resource getKeyStore() {
		return keyStore;
	}

	/**
	 * @return the key store password or {@literal null} if not configured.
	 */
	public String getKeyStorePassword() {
		return keyStorePassword;
	}

	/**
	 * @return the {@link java.security.KeyStore key store} resource or {@literal null} if
	 * not configured.
	 */
	public Resource getTrustStore() {
		return trustStore;
	}

	/**
	 * @return the trust store password or {@literal null} if not configured.
	 */
	public String getTrustStorePassword() {
		return trustStorePassword;
	}
}
