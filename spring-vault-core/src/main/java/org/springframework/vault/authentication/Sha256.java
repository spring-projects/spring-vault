/*
 * Copyright 2016-2025 the original author or authors.
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

package org.springframework.vault.authentication;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.util.Assert;

/**
 * Utility to generate a SHA 256 checksum.
 *
 * @author Mark Paluch
 */
class Sha256 {

	private static final Charset US_ASCII = StandardCharsets.US_ASCII;


	/**
	 * Generates a hex-encoded SHA256 checksum from the supplied {@code content}.
	 * @param content must not be {@literal null} and not empty.
	 * @return hex-encoded SHA256 checksum
	 */
	public static String toSha256(String content) {
		Assert.hasText(content, "Content must not be empty");
		MessageDigest messageDigest = getMessageDigest("SHA-256");
		byte[] digest = messageDigest.digest(content.getBytes(US_ASCII));
		return toHexString(digest);
	}

	/**
	 * Get a MessageDigest instance for the given algorithm. Throws an
	 * IllegalArgumentException if <i>algorithm</i> is unknown
	 * @return MessageDigest instance
	 * @throws IllegalArgumentException if NoSuchAlgorithmException is thrown
	 */
	private static MessageDigest getMessageDigest(String algorithm) throws IllegalArgumentException {
		try {
			return MessageDigest.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException("No such algorithm [" + algorithm + "]");
		}
	}

	static String toHexString(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append("%X".formatted(b));
		}
		return sb.toString();
	}

}
