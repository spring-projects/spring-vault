/*
 * Copyright 2019-2022 the original author or authors.
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
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.List;
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

	private static final Pattern PRIVATE_KEY_PATTERN = Pattern
			.compile("-+BEGIN\\s+.*PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+" + // Header
					"([a-z0-9+/=\\r\\n]+)" + // Base64 text
					"-+END\\s+.*PRIVATE\\s+KEY[^-]*-+", // Footer
					Pattern.CASE_INSENSITIVE);

	private static final Pattern PUBLIC_KEY_PATTERN = Pattern
			.compile("-+BEGIN\\s+.*PUBLIC\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+" + // Header
					"([a-z0-9+/=\\r\\n]+)" + // Base64 text
					"-+END\\s+.*PUBLIC\\s+KEY[^-]*-+", // Footer
					Pattern.CASE_INSENSITIVE);

	private static final Pattern CERTIFICATE_PATTERN = Pattern
			.compile("-+BEGIN\\s+.*CERTIFICATE[^-]*-+(?:\\s|\\r|\\n)+" + // Header
					"([a-z0-9+/=\\r\\n]+)" + // Base64 text
					"-+END\\s+.*CERTIFICATE[^-]*-+", // Footer
					Pattern.CASE_INSENSITIVE);

	private static final Pattern[] PATTERNS = new Pattern[] { PRIVATE_KEY_PATTERN, PUBLIC_KEY_PATTERN,
			CERTIFICATE_PATTERN };

	private final byte[] content;

	private final Pattern matchingPattern;

	private PemObject(String content, Pattern matchingPattern) {
		this.matchingPattern = matchingPattern;

		String sanitized = content.replaceAll("\r", "").replaceAll("\n", "");
		this.content = Base64Utils.decodeFromString(sanitized);
	}

	/**
	 * Create a {@link PemObject} from PEM {@code content} that is enclosed with
	 * {@code -BEGIN PRIVATE KEY-} and {@code -END PRIVATE KEY-}. This method returns
	 * either the first PEM object ot throws {@link IllegalArgumentException} of no object
	 * could be found.
	 * @param content the PEM content.
	 * @return the {@link PemObject} from PEM {@code content}.
	 * @throws IllegalArgumentException if no PEM object could be found.
	 */
	public static PemObject fromKey(String content) {

		Matcher m = PRIVATE_KEY_PATTERN.matcher(content);
		if (!m.find()) {
			throw new IllegalArgumentException("Could not find a PKCS #8 private key");
		}

		return new PemObject(m.group(1), PRIVATE_KEY_PATTERN);
	}

	/**
	 * Create a {@link PemObject} from PEM {@code content} that is enclosed with
	 * {@code -BEGIN PRIVATE KEY-} or {@code -BEGIN PUBLIC KEY-}. This method returns
	 * either the first PEM object ot throws {@link IllegalArgumentException} of no object
	 * could be found.
	 * @param content the PEM content.
	 * @return the {@link PemObject} from PEM {@code content}.
	 * @throws IllegalArgumentException if no PEM object could be found.
	 * @since 2.3
	 */
	public static PemObject parseFirst(String content) {

		List<PemObject> objects = parse(content);

		if (objects.isEmpty()) {
			throw new IllegalArgumentException("Cannot find PEM object");
		}

		return objects.get(0);
	}

	/**
	 * Create one or more {@link PemObject}s from PEM {@code content}. Accepts
	 * concatenated PEM objects.
	 * @param content the PEM content.
	 * @return the list of {@link PemObject} from PEM {@code content}.
	 * @since 2.3
	 */
	public static List<PemObject> parse(String content) {

		List<PemObject> objects = new ArrayList<>();
		int index = 0;

		boolean found;

		do {
			found = false;

			Matcher discoveredMatcher = null;
			int indexDiscoveredIndex = 0;

			for (Pattern pattern : PATTERNS) {

				Matcher m = pattern.matcher(content);

				if (!m.find(index)) {
					continue;
				}

				// discover which pattern is the next applicable one.
				if (indexDiscoveredIndex == 0 || indexDiscoveredIndex > m.start()) {
					discoveredMatcher = m;
					indexDiscoveredIndex = m.start();
				}
			}

			// extract using the matching pattern.
			if (discoveredMatcher != null) {
				found = true;
				index = discoveredMatcher.end();
				objects.add(new PemObject(discoveredMatcher.group(1), discoveredMatcher.pattern()));
			}

		}
		while (found);

		return objects;
	}

	/**
	 * @return {@literal true} if the object was identified to contain a private key.
	 * @since 2.3
	 */
	public boolean isCertificate() {
		return this.matchingPattern.equals(CERTIFICATE_PATTERN);
	}

	/**
	 * @return {@literal true} if the object was identified to contain a private key.
	 * @since 2.3
	 */
	public boolean isPrivateKey() {
		return this.matchingPattern.equals(PRIVATE_KEY_PATTERN);
	}

	/**
	 * @return {@literal true} if the object was identified to contain a public key.
	 * @since 2.3
	 */
	public boolean isPublicKey() {
		return this.matchingPattern.equals(PUBLIC_KEY_PATTERN);
	}

	/**
	 * Retrieve a {@link RSAPrivateCrtKeySpec}.
	 * @return the {@link RSAPrivateCrtKeySpec}.
	 * @deprecated since 2.3. Use {@link #getRSAPrivateKeySpec()} instead that uses an
	 * improved name to indicate what the method is supposed to return.
	 */
	@Deprecated
	public RSAPrivateCrtKeySpec getRSAKeySpec() {
		return getRSAPrivateKeySpec();
	}

	/**
	 * Retrieve a {@link X509Certificate}.
	 * @return the {@link X509Certificate}.
	 * @since 2.3
	 */
	public X509Certificate getCertificate() {

		if (!isCertificate()) {
			throw new IllegalStateException("PEM object is not a certificate");
		}

		try {
			return KeystoreUtil.getCertificate(this.content);
		}
		catch (CertificateException e) {
			throw new IllegalStateException("Cannot obtain Certificate", e);
		}
	}

	/**
	 * Retrieve a {@link RSAPrivateCrtKeySpec}.
	 * @return the {@link RSAPrivateCrtKeySpec}.
	 * @since 2.3
	 */
	public RSAPrivateCrtKeySpec getRSAPrivateKeySpec() {

		if (!isPrivateKey()) {
			throw new IllegalStateException("PEM object is not a private key");
		}

		try {
			return KeystoreUtil.getRSAPrivateKeySpec(this.content);
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot obtain PrivateKey", e);
		}
	}

	/**
	 * Retrieve a {@link RSAPrivateCrtKeySpec}.
	 * @return the {@link RSAPrivateCrtKeySpec}.
	 */
	public RSAPublicKeySpec getRSAPublicKeySpec() {

		if (!isPublicKey()) {
			throw new IllegalStateException("PEM object is not a public key");
		}

		try {
			return KeystoreUtil.getRSAPublicKeySpec(this.content);
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot obtain PrivateKey", e);
		}
	}

}
