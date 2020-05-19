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
package org.springframework.vault.core;

import java.io.InputStream;

import org.springframework.lang.Nullable;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.CertificateBundle;
import org.springframework.vault.support.VaultCertificateRequest;
import org.springframework.vault.support.VaultCertificateResponse;
import org.springframework.vault.support.VaultSignCertificateRequestResponse;

/**
 * Interface that specifies PKI backend-related operations.
 * <p>
 * The PKI secret backend for Vault generates X.509 certificates dynamically based on
 * configured roles. This means services can get certificates needed for both client and
 * server authentication without going through the usual manual process of generating a
 * private key and CSR, submitting to a CA, and waiting for a verification and signing
 * process to complete. Vault's built-in authentication and authorization mechanisms
 * provide the verification functionality.
 *
 * @author Mark Paluch
 * @see <a href=
 * "https://www.vaultproject.io/docs/secrets/pki/index.html">https://www.vaultproject.io/docs/secrets/pki/index.html</a>
 */
public interface VaultPkiOperations {

	/**
	 * Requests a certificate bundle (private key and certificate) from Vault's PKI
	 * backend given a {@code roleName} and {@link VaultCertificateRequest}. The issuing
	 * CA certificate is returned as well, so that only the root CA need be in a client's
	 * trust store. Certificates use DER format and are base64 encoded.
	 *
	 * @param roleName must not be empty or {@literal null}.
	 * @param certificateRequest must not be {@literal null}.
	 * @return the {@link VaultCertificateResponse} containing a {@link CertificateBundle}
	 * .
	 * @see <a href=
	 * "https://www.vaultproject.io/docs/secrets/pki/index.html#pki-issue">POST
	 * /pki/issue/[role name]</a>
	 */
	VaultCertificateResponse issueCertificate(String roleName,
			VaultCertificateRequest certificateRequest) throws VaultException;

	/**
	 * Signs a CSR using Vault's PKI backend given a {@code roleName}, {@code csr} and
	 * {@link VaultCertificateRequest}. The issuing CA certificate is returned as well, so
	 * that only the root CA need be in a client's trust store. Certificates use DER
	 * format and are base64 encoded.
	 *
	 * @param roleName must not be empty or {@literal null}.
	 * @param csr must not be empty or {@literal null}.
	 * @param certificateRequest must not be {@literal null}.
	 * @return the {@link VaultCertificateResponse} containing a
	 * {@link org.springframework.vault.support.Certificate} .
	 * @since 2.0
	 * @see <a href=
	 * "https://www.vaultproject.io/docs/secrets/pki/index.html#pki-issue">POST
	 * /pki/sign/[role name]</a>
	 */
	VaultSignCertificateRequestResponse signCertificateRequest(String roleName,
			String csr, VaultCertificateRequest certificateRequest) throws VaultException;

	/**
	 * Revokes a certificate using its serial number. This is an alternative option to the
	 * standard method of revoking using Vault lease IDs. A successful revocation will
	 * rotate the CRL
	 *
	 * @param serialNumber must not be empty or {@literal null}.
	 * @since 2.0
	 * @see <a href=
	 * "https://www.vaultproject.io/docs/secrets/pki/index.html#revoke-certificate">POST
	 * /pki/revoke</a>
	 */
	void revoke(String serialNumber) throws VaultException;

	/**
	 * Retrieves the current CRL in raw form. This endpoint is suitable for usage in the
	 * CRL distribution points extension in a CA certificate. This is a bare endpoint that
	 * does not return a standard Vault data structure. Returns data {@link Encoding#DER}
	 * or {@link Encoding#PEM} encoded.
	 * <p>
	 * If Vault reports no content under the CRL URL, then the result of this method call
	 * is {@literal null}.
	 *
	 * @return {@link java.io.InputStream} containing the encoded CRL or {@literal null}
	 * if Vault responds with 204 No Content.
	 * @since 2.0
	 * @see <a href="https://www.vaultproject.io/api/secret/pki/index.html#read-crl">GET
	 * /pki/crl</a>
	 */
	@Nullable
	InputStream getCrl(Encoding encoding) throws VaultException;

	enum Encoding {
		DER, PEM,
	}
}
