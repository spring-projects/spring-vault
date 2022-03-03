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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Value object representing a certificate bundle consisting of a private key, the
 * certificate and the issuer certificate. Certificate and keys can be either DER or PEM
 * encoded. DER-encoded certificates can be converted to a {@link KeySpec} and
 * {@link X509Certificate}.
 *
 * @author Mark Paluch
 * @author Alex Bremora
 * @see #getPrivateKeySpec()
 * @see #getX509Certificate()
 * @see #getIssuingCaCertificate()
 */
public class CertificateBundle extends Certificate {

	private final String privateKey;

	@Nullable
	private final String privateKeyType;

	private final List<String> caChain;

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
			@Nullable @JsonProperty("private_key_type") String privateKeyType) {

		super(serialNumber, certificate, issuingCaCertificate);
		this.privateKey = privateKey;
		this.privateKeyType = privateKeyType;
		this.caChain = caChain;
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
				Collections.singletonList(issuingCaCertificate), privateKey, null);
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
				Collections.singletonList(issuingCaCertificate), privateKey, privateKeyType);
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
	 * Retrieve the private key as {@link KeySpec}.
	 * @return the private {@link KeySpec}. {@link java.security.KeyFactory} can generate
	 * a {@link java.security.PrivateKey} from this {@link KeySpec}.
	 */
	public KeySpec getPrivateKeySpec() {

		try {
			return PrivateKeyBuilder.create(getPrivateKey(), getPrivateKeyType());
		}
		catch (IOException e) {
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
	 * @param includeCaChain whether to include the certificate authority chain instead of
	 * just the issuer certificate.
	 * @return the {@link KeyStore} containing the private key and certificate chain.
	 * @since 2.3.3
	 */
	public KeyStore createKeyStore(String keyAlias, boolean includeCaChain) {

		Assert.hasText(keyAlias, "Key alias must not be empty");

		try {

			List<X509Certificate> certificates = new ArrayList<>();
			certificates.add(getX509Certificate());

			if (includeCaChain) {
				certificates.addAll(getX509IssuerCertificates());
			}
			else {
				certificates.add(getX509IssuerCertificate());
			}

			return KeystoreUtil.createKeyStore(keyAlias, getPrivateKeySpec(), getPrivateKeyType(),
					certificates.toArray(new X509Certificate[0]));
		}
		catch (GeneralSecurityException | IOException e) {
			throw new VaultException("Cannot create KeyStore", e);
		}
	}

	/**
	 * Retrieve the issuing CA certificates as list of {@link X509Certificate}.
	 * @return the issuing CA {@link X509Certificate}.
	 * @since 2.3.3
	 */
	public List<X509Certificate> getX509IssuerCertificates() {

		List<X509Certificate> certificates = new ArrayList<>();

		for (String data : caChain) {
			try {
				X509Certificate certificate = CertificateBuilder.create(data);
				certificates.add(certificate);
			}
			catch (CertificateException e) {
				throw new VaultException("Cannot create Certificate from issuing CA certificate", e);
			}
		}

		return certificates;
	}

}
