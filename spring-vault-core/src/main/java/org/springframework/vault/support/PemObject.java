/*
 * Copyright 2019-2020 the original author or authors.
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
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.Base64Utils;

/**
 * Represents a PEM object that is internally decoded to a DER object. Typically used to
 * obtain a {@link RSAPrivateCrtKeySpec}.
 * <p>
 * Mainly for use within the framework.
 *
 * @author Mark Paluch
 * @since 2.2
 */
public class PemObject {

	private static final Pattern KEY_PATTERN = Pattern
			.compile("-+BEGIN\\s+.*PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+" + // Header
					"([a-z0-9+/=\\r\\n]+)" + // Base64 text
					"-+END\\s+.*PRIVATE\\s+KEY[^-]*-+", // Footer
					Pattern.CASE_INSENSITIVE);

	private final byte[] content;

	private PemObject(String content) {

		String sanitized = content.replaceAll("\r", "").replaceAll("\n", "");
		this.content = Base64Utils.decodeFromString(sanitized);
	}

	/**
	 * Create a{@link PemObject} from PEM {@code content} that is enclosed with
	 * {@code -BEGIN PRIVATE KEY-} and {@code -END PRIVATE KEY-}.
	 *
	 * @param content the PEM content.
	 * @return the {@link PemObject} from PEM {@code content}.
	 */
	public static PemObject fromKey(String content) {

		Matcher m = KEY_PATTERN.matcher(content);
		if (!m.find()) {
			throw new IllegalArgumentException("Could not find a PKCS #8 private key");
		}

		return new PemObject(m.group(1));
	}

	/**
	 * Retrieve a {@link RSAPrivateCrtKeySpec}.
	 *
	 * @return the {@link RSAPrivateCrtKeySpec}.
	 */
	public RSAPrivateCrtKeySpec getRSAKeySpec() {

		try {
			return KeystoreUtil.getRSAKeySpec(this.content);
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Cannot obtain PrivateKey", e);
		}
	}
}
