/*
 * Copyright 2016 the original author or authors.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.KeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Keystore utility to create a {@link KeyStore} containing a {@link CertificateBundle}
 * with the certificate chain and its private key.
 *
 * @author Mark Paluch
 */
class KeystoreUtil {

	/**
	 * Create a {@link KeyStore} containing the {@link KeySpec} and
	 * {@link X509Certificate certificates} using the given {@code keyAlias}.
	 *
	 * @param keyAlias
	 * @param certificates
	 * @return
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */
	static KeyStore createKeyStore(String keyAlias, KeySpec privateKeySpec,
			X509Certificate... certificates) throws GeneralSecurityException, IOException {

		KeyFactory kf = KeyFactory.getInstance("RSA");
		PrivateKey privateKey = kf.generatePrivate(privateKeySpec);

		KeyStore keyStore = createKeyStore();

		List<X509Certificate> certChain = new ArrayList<X509Certificate>();
		Collections.addAll(certChain, certificates);

		keyStore.setKeyEntry(keyAlias, privateKey, new char[0],
				certChain.toArray(new java.security.cert.Certificate[certChain.size()]));

		return keyStore;
	}

	static X509Certificate getCertificate(byte[] source) throws CertificateException,
			IOException {

		CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

		List<X509Certificate> certificates = getCertificates(certificateFactory, source);

		if (certificates.isEmpty()) {
			return null;
		}

		return certificates.get(0);
	}

	/**
	 * Create an empty {@link KeyStore}.
	 *
	 * @return
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */
	private static KeyStore createKeyStore() throws GeneralSecurityException, IOException {

		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, new char[0]);

