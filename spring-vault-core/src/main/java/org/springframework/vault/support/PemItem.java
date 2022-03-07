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

import java.util.regex.Pattern;
import org.springframework.util.Base64Utils;

/**
 * Represents an item with a {@literal PEM} structure.
 *
 * @author Alex Bremora
 * @since 2.4
 */
class PemItem {

	public static final String PEM_PREFIX = "-----";

	public static final String PEM_PREFIX_BEGIN = PEM_PREFIX + "BEGIN";

	public static final String PEM_PREFIX_END = PEM_PREFIX + "END";

	public static final String REGEX_PEM_START = "(?m)^-{5}BEGIN.*";

	public static final String REGEX_PEM_END = "(?m)^-{5}END.*";

	public static final Pattern REGEX_PEM = Pattern.compile("^-{5}BEGIN.+-{5}(\\s|.)+-{5}END.+-{5}$");

	private final byte[] content;

	private final PemItemType itemType;

	PemItem(String content, PemItemType itemType) {
		this.content = Base64Utils.decodeFromString(content);
		this.itemType = itemType;
	}

	public static PemItem parse(String pem) {
		pem = pem.trim();
		String content = strip(pem);
		PemItemType pemItemType = getItemType(pem);
		return new PemItem(content, pemItemType);
	}

	private static PemItemType getItemType(String pem) {
		String[] result = pem.trim().split("\r\n|\r|\n");
		String firstLine = result[0];

		for (PemItemType type : PemItemType.values()) {
			if (firstLine.contains(type.toString())) {
				return type;
			}
		}

		throw new IllegalStateException("No valid PemItemType found");
	}

	public byte[] getContent() {
		return content;
	}

	public PemItemType getItemType() {
		return itemType;
	}

	public boolean isPrivateKey() {
		return itemType == PemItemType.PRIVATE_KEY || itemType == PemItemType.EC_PRIVATE_KEY
				|| itemType == PemItemType.ENCRYPTED_PRIVATE_KEY || itemType == PemItemType.RSA_PRIVATE_KEY;
	}

	public boolean isCertificate() {
		return itemType == PemItemType.CERTIFICATE || itemType == PemItemType.X509_CERTIFICATE;
	}

	private static String strip(String content) {
		return content.replaceAll(REGEX_PEM_START, "").replaceAll(REGEX_PEM_END, "").replaceAll("\r", "")
				.replaceAll("\n", "");
	}

	public enum PemItemType {

		CERTIFICATE_REQUEST("CERTIFICATE REQUEST"),
		NEW_CERTIFICATE_REQUEST("NEW CERTIFICATE REQUEST"),
		CERTIFICATE("CERTIFICATE"),
		TRUSTED_CERTIFICATE("TRUSTED CERTIFICATE"),
		X509_CERTIFICATE("X509 CERTIFICATE"),
		X509_CRL("X509 CRL"),
		PKCS7("PKCS7"),
		CMS("CMS"),
		ATTRIBUTE_CERTIFICATE("ATTRIBUTE CERTIFICATE"),
		EC_PARAMETERS("EC PARAMETERS"),
		PUBLIC_KEY("PUBLIC KEY"),
		RSA_PUBLIC_KEY("RSA PUBLIC KEY"),
		RSA_PRIVATE_KEY("RSA PRIVATE KEY"),
		EC_PRIVATE_KEY("EC PRIVATE KEY"),
		ENCRYPTED_PRIVATE_KEY("ENCRYPTED PRIVATE KEY"),
		PRIVATE_KEY("PRIVATE KEY");

		private String name;

		PemItemType(String value) {
			name = value;
		}

		public String toString() {
			return name;
		}

		public static String getEnumByString(String code) {
			for (PemItemType e : PemItemType.values()) {
				if (e.name.equals(code))
					return e.name();
			}

			return null;
		}
	}
}
