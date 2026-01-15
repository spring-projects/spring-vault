/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.vault.core.certificate;

import org.springframework.vault.support.Certificate;
import org.springframework.vault.support.CertificateBundle;
import org.springframework.vault.support.VaultCertificateRequest;

/**
 * Interface representing a Certificate Authority to issue certificates.
 *
 * @author Mark Paluch
 * @since 4.1
 */
public interface CertificateAuthority {

	/**
	 * Issue (or re-issue) a certificate for the given {@code certificateName} using
	 * the role name and {@link VaultCertificateRequest}.
	 * @param certificateName name of the certificate bundle to identify the
	 * certificate. Useful for caching purposes.
	 * @param role Vault role name to use for issuing the certificate.
	 * @param request the {@link VaultCertificateRequest}.
	 * @return the issued (or re-issued) {@link CertificateBundle}.
	 */
	CertificateBundle issueCertificate(String certificateName, String role, VaultCertificateRequest request);

	/**
	 * Retrieve the issuer certificate for the given {@code certificateName} and
	 * {@code issuer}.
	 * @param certificateName name of the certificate. Useful for caching purposes.
	 * @param issuer issuer name.
	 * @return the issuer {@link Certificate}.
	 */
	Certificate getIssuerCertificate(String certificateName, String issuer);

}
