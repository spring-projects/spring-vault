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

import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Base encoding validator.
 *
 * @author Alex Bremora
 * @since 2.4
 */
class EncodingValidator {

	public static final String PEM_PREFIX = "-----";

	public static final String PEM_PREFIX_BEGIN = PEM_PREFIX + "BEGIN";

	public static final String PEM_PREFIX_END = PEM_PREFIX + "END";

	public static final Pattern REGEX_PEM = Pattern.compile("^-{5}BEGIN.+-{5}(\\s|.)+-{5}END.+-{5}$");

	public static final Pattern REGEX_DER = Pattern.compile("^([0-9a-f]|\\s)+$");

	/**
	 * Check if content is of type {@literal DER}.
	 * @param content the content to check.
	 * @return {@literal true} if content is of type {@literal DER}.
	 */
	public static boolean isDer(String content) {
		try {
			byte[] decodedCertificate = Base64.getDecoder().decode(content);
			if (decodedCertificate == null || decodedCertificate.length == 0) {
				return false;
			}

			return true;
		}
		catch (Exception ex) {
			return false;
		}
	}

	/**
	 * Check if content is of type {@literal PEM}.
	 * @param content the content to check.
	 * @return {@literal true} if content is of type {@literal PEM}.
	 */
	public static boolean isPem(String content) {
		return content.startsWith(PEM_PREFIX_BEGIN);
	}

}
