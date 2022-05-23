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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.Base64Utils;

/**
 * Represents a PEM object that is internally decoded to a DER object. Typically, used to
 * obtain a {@link RSAPrivateCrtKeySpec}.
 * <p>
 * Mainly for use within the framework.
 *
 * @author Mark Paluch
 * @since 2.2
 */
public class PemObject {

	private static final Pattern BEGIN_PATTERN = Pattern.compile("-+BEGIN ([A-Z ]+)-+");

	private static final Pattern END_PATTERN = Pattern.compile("-+END ([A-Z ]+)-+");

	private final PemObjectType objectType;

	private final byte[] content;

	private PemObject(PemObjectType objectType, String content) {

		this.objectType = objectType;
		String sanitized = content.replaceAll("\r", "").replaceAll("\n", "");
		this.content = Base64Utils.decodeFromString(sanitized);
	}

	/**
	 * Check whether the content is PEM-encoded.
	 * @param content the content to inspect
	 * @return {@code true} if PEM-encoded.
	 */
	public static boolean isPemEncoded(String content) {
		return BEGIN_PATTERN.matcher(content).find() && END_PATTERN.matcher(content).find();
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

		return parse(content).stream().filter(PemObject::isPrivateKey).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Could not find a PKCS #8 private key"));
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

		try (BufferedReader reader = new BufferedReader(new StringReader(content))) {

			PemObject object;

			while ((object = readNextSection(reader)) != null) {
				objects.add(object);
			}
		}
		catch (IOException e) {
			throw new RuntimeException("No way this could happen with a StringReader underneath", e);
		}

		return objects;
	}

	/**
	 * Reads the next object from the PEM content.
	 * @return next object or {@code null} for end of file
	 */
	@Nullable
	static PemObject readNextSection(BufferedReader reader) throws IOException {
		String title = null;
		StringBuilder keyBuilder = null;
		while (true) {
			String line = reader.readLine();
			;
			if (line == null) {
				Assert.isTrue(title == null, "missing end tag " + title);
				return null;
			}
			if (keyBuilder == null) {
				Matcher m = BEGIN_PATTERN.matcher(line);
				if (m.matches()) {
					String curTitle = m.group(1);
					keyBuilder = new StringBuilder();
					title = curTitle;
				}
			}
			else {
				Matcher m = END_PATTERN.matcher(line);
				if (m.matches()) {
					String endTitle = m.group(1);

					if (!endTitle.equals(title)) {
						throw new IllegalArgumentException(
								String.format("end tag (%s) doesn't match begin tag (%s)", endTitle, title));
					}
					return new PemObject(PemObjectType.of(title), keyBuilder.toString());
				}
				keyBuilder.append(line);
			}
		}
	}

	/**
	 * @return {@literal true} if the object was identified to contain a private key.
	 * @since 2.3
	 */
	public boolean isCertificate() {
		return PemObjectType.CERTIFICATE == this.objectType || PemObjectType.X509_CERTIFICATE == this.objectType
				|| PemObjectType.TRUSTED_CERTIFICATE == this.objectType;
	}

	/**
	 * @return {@literal true} if the object was identified to contain a private key.
	 * @since 2.3
	 */
	public boolean isPrivateKey() {
		return PemObjectType.PRIVATE_KEY == this.objectType || PemObjectType.EC_PRIVATE_KEY == this.objectType
				|| PemObjectType.RSA_PRIVATE_KEY == this.objectType;
	}

	/**
	 * @return {@literal true} if the object was identified to contain a public key.
	 * @since 2.3
	 */
	public boolean isPublicKey() {
		return PemObjectType.PUBLIC_KEY == this.objectType || PemObjectType.RSA_PUBLIC_KEY == this.objectType;
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
	 * Retrieve one or more {@link X509Certificate}s.
	 * @return the {@link X509Certificate}s.
	 * @since 2.4
	 */
	public List<X509Certificate> getCertificates() {

		if (!isCertificate()) {
			throw new IllegalStateException("PEM object is not a certificate");
		}

		try {
			return Collections.unmodifiableList(KeystoreUtil.getCertificates(this.content));
		}
		catch (CertificateException e) {
			throw new IllegalStateException("Cannot obtain Certificates", e);
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
			return KeyFactories.RSA_PRIVATE.getKey(this.content);
		}
		catch (GeneralSecurityException | IOException e) {
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
			return KeyFactories.RSA_PUBLIC.getKey(this.content);
		}
		catch (GeneralSecurityException | IOException e) {
			throw new IllegalStateException("Cannot obtain PrivateKey", e);
		}
	}

	byte[] getContent() {
		return content;
	}

	enum PemObjectType {

		CERTIFICATE_REQUEST("CERTIFICATE REQUEST"), NEW_CERTIFICATE_REQUEST("NEW CERTIFICATE REQUEST"), CERTIFICATE(
				"CERTIFICATE"), TRUSTED_CERTIFICATE("TRUSTED CERTIFICATE"), X509_CERTIFICATE(
						"X509 CERTIFICATE"), X509_CRL("X509 CRL"), PKCS7("PKCS7"), CMS("CMS"), ATTRIBUTE_CERTIFICATE(
								"ATTRIBUTE CERTIFICATE"), EC_PARAMETERS(
										"EC PARAMETERS"), PUBLIC_KEY("PUBLIC KEY"), RSA_PUBLIC_KEY(
												"RSA PUBLIC KEY"), RSA_PRIVATE_KEY("RSA PRIVATE KEY"), EC_PRIVATE_KEY(
														"EC PRIVATE KEY"), ENCRYPTED_PRIVATE_KEY(
																"ENCRYPTED PRIVATE KEY"), PRIVATE_KEY("PRIVATE KEY");

		// cache
		private static final PemObjectType[] constants = values();

		private final String name;

		PemObjectType(String value) {
			this.name = value;
		}

		public String toString() {
			return name;
		}

		public static PemObjectType of(String identifier) {

			Assert.hasText(identifier, "Identifier must not be empty");

			for (PemObjectType constant : constants) {
				if (constant.name.equalsIgnoreCase(identifier)) {
					return constant;
				}
			}

			throw new IllegalArgumentException(String.format("No enum constant %s", identifier));
		}

	}

}
