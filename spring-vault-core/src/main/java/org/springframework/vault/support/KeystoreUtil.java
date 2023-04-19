/*
 * Copyright 2016-2022 the original author or authors.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.util.Assert;

/**
 * Keystore utility to create a {@link KeyStore} containing a {@link CertificateBundle}
 * with the certificate chain and its private key.
 *
 * @author Mark Paluch
 * @author Bogdan Cardos
 */
class KeystoreUtil {

	private static final CertificateFactory CERTIFICATE_FACTORY;

	private static final KeyFactory RSA_KEY_FACTORY;

	private static final KeyFactory EC_KEY_FACTORY;

	static {

		try {
			CERTIFICATE_FACTORY = CertificateFactory.getInstance("X.509");
		}
		catch (CertificateException e) {
			throw new IllegalStateException("No X.509 Certificate available", e);
		}

		try {
			RSA_KEY_FACTORY = KeyFactory.getInstance("RSA");
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("No RSA KeyFactory available", e);
		}

		try {
			EC_KEY_FACTORY = KeyFactory.getInstance("EC");
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("No EC KeyFactory available", e);
		}
	}

	/**
	 * Create a {@link KeyStore} containing the {@link KeySpec} and {@link X509Certificate
	 * certificates} using the given {@code keyAlias} and {@code keyPassword}.
	 * @param keyAlias the key alias to use.
	 * @param privateKeySpec the private key to use.
	 * @param keyPassword the password to use.
	 * @param certificates the certificate chain to use.
	 * @return the {@link KeyStore} containing the private key and certificate chain.
	 * @throws GeneralSecurityException if exception occur when creating the instance of
	 * the {@link KeyStore}
	 * @throws IOException if there is an I/O or format problem with the keystore data, if
	 * a password is required but not given, or if the given password was incorrect. If
	 * the error is due to a wrong password, the {@link Throwable#getCause cause} of the
	 * {@code IOException} should be an {@code UnrecoverableKeyException}
	 */
	static KeyStore createKeyStore(String keyAlias, KeySpec privateKeySpec, char[] keyPassword,
			X509Certificate... certificates) throws GeneralSecurityException, IOException {

		Assert.notNull(keyPassword, "keyPassword must not be null");

		PrivateKey privateKey = (privateKeySpec instanceof RSAPrivateKeySpec
				|| privateKeySpec instanceof PKCS8EncodedKeySpec) ? RSA_KEY_FACTORY.generatePrivate(privateKeySpec)
						: EC_KEY_FACTORY.generatePrivate(privateKeySpec);

		KeyStore keyStore = createKeyStore();

		List<X509Certificate> certChain = new ArrayList<>();
		Collections.addAll(certChain, certificates);

		keyStore.setKeyEntry(keyAlias, privateKey, keyPassword,
				certChain.toArray(new java.security.cert.Certificate[certChain.size()]));

		return keyStore;
	}

	/**
	 * Create a {@link KeyStore} containing the {@link X509Certificate certificates}
	 * stored with as {@code cert_0, cert_1...cert_N}.
	 * @param certificates the certificate chain to use.
	 * @return the {@link KeyStore} containing the certificate chain.
	 * @throws GeneralSecurityException if exception occur when creating the instance of
	 * the {@link KeyStore}
	 * @throws IOException if there is an I/O or format problem with the keystore data, if
	 * a password is required but not given, or if the given password was incorrect. If
	 * the error is due to a wrong password, the {@link Throwable#getCause cause} of the
	 * {@code IOException} should be an {@code UnrecoverableKeyException}
	 * @since 2.0
	 */
	static KeyStore createKeyStore(X509Certificate... certificates) throws GeneralSecurityException, IOException {

		KeyStore keyStore = createKeyStore();

		int counter = 0;
		for (X509Certificate certificate : certificates) {
			keyStore.setCertificateEntry(String.format("cert_%d", counter++), certificate);
		}

		return keyStore;
	}

	static X509Certificate getCertificate(byte[] source) throws CertificateException {

		List<X509Certificate> certificates = getCertificates(CERTIFICATE_FACTORY, source);

		return certificates.stream()
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("No X509Certificate found"));
	}

	static List<X509Certificate> getCertificates(byte[] source) throws CertificateException {
		return getCertificates(CERTIFICATE_FACTORY, source);
	}

	/**
	 * Create an empty {@link KeyStore}.
	 * @return the {@link KeyStore}.
	 * @throws GeneralSecurityException if exception occur when creating the instance of
	 * the {@link KeyStore}
	 * @throws IOException if there is an I/O or format problem with the keystore data, if
	 * a password is required but not given, or if the given password was incorrect. If
	 * the error is due to a wrong password, the {@link Throwable#getCause cause} of the
	 * {@code IOException} should be an {@code UnrecoverableKeyException}
	 */
	private static KeyStore createKeyStore() throws GeneralSecurityException, IOException {

		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, new char[0]);

		return keyStore;
	}

	private static List<X509Certificate> getCertificates(CertificateFactory cf, byte[] source)
			throws CertificateException {

		List<X509Certificate> x509Certificates = new ArrayList<>();

		ByteArrayInputStream bis = new ByteArrayInputStream(source);
		while (bis.available() > 0) {
			java.security.cert.Certificate cert = cf.generateCertificate(bis);

			if (cert instanceof X509Certificate) {
				x509Certificates.add((X509Certificate) cert);
			}
		}

		return x509Certificates;
	}

}
