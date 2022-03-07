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

/**
 * Simple validator which analysis the encoding of the certificate. Checks encoding based
 * on prefixes. Does not verify the content.
 *
 * @author Alex Bremora
 * @since 2.4
 */
class CertificateEncodingValidator extends EncodingValidator {

	public static CertificateEncoding getFormat(String certificate) {
		if (certificate == null || certificate.isEmpty()) {
			return CertificateEncoding.UNKNOWN;
		}

		String certificateWithoutWhitespaces = certificate.replace("\\s+", "");

		if (isPem(certificateWithoutWhitespaces)) {
			if (isPemBundled(certificateWithoutWhitespaces)) {
				return CertificateEncoding.PEM_BUNDLE;
			}

			return CertificateEncoding.PEM;
		}

		if (isDer(certificateWithoutWhitespaces)) {
			return CertificateEncoding.DER;
		}

		throw new IllegalStateException("Unknown format");
	}

	private static boolean isPemBundled(String certificate) {
		String[] content = certificate.split("^" + PEM_PREFIX_BEGIN);
		return content.length > 1;
	}

}
