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

import org.springframework.util.Base64Utils;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * Builder for certificates.
 *
 * @author Alex Bremora
 * @since 2.4
 */
class CertificateBuilder {

  /**
   * Creates a {@link X509Certificate}. Supports the formats {@literal DER}, {@literal PEM} and {@literal PEM_BUNDLE}.
   *
   * @param certificate Base64 encoded certificate.
   * @return {@link X509Certificate}
   * @throws CertificateException
   */
	public static X509Certificate create(String certificate) throws CertificateException {
		CertificateEncoding format = CertificateEncodingValidator.getFormat(certificate);

		switch (format) {
		case DER:
			return createCertificateFromDer(certificate);
		case PEM:
		case PEM_BUNDLE:
			return createCertificateFromPemOrPemBundle(certificate);
		case UNKNOWN:
			throw new IllegalArgumentException("No X509Certificate found");
		default:
			throw new IllegalStateException("Unexpected value: " + format);
		}
	}

	private static X509Certificate createCertificateFromDer(String certificate) throws CertificateException {
		byte[] bytes = Base64Utils.decodeFromString(certificate);
		return KeystoreUtil.getCertificate(bytes);
	}

	private static X509Certificate createCertificateFromPemOrPemBundle(String certificateBundle)
			throws CertificateException {
    Optional<PemItem> pemItem = PemReader.parse(certificateBundle)
			.stream().filter(p -> p.isCertificate()).findFirst();
    if(!pemItem.isPresent()) {
      throw new CertificateException("No certificate found");
    }

    return KeystoreUtil.getCertificate(pemItem.get().getContent());
	}
}
