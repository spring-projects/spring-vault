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
package org.springframework.vault.support;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.vault.VaultException;

/**
 * Value object representing a certificate bundle consisting of a private key, the
 * certificate and the issuer certificate. Certificate and keys can be either DER or PEM
 * encoded. RSA and Elliptic Curve keys and certificates can be converted to a
 * {@link KeySpec} respective {@link X509Certificate} object. Supports creation of
 * {@link #createKeyStore(String) key stores} that contain the key and the certificate
 * chain.
 *
 * @author Mark Paluch
 * @author Alex Bremora
 * @author Bogdan Cardos
 * @see #getPrivateKeySpec()
 * @see #getX509Certificate()
 * @see #getIssuingCaCertificate()
 * @see PemObject
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CertificateBundle extends Certificate {

	private final String privateKey;

	@Nullable
	private final String privateKeyType;

	/**
	 * Create a new {@link CertificateBundle}.
	 * @param serialNumber the serial number.
	 * @param certificate the certificate.
	 * @param issuingCaCertificate the issuing CA certificate.
	 * @param caChain the CA chain.
	 * @param privateKey the private key.
	 * @param privateKeyType the private key type.
	 */
	CertificateBundle(@JsonProperty("serial_number") String serialNumber,
			@JsonProperty("certificate") String certificate, @JsonProperty("issuing_ca") String issuingCaCertificate,
			@JsonProperty("ca_chain") List<String> caChain, @JsonProperty("private_key") String privateKey,
			@Nullable @JsonProperty("private_key_type") String privateKeyType,
			@Nullable @JsonProperty("revocation_time") Long revocationTime) {

		super(serialNumber, certificate, issuingCaCertificate, caChain, revocationTime);
		this.privateKey = privateKey;
		this.privateKeyType = privateKeyType;
	}

	/**
	 * Create a {@link CertificateBundle} given a private key with certificates and the
	 * serial number.
	 * @param serialNumber must not be empty or {@literal null}.
	 * @param certificate must not be empty or {@literal null}.
	 * @param issuingCaCertificate must not be empty or {@literal null}.
	 * @param privateKey must not be empty or {@literal null}.
	 * @return the {@link CertificateBundle} instead.
	 */
	public static CertificateBundle of(String serialNumber, String certificate, String issuingCaCertificate,
			String privateKey) {

		Assert.hasText(serialNumber, "Serial number must not be empty");
		Assert.hasText(certificate, "Certificate must not be empty");
		Assert.hasText(issuingCaCertificate, "Issuing CA certificate must not be empty");
		Assert.hasText(privateKey, "Private key must not be empty");

		return new CertificateBundle(serialNumber, certificate, issuingCaCertificate,
				Collections.singletonList(issuingCaCertificate), privateKey, privateKey, null);
	}

	/**
	 * Create a {@link CertificateBundle} given a private key with certificates and the
	 * serial number.
	 * @param serialNumber must not be empty or {@literal null}.
	 * @param certificate must not be empty or {@literal null}.
	 * @param issuingCaCertificate must not be empty or {@literal null}.
	 * @param privateKey must not be empty or {@literal null}.
	 * @param privateKeyType must not be empty or {@literal null}.
	 * @return the {@link CertificateBundle}
	 * @since 2.4
	 */
	public static CertificateBundle of(String serialNumber, String certificate, String issuingCaCertificate,
			String privateKey, @Nullable String privateKeyType) {

		Assert.hasText(serialNumber, "Serial number must not be empty");
		Assert.hasText(certificate, "Certificate must not be empty");
		Assert.hasText(issuingCaCertificate, "Issuing CA certificate must not be empty");
		Assert.hasText(privateKey, "Private key must not be empty");
		Assert.hasText(privateKeyType, "Private key type must not be empty");

		return new CertificateBundle(serialNumber, certificate, issuingCaCertificate,
				Collections.singletonList(issuingCaCertificate), privateKey, privateKeyType, null);
	}

	/**
	 * Create a {@link CertificateBundle} given a private key with certificates and the
	 * serial number.
	 * @param serialNumber must not be empty or {@literal null}.
	 * @param certificate must not be empty or {@literal null}.
	 * @param issuingCaCertificate must not be empty or {@literal null}.
	 * @param privateKey must not be empty or {@literal null}.
	 * @param privateKeyType must not be empty or {@literal null}.
	 * @param revocationTime the revocation time.
	 * @return the {@link CertificateBundle}
	 * @since 2.4
	 */
	public static CertificateBundle of(String serialNumber, String certificate, String issuingCaCertificate,
			String privateKey, @Nullable String privateKeyType, Long revocationTime) {

		Assert.hasText(serialNumber, "Serial number must not be empty");
		Assert.hasText(certificate, "Certificate must not be empty");
		Assert.hasText(issuingCaCertificate, "Issuing CA certificate must not be empty");
		Assert.hasText(privateKey, "Private key must not be empty");
		Assert.hasText(privateKeyType, "Private key type must not be empty");
		Assert.notNull(revocationTime, "Revocation time must not be null");

		return new CertificateBundle(serialNumber, certificate, issuingCaCertificate,
				Collections.singletonList(issuingCaCertificate), privateKey, privateKeyType, revocationTime);
	}

	/**
	 * @return the private key (decrypted form, PEM or DER-encoded)
	 */
	public String getPrivateKey() {
		return this.privateKey;
	}

	/**
	 * @return the private key type, can be {@literal null}.
	 * @since 2.4
	 */
	@Nullable
	public String getPrivateKeyType() {
		return this.privateKeyType;
	}

	/**
	 * @return the required private key type, can be {@literal null}.
	 * @since 2.4
	 * @throws IllegalStateException if the private key type is {@literal null}
	 */
	public String getRequiredPrivateKeyType() {

		String type = getPrivateKeyType();

		if (type == null) {
			throw new IllegalStateException("Private key type is not set");
		}

		return type;
	}

	/**
	 * Retrieve the private key as {@link KeySpec}.
	 * @return the private {@link KeySpec}. {@link java.security.KeyFactory} can generate
	 * a {@link java.security.PrivateKey} from this {@link KeySpec}.
	 */
	public KeySpec getPrivateKeySpec() {

		try {
			return getPrivateKey(getPrivateKey(), getRequiredPrivateKeyType());
		}
		catch (IOException | GeneralSecurityException e) {
			throw new VaultException("Cannot create KeySpec from private key", e);
		}
	}

	/**
	 * Create a {@link KeyStore} from this {@link CertificateBundle} containing the
	 * private key and certificate chain.
	 * @param keyAlias the key alias to use.
	 * @return the {@link KeyStore} containing the private key and certificate chain.
	 */
	public KeyStore createKeyStore(String keyAlias) {
		return createKeyStore(keyAlias, false);
	}

	/**
	 * Create a {@link KeyStore} from this {@link CertificateBundle} containing the
	 * private key and certificate chain.
	 * @param keyAlias the key alias to use.
	 * @param password the password to use.
	 * @return the {@link KeyStore} containing the private key and certificate chain.
	 * @since 2.4
	 */
	public KeyStore createKeyStore(String keyAlias, CharSequence password) {
		return createKeyStore(keyAlias, false, password);
	}

	/**
	 * Create a {@link KeyStore} from this {@link CertificateBundle} containing the
	 * private key and certificate chain.
	 * @param keyAlias the key alias to use.
	 * @param password the password to use.
	 * @return the {@link KeyStore} containing the private key and certificate chain.
	 * @since 2.4
	 */
	public KeyStore createKeyStore(String keyAlias, char[] password) {
		return createKeyStore(keyAlias, false, password);
	}

	/**
	 * Create a {@link KeyStore} from this {@link CertificateBundle} containing the
	 * private key and certificate chain.
	 * @param keyAlias the key alias to use.
	 * @param includeCaChain whether to include the certificate authority chain instead of
	 * just the issuer certificate.
	 * @return the {@link KeyStore} containing the private key and certificate chain.
	 * @since 2.3.3
	 */
	public KeyStore createKeyStore(String keyAlias, boolean includeCaChain) {
		return createKeyStore(keyAlias, includeCaChain, new char[0]);
	}

	/**
	 * Create a {@link KeyStore} from this {@link CertificateBundle} containing the
	 * private key and certificate chain.
	 * @param keyAlias the key alias to use.
	 * @param includeCaChain whether to include the certificate authority chain instead of
	 * just the issuer certificate.
	 * @param password the password to use.
	 * @return the {@link KeyStore} containing the private key and certificate chain.
	 * @since 2.4
	 */
	public KeyStore createKeyStore(String keyAlias, boolean includeCaChain, CharSequence password) {

		Assert.notNull(password, "Password must not be null");

		char[] passwordChars = new char[password.length()];
		for (int i = 0; i < passwordChars.length; i++) {
			passwordChars[i] = password.charAt(i);
		}

		return createKeyStore(keyAlias, includeCaChain, passwordChars);
	}

	/**
	 * Create a {@link KeyStore} from this {@link CertificateBundle} containing the
	 * private key and certificate chain.
	 * @param keyAlias the key alias to use.
	 * @param includeCaChain whether to include the certificate authority chain instead of
	 * just the issuer certificate.
	 * @param password the password to use.
	 * @return the {@link KeyStore} containing the private key and certificate chain.
	 * @since 2.4
	 */
	public KeyStore createKeyStore(String keyAlias, boolean includeCaChain, char[] password) {

		Assert.hasText(keyAlias, "Key alias must not be empty");
		Assert.notNull(password, "Password must not be null");

		try {

			List<X509Certificate> certificates = new ArrayList<>();
			certificates.add(getX509Certificate());

			if (includeCaChain) {
				certificates.addAll(getX509IssuerCertificates());
			}
			else {
				certificates.add(getX509IssuerCertificate());
			}

			return KeystoreUtil.createKeyStore(keyAlias, getPrivateKeySpec(), password,
					certificates.toArray(new X509Certificate[0]));
		}
		catch (GeneralSecurityException | IOException e) {
			throw new VaultException("Cannot create KeyStore", e);
		}
	}

	private static KeySpec getPrivateKey(String privateKey, String keyType)
			throws GeneralSecurityException, IOException {

		Assert.hasText(privateKey, "Private key must not be empty");
		Assert.hasText(keyType, "Private key type must not be empty");

		if (PemObject.isPemEncoded(privateKey)) {

			List<PemObject> pemObjects = PemObject.parse(privateKey);

			for (PemObject pemObject : pemObjects) {

				if (pemObject.isPrivateKey()) {
					return getPrivateKey(pemObject.getContent(), keyType);
				}
			}

			throw new IllegalArgumentException("No private key found in PEM-encoded key spec");
		}

		return getPrivateKey(Base64.getDecoder().decode(privateKey), keyType);
	}

	private static KeySpec getPrivateKey(byte[] privateKey, String keyType)
			throws GeneralSecurityException, IOException {

		return switch (keyType.toLowerCase(Locale.ROOT)) {
			case "rsa" -> KeyFactories.RSA_PRIVATE.getKey(privateKey);
			case "ec" -> KeyFactories.EC.getKey(privateKey);
			default -> throw new IllegalArgumentException(
					"Key type %s not supported. Supported types are: rsa, ec.".formatted(keyType));
		};

	}

}