		return keyStore;
	}

	private static List<X509Certificate> getCertificates(CertificateFactory cf,
			byte[] source) throws CertificateException, IOException {

		List<X509Certificate> x509Certificates = new ArrayList<X509Certificate>();

		ByteArrayInputStream bis = new ByteArrayInputStream(source);
		while (bis.available() > 0) {
			java.security.cert.Certificate cert = cf.generateCertificate(bis);

			if (cert instanceof X509Certificate) {
				x509Certificates.add((X509Certificate) cert);
			}
		}

		return x509Certificates;
	}

	/**
	 * Convert PKCS#1 encoded private key into RSAPrivateCrtKeySpec.
	 * <p/>
	 * <p/>
	 * The ASN.1 syntax for the private key with CRT is
	 * <p/>
	 *
	 * <pre>
	 * --
	 * -- Representation of RSA private key with information for the CRT algorithm.
	 * --
	 * RSAPrivateKey ::= SEQUENCE {
	 *   version           Version,
	 *   modulus           INTEGER,  -- n
	 *   publicExponent    INTEGER,  -- e
	 *   privateExponent   INTEGER,  -- d
	 *   prime1            INTEGER,  -- p
	 *   prime2            INTEGER,  -- q
	 *   exponent1         INTEGER,  -- d mod (p-1)
	 *   exponent2         INTEGER,  -- d mod (q-1)
	 *   coefficient       INTEGER,  -- (inverse of q) mod p
	 *   otherPrimeInfos   OtherPrimeInfos OPTIONAL
	 * }
	 * </pre>
	 *
	 * @param keyBytes PKCS#1 encoded key
	 * @return KeySpec
	 * @throws IOException
	 */
	static RSAPrivateCrtKeySpec getRSAKeySpec(byte[] keyBytes) throws IOException {
		DerParser parser = new DerParser(keyBytes);

		Asn1Object sequence = parser.read();
		if (sequence.getType() != DerParser.SEQUENCE) {
			throw new IllegalStateException("Invalid DER: not a sequence");
		}

		// Parse inside the sequence
		parser = sequence.getParser();

		parser.read(); // Skip version
		BigInteger modulus = parser.read().getInteger();
		BigInteger publicExp = parser.read().getInteger();
		BigInteger privateExp = parser.read().getInteger();
		BigInteger prime1 = parser.read().getInteger();
		BigInteger prime2 = parser.read().getInteger();
		BigInteger exp1 = parser.read().getInteger();
		BigInteger exp2 = parser.read().getInteger();
		BigInteger crtCoef = parser.read().getInteger();

		return new RSAPrivateCrtKeySpec(modulus, publicExp, privateExp, prime1, prime2,
				exp1, exp2, crtCoef);
	}

	/**
	 * A bare-minimum ASN.1 DER decoder, just having enough functions to decode PKCS#1
	 * private keys. Especially, it doesn't handle explicitly tagged types with an outer
	 * tag.
	 * <p/>
	 * <p/>
	 * This parser can only handle one layer. To parse nested constructs, get a new parser
	 * for each layer using <code>Asn1Object.getParser()</code>.
	 * <p/>
	 * <p/>
	 * There are many DER decoders in JRE but using them will tie this program to a
	 * specific JCE/JVM.
	 */
	private static class DerParser {

		// Classes
		public static final int UNIVERSAL = 0x00;
		public static final int APPLICATION = 0x40;
		public static final int CONTEXT = 0x80;
		public static final int PRIVATE = 0xC0;

		// Constructed Flag
		public static final int CONSTRUCTED = 0x20;

		// Tag and data types
		public static final int ANY = 0x00;
		public static final int BOOLEAN = 0x01;
		public static final int INTEGER = 0x02;
		public static final int BIT_STRING = 0x03;
		public static final int OCTET_STRING = 0x04;
		public static final int NULL = 0x05;
		public static final int REAL = 0x09;
		public static final int ENUMERATED = 0x0a;

		public static final int SEQUENCE = 0x10;
		public static final int SET = 0x11;

		public static final int NUMERIC_STRING = 0x12;
		public static final int PRINTABLE_STRING = 0x13;
		public static final int VIDEOTEX_STRING = 0x15;
		public static final int IA5_STRING = 0x16;
		public static final int GRAPHIC_STRING = 0x19;
		public static final int ISO646_STRING = 0x1A;
		public static final int GENERAL_STRING = 0x1B;

		public static final int UTF8_STRING = 0x0C;
		public static final int UNIVERSAL_STRING = 0x1C;
		public static final int BMP_STRING = 0x1E;

		public static final int UTC_TIME = 0x17;

		protected InputStream in;

		/**
		 * Create a new DER decoder from an input stream.
		 *
		 * @param in The DER encoded stream
		 */
		public DerParser(InputStream in) {
			this.in = in;
		}

		/**
		 * Create a new DER decoder from a byte array.
		 *
		 * @param bytes The encoded bytes
		 */
		public DerParser(byte[] bytes) {
			this(new ByteArrayInputStream(bytes));
		}

		/**
		 * Read next object. If it's constructed, the value holds encoded content and it
		 * should be parsed by a new parser from <code>Asn1Object.getParser</code>.
		 *
		 * @return A object
		 * @throws IOException
		 */
		public Asn1Object read() throws IOException {

			int tag = in.read();

			if (tag == -1) {
				throw new IllegalStateException(
						"Invalid DER: stream too short, missing tag");
			}

			int length = getLength();

			byte[] value = new byte[length];
			int n = in.read(value);
			if (n < length) {
				throw new IllegalStateException(
						"Invalid DER: stream too short, missing value");
			}

			return new Asn1Object(tag, length, value);
		}

		/**
		 * Decode the length of the field. Can only support length encoding up to 4
		 * octets.
		 * <p/>
		 * <p/>
		 * In BER/DER encoding, length can be encoded in 2 forms,
		 * <ul>
		 * <li>Short form. One octet. Bit 8 has value "0" and bits 7-1 give the length.
		 * <li>Long form. Two to 127 octets (only 4 is supported here). Bit 8 of first
		 * octet has value "1" and bits 7-1 give the number of additional length octets.
		 * Second and following octets give the length, base 256, most significant digit
		 * first.
		 * </ul>
		 *
		 * @return The length as integer
		 * @throws IOException
		 */
		private int getLength() throws IOException {

			int i = in.read();
			if (i == -1) {
				throw new IllegalStateException("Invalid DER: length missing");
			}

			// A single byte short length
			if ((i & ~0x7F) == 0) {
				return i;
			}

			int num = i & 0x7F;

			// We can't handle length longer than 4 bytes
			if (i >= 0xFF || num > 4) {
				throw new IllegalStateException("Invalid DER: length field too big (" + i
						+ ")");
			}

			byte[] bytes = new byte[num];
			int n = in.read(bytes);
			if (n < num) {
				throw new IllegalStateException("Invalid DER: length too short");
			}

			return new BigInteger(1, bytes).intValue();
		}
	}

	/**
	 * An ASN.1 TLV. The object is not parsed. It can only handle integers and strings.
	 */
	private static class Asn1Object {

		private final int type;
		private final int length;
		private final byte[] value;
		private final int tag;

		/**
		 * Construct a ASN.1 TLV. The TLV could be either a constructed or primitive
		 * entity.
		 * <p/>
		 * <p/>
		 * The first byte in DER encoding is made of following fields,
		 *
		 * <pre>
		 * -------------------------------------------------
		 * |Bit 8|Bit 7|Bit 6|Bit 5|Bit 4|Bit 3|Bit 2|Bit 1|
		 * -------------------------------------------------
		 * |  Class    | CF  |     +      Type             |
		 * -------------------------------------------------
		 * </pre>
		 *
		 * <ul>
		 * <li>Class: Universal, Application, Context or Private
		 * <li>CF: Constructed flag. If 1, the field is constructed.
		 * <li>Type: This is actually called tag in ASN.1. It indicates data type
		 * (Integer, String) or a construct (sequence, choice, set).
		 * </ul>
		 *
		 * @param tag Tag or Identifier
		 * @param length Length of the field
		 * @param value Encoded octet string for the field.
		 */
		public Asn1Object(int tag, int length, byte[] value) {
			this.tag = tag;
			this.type = tag & 0x1F;
			this.length = length;
			this.value = value;
		}

		public int getType() {
			return type;
		}

		public int getLength() {
			return length;
		}

		public byte[] getValue() {
			return value;
		}

		public boolean isConstructed() {
			return (tag & DerParser.CONSTRUCTED) == DerParser.CONSTRUCTED;
		}

		/**
		 * For constructed field, return a parser for its content.
		 *
		 * @return A parser for the construct.
		 * @throws IOException
		 */
		public DerParser getParser() throws IOException {
			if (!isConstructed()) {
				throw new IllegalStateException(
						"Invalid DER: can't parse primitive entity");
			}

			return new DerParser(value);
		}

		/**
		 * Get the value as integer
		 *
		 * @return BigInteger
		 */
		public BigInteger getInteger() {

			if (type != DerParser.INTEGER) {
				throw new IllegalStateException("Invalid DER: object is not integer");
			}

			return new BigInteger(value);
		}

		/**
		 * Get value as string. Most strings are treated as ISO-8859-1.
		 *
		 * @return Java string
		 * @throws IOException
		 */
		public String getString() throws IOException {

			String encoding;

			switch (type) {

			// Not all are ISO-8859-1 but it's the closest thing
			case DerParser.NUMERIC_STRING:
			case DerParser.PRINTABLE_STRING:
			case DerParser.VIDEOTEX_STRING:
			case DerParser.IA5_STRING:
			case DerParser.GRAPHIC_STRING:
			case DerParser.ISO646_STRING:
			case DerParser.GENERAL_STRING:
				encoding = "ISO-8859-1";
				break;

			case DerParser.BMP_STRING:
				encoding = "UTF-16BE";
				break;

			case DerParser.UTF8_STRING:
				encoding = "UTF-8";
				break;

			case DerParser.UNIVERSAL_STRING:
				throw new IOException("Invalid DER: can't handle UCS-4 string");

			default:
				throw new IOException("Invalid DER: object is not a string");
			}

			return new String(value, encoding);
		}
	}
}
