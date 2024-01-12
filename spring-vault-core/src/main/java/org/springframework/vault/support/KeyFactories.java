/*
 * Copyright 2022-2024 the original author or authors.
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
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;

/**
 * Key factories to create {@link KeySpec} from its encoded representation.
 *
 * Supports plain- and PKCS#8-encoded keys.
 * <p/>
 * The ASN.1 syntax for the private key is
 * <p/>
 *
 * <pre>
 * --
 * -- Representation of PrivateKeyInfo.
 * --
 * PrivateKeyInfo ::= SEQUENCE {
 *   version               Version,
 *   privateKeyAlgorithm   PrivateKeyAlgorithmIdentifier,
 *   privateKey            OCTET STRING,
 *   attributes            [0] IMPLICIT Attributes OPTIONAL
 * }
 * </pre>
 *
 * @author Mark Paluch
 * @since 2.4
 */
class KeyFactories {

	static final String RSA = "1.2.840.113549.1.1.1";

	static final String EC_PUBLIC_KEY = "1.2.840.10045.2.1";

	static final EcPrivateKeyFactory EC = new EcPrivateKeyFactory();

	static final RsaPrivateKeyFactory RSA_PRIVATE = new RsaPrivateKeyFactory();

	static final RsaPublicKeyFactory RSA_PUBLIC = new RsaPublicKeyFactory();

	/**
	 * Interface defining a contract for key factories to create a {@link KeySpec} from a
	 * binary key representation using ASN.1 syntax.
	 */
	interface KeyFactory {

		/**
		 * Create the key spec from {@code keyBytes}.
		 * @param keyBytes
		 * @return
		 * @throws IOException
		 * @throws GeneralSecurityException
		 */
		KeySpec getKey(byte[] keyBytes) throws IOException, GeneralSecurityException;

	}

	/**
	 * Convert PKCS#1 encoded ec key into ECPrivateKeySpec.
	 * <p/>
	 * The ASN.1 syntax for the private key with CRT is
	 * <p/>
	 *
	 * <pre>
	 * --
	 * -- Representation of EC private key.
	 * --
	 * ECPrivateKey ::= SEQUENCE {
	 *   version           Version,
	 *   privateKey        OCTET STRING,
	 *   parameters        [0] ECParameters {{ NamedCurve }} OPTIONAL,
	 *   publicKey         [1] BIT STRING OPTIONAL
	 * }
	 * </pre>
	 */
	static class EcPrivateKeyFactory implements KeyFactory {

		@Override
		public ECPrivateKeySpec getKey(byte[] keyBytes) throws IOException, GeneralSecurityException {

			DerParser parser = new DerParser(keyBytes);

			DerParser.Asn1Object sequence = parser.read();
			if (sequence.getType() != DerParser.SEQUENCE) {
				throw new InvalidKeySpecException("Invalid DER: not a sequence");
			}

			// Parse inside the sequence
			parser = sequence.createNestedParser();

			parser.read(); // skip version
			DerParser.Asn1Object first = parser.read(); // read first token to identify
														// how the key is represented

			String parameterOid;
			// EC Key nested in a sequence
			if (first.getType() == DerParser.SEQUENCE) {

				DerParser nested = first.createNestedParser();
				DerParser.Asn1Object oid = nested.read();

				if (!EC_PUBLIC_KEY.equalsIgnoreCase(oid.getString())) {
					throw new InvalidKeySpecException(
							"Unsupported Public Key Algorithm. Expected EC (" + EC + "), but was: " + oid.getString());
				}

				parameterOid = nested.read().getString();
			}
			else {
				parameterOid = readParameters(parser);
			}

			byte[] octetString = first.getValue();
			BigInteger key = new BigInteger(1, octetString);

			AlgorithmParameters ec = AlgorithmParameters.getInstance("EC");
			ec.init(new ECGenParameterSpec(parameterOid));
			ECParameterSpec parameterSpec = ec.getParameterSpec(ECParameterSpec.class);

			return new ECPrivateKeySpec(key, parameterSpec);
		}

		private static String readParameters(DerParser keyParser) throws IOException, GeneralSecurityException {

			while (keyParser.hasLength()) {

				DerParser.Asn1Object object = keyParser.read();

				if (object.isTagged() && object.getTagNo() == 0) {
					return object.createNestedParser().read().getString();
				}
			}

			throw new InvalidParameterSpecException("Cannot decode EC parameter OID");
		}

	}

