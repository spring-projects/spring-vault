/*
 * Copyright 2017-2019 the original author or authors.
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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.util.Assert;
import org.springframework.util.Base64Utils;
import org.springframework.vault.VaultException;

/**
 * Value object representing a certificate consisting of the certificate and the issuer
 * certificate. Certificate and keys can be either DER or PEM encoded. DER-encoded
 * certificates can be converted to a {@link X509Certificate}.
 *
 * @author Mark Paluch
 * @since 2.0
 * @see #getX509Certificate()
 * @see #getIssuingCaCertificate()
 */
public class Certificate {

	private final String serialNumber;

	private final String certificate;

	private final String issuingCaCertificate;

	Certificate(@JsonProperty("serial_number") String serialNumber,
			@JsonProperty("certificate") String certificate,
			@JsonProperty("issuing_ca") String issuingCaCertificate) {

		this.serialNumber = serialNumber;
		this.certificate = certificate;
		this.issuingCaCertificate = issuingCaCertificate;
	}

	/**
	 * Create a {@link Certificate} given a private key with certificates and the serial
	 * number.
	 *
	 * @param serialNumber must not be empty or {@literal null}.
	 * @param certificate must not be empty or {@literal null}.
	 * @param issuingCaCertificate must not be empty or {@literal null}.
	 * @return the {@link Certificate}
	 */
	public static Certificate of(String serialNumber, String certificate,
			String issuingCaCertificate) {

		Assert.hasText(serialNumber, "Serial number must not be empty");
		Assert.hasText(certificate, "Certificate must not be empty");
		Assert.hasText(issuingCaCertificate, "Issuing CA certificate must not be empty");

		return new Certificate(serialNumber, certificate, issuingCaCertificate);
	}

	/**
	 * @return the serial number.
	 */
	public String getSerialNumber() {
		return this.serialNumber;
	}

	/**
	 * @return encoded certificate (PEM or DER-encoded).
	 */
	public String getCertificate() {
		return this.certificate;
	}

	/**
	 * @return encoded certificate of the issuing CA (PEM or DER-encoded).
	 */
	public String getIssuingCaCertificate() {
		return this.issuingCaCertificate;
	}

	/**
	 * Retrieve the certificate as {@link X509Certificate}. Only supported if certificate
	 * is DER-encoded.
	 *
	 * @return the {@link X509Certificate}.
	 */
	public X509Certificate getX509Certificate() {

		try {
			byte[] bytes = Base64Utils.decodeFromString(getCertificate());
			return KeystoreUtil.getCertificate(bytes);
		}
		catch (IOException | CertificateException e) {
			throw new VaultException("Cannot create Certificate from certificate", e);
		}
	}

	/**
	 * Retrieve the issuing CA certificate as {@link X509Certificate}. Only supported if
	 * certificate is DER-encoded.
	 *
	 * @return the issuing CA {@link X509Certificate}.
	 */
	public X509Certificate getX509IssuerCertificate() {

		try {
			byte[] bytes = Base64Utils.decodeFromString(getIssuingCaCertificate());
			return KeystoreUtil.getCertificate(bytes);
		}
		catch (IOException | CertificateException e) {
			throw new VaultException(
					"Cannot create Certificate from issuing CA certificate", e);
		}
	}

	/**
	 * Create a trust store as {@link KeyStore} from this {@link Certificate} containing
	 * the certificate chain. Only supported if certificate is DER-encoded.
	 *
	 * @return the {@link KeyStore} containing the private key and certificate chain.
	 */
	public KeyStore createTrustStore() {

		try {
			return KeystoreUtil.createKeyStore(getX509Certificate(),
					getX509IssuerCertificate());
		}
		catch (GeneralSecurityException | IOException e) {
			throw new VaultException("Cannot create KeyStore", e);
		}
	}
}
