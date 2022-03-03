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

import java.io.IOException;
import java.security.spec.KeySpec;
import org.springframework.util.Base64Utils;
import org.springframework.vault.support.PemItem.PemItemType;

/**
 * Private key builder. Reads {@literal DER} and {@literal PEM} encoded content
 * but {@literal pkcs8} is not supported.
 *
 * @author Alex Bremora
 * @since 2.4
 */
class PrivateKeyBuilder {

	/**
	 * Creates a {@link KeySpec}. Supports {@literal DER} and {@literal PEM} content.
	 * @param privateKey private key.
	 * @param privateKeyType private key type
	 * @return {@link KeySpec}
	 * @throws IOException
	 */
	public static KeySpec create(String privateKey, String privateKeyType) throws IOException {
		if (!PrivateKeyFactory.isTypeSupported(privateKeyType)) {
			throw new UnsupportedOperationException("Private key type not supported: " + privateKeyType);
		}

		PrivateKeyEncoding format = PrivateKeyEncodingValidator.getFormat(privateKey);

		switch (format) {
		case DER:
			return createKeySpecFromDer(privateKey, privateKeyType);
		case PEM:
			return createKeySpecFromPem(privateKey, privateKeyType);
		case UNKNOWN:
			throw new IllegalArgumentException("No X509Certificate found");
		default:
			throw new IllegalStateException("Unexpected value: " + format);
		}
	}

	// PEM-encoded DER
	private static KeySpec createKeySpecFromPem(String pemContent, String privateKeyType) throws IOException {
		PemItem pemItem = PemReader.parse(pemContent).stream().filter(i -> i.isPrivateKey()).findFirst().get();

		String format = getFormatForPrivateKey(pemItem.getItemType());
		if (!"pkcs1".equals(format)) {
			throw new UnsupportedOperationException("PKCS#1 supported only. Format: " + format);
		}

		byte[] privateKey = pemItem.getContent();

		return createKeySpecFromDer(privateKeyType, privateKey);
	}

	private static KeySpec createKeySpecFromDer(String privateKey, String privateKeyType) throws IOException {
		byte[] bytes = Base64Utils.decodeFromString(privateKey);
		return createKeySpecFromDer(privateKeyType, bytes);
	}

	private static KeySpec createKeySpecFromDer(String privateKeyType, byte[] privateKey) throws IOException {
    PrivateKeyStrategy privateKeyStrategy = PrivateKeyFactory.create(privateKeyType);
    if(privateKeyStrategy == null) {
      throw new UnsupportedOperationException("Private key type not supported: " + privateKeyType);
    }

    return privateKeyStrategy.getKeySpec(privateKey);
	}

	private static String getFormatForPrivateKey(PemItemType pemItemType) {
		switch (pemItemType) {
		case PKCS7:
			return "pkcs7";
		case RSA_PUBLIC_KEY:
		case RSA_PRIVATE_KEY:
		case EC_PRIVATE_KEY:
			return "pkcs1";
		case ENCRYPTED_PRIVATE_KEY:
		case PUBLIC_KEY:
		case PRIVATE_KEY:
			return "pkcs8";
		default:
			throw new IllegalStateException("Unexpected value: " + pemItemType);
		}
	}
}