	/**
	 * Convert PKCS#1 encoded private key into RSAPrivateCrtKeySpec.
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
	 */
	static class RsaPrivateKeyFactory implements KeyFactory {

		@Override
		public RSAPrivateCrtKeySpec getKey(byte[] keyBytes) throws IOException, GeneralSecurityException {

			DerParser parser = new DerParser(keyBytes);

			DerParser.Asn1Object sequence = parser.read();
			if (sequence.getType() != DerParser.SEQUENCE) {
				throw new InvalidKeySpecException("Invalid DER: not a sequence");
			}

			// Parse inside the sequence
			parser = sequence.createNestedParser();
			parser.read();// Skip version

			DerParser.Asn1Object first = parser.read(); // read first token to identify
														// how the key is represented

			// RSA Key nested in a sequence
			if (first.getType() == DerParser.SEQUENCE) {

				DerParser nestedParser = first.createNestedParser();
				DerParser.Asn1Object oid = nestedParser.read();

				if (!RSA.equalsIgnoreCase(oid.getString())) {
					throw new InvalidKeySpecException("Unsupported Public Key Algorithm. Expected RSA (" + RSA
							+ "), but was: " + oid.getString());
				}

				DerParser.Asn1Object octetString = parser.read();
				return getKey(octetString.getValue());
			}

			BigInteger modulus = first.getInteger();
			BigInteger publicExp = parser.read().getInteger();
			BigInteger privateExp = parser.read().getInteger();
			BigInteger prime1 = parser.read().getInteger();
			BigInteger prime2 = parser.read().getInteger();
			BigInteger exp1 = parser.read().getInteger();
			BigInteger exp2 = parser.read().getInteger();
			BigInteger crtCoef = parser.read().getInteger();

			return new RSAPrivateCrtKeySpec(modulus, publicExp, privateExp, prime1, prime2, exp1, exp2, crtCoef);
		}

	}

	/**
	 * Convert PKCS#1 encoded public key into RSAPublicKeySpec.
	 * <p/>
	 * The ASN.1 syntax for the public key with CRT is
	 * <p/>
	 *
	 * <pre>
	 * --
	 * -- Representation of RSA public key with information for the CRT algorithm.
	 * --
	 * RSAPublicKey ::= SEQUENCE {
	 *   modulus           INTEGER,  -- n
	 *   publicExponent    INTEGER,  -- e
	 * }
	 * </pre>
	 *
	 * Supports PEM objects with a {@code SEQUENCE} and {@code OBJECT IDENTIFIER} header
	 * where the actual key sequence is represented as {@code BIT_STRING} (as of
	 * {@code openssl -pubout} format).
	 */
	static class RsaPublicKeyFactory implements KeyFactory {

		@Override
		public RSAPublicKeySpec getKey(byte[] keyBytes) throws IOException, GeneralSecurityException {

			DerParser parser = new DerParser(keyBytes);

			DerParser.Asn1Object sequence = parser.read();
			if (sequence.getType() != DerParser.SEQUENCE) {
				throw new InvalidKeySpecException("Invalid DER: not a sequence");
			}

			// Parse inside the sequence
			parser = sequence.createNestedParser();
			DerParser.Asn1Object object = parser.read();

			if (object.getType() == DerParser.SEQUENCE) {

				DerParser.Asn1Object read = object.createNestedParser().read();
				if (!RSA.equalsIgnoreCase(read.getString())) {
					throw new InvalidKeySpecException("Unsupported Public Key Algorithm. Expected RSA (" + RSA
							+ "), but was: " + read.getString());
				}

				DerParser.Asn1Object bitString = parser.read();
				if (bitString.getType() != DerParser.BIT_STRING) {
					throw new InvalidKeySpecException("Invalid DER: not a bit string");
				}

				parser = new DerParser(bitString.getValue());
				sequence = parser.read();

				if (sequence.getType() != DerParser.SEQUENCE) {
					throw new InvalidKeySpecException("Invalid DER: not a sequence");
				}

				parser = sequence.createNestedParser();
			}

			BigInteger modulus = parser.read().getInteger();
			BigInteger publicExp = parser.read().getInteger();

			return new RSAPublicKeySpec(modulus, publicExp);
		}

	}

}
