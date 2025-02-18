/*
 * Copyright 2022-2025 the original author or authors.
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
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;

/**
 * A bare-minimum ASN.1 DER decoder, just having enough functions to decode PKCS#1 private
 * keys. Especially, it doesn't handle explicitly tagged types with an outer tag.
 * <p/>
 * <p/>
 * This parser can only handle one layer. To parse nested constructs, get a new parser for
 * each layer using <code>Asn1Object.getParser()</code>.
 * <p/>
 * <p/>
 * There are many DER decoders in JRE but using them will tie this program to a specific
 * JCE/JVM.
 *
 * @author Mark Paluch
 * @since 2.4
 */
@SuppressWarnings("unused")
class DerParser {

	// Classes
	static final int UNIVERSAL = 0x00;
	static final int APPLICATION = 0x40;
	static final int CONTEXT = 0x80;
	static final int PRIVATE = 0xC0;

	// Constructed Flag
	static final int CONSTRUCTED = 0x20;

	// Tag and data types
	static final int ANY = 0x00;
	static final int BOOLEAN = 0x01;
	static final int INTEGER = 0x02;
	static final int BIT_STRING = 0x03;
	static final int OCTET_STRING = 0x04;
	static final int NULL = 0x05;
	static final int OID = 0x06;
	static final int REAL = 0x09;
	static final int ENUMERATED = 0x0a;

	static final int SEQUENCE = 0x10;
	static final int SET = 0x11;

	static final int NUMERIC_STRING = 0x12;
	static final int PRINTABLE_STRING = 0x13;
	static final int VIDEOTEX_STRING = 0x15;
	static final int IA5_STRING = 0x16;
	static final int GRAPHIC_STRING = 0x19;
	static final int ISO646_STRING = 0x1A;
	static final int GENERAL_STRING = 0x1B;

	static final int TAGGED = 0x80; // decimal 128

	static final int UTF8_STRING = 0x0C;
	static final int UNIVERSAL_STRING = 0x1C;
	static final int BMP_STRING = 0x1E;

	static final int UTC_TIME = 0x17;

	protected InputStream in;

	/**
	 * Create a new DER decoder from an input stream.
	 * @param in The DER encoded stream
	 */
	DerParser(InputStream in) {
		this.in = in;
	}

	/**
	 * Create a new DER decoder from a byte array.
	 * @param bytes The encoded bytes
	 */
	DerParser(byte[] bytes) {
		this(new ByteArrayInputStream(bytes));
	}

	/**
	 * Read next object. If it's constructed, the value holds encoded content and it
	 * should be parsed by a new parser from <code>Asn1Object.getParser</code>.
	 * @return A object
	 */
	public Asn1Object read() throws IOException {

		int tag = this.in.read();

		if (tag == -1) {
			throw new IllegalStateException("Invalid DER: stream too short, missing tag");
		}
		int tagNo = readTagNumber(this.in, tag);

		int length = getLength();

		if (tag == BIT_STRING) {
			// Not sure what to do with this one.
			int padBits = this.in.read();
			length--;
		}

		byte[] value = new byte[length];
		int n = this.in.read(value);
		if (n < length) {
			throw new IllegalStateException("Invalid DER: stream too short, missing value");
		}

		return new Asn1Object(tag, tagNo, length, value);
	}

	static int readTagNumber(InputStream s, int tag) throws IOException {
		int tagNo = tag & 0x1f;

		//
		// with tagged object tag number is bottom 5 bits, or stored at the start of the
		// content
		//
		if (tagNo == 0x1f) {
			tagNo = 0;

			int b = s.read();

			// X.690-0207 8.1.2.4.2
			// "c) bits 7 to 1 of the first subsequent octet shall not all be zero."
			if ((b & 0x7f) == 0) // Note: -1 will pass
			{
				throw new IOException("corrupted stream - invalid high tag number found");
			}

			while ((b >= 0) && ((b & 0x80) != 0)) {
				tagNo |= (b & 0x7f);
				tagNo <<= 7;
				b = s.read();
			}

			if (b < 0) {
				throw new EOFException("EOF found inside tag value.");
			}

			tagNo |= (b & 0x7f);
		}

		return tagNo;
	}

	/**
	 * Decode the length of the field. Can only support length encoding up to 4 octets.
	 * <p/>
	 * <p/>
	 * In BER/DER encoding, length can be encoded in 2 forms,
	 * <ul>
	 * <li>Short form. One octet. Bit 8 has value "0" and bits 7-1 give the length.
	 * <li>Long form. Two to 127 octets (only 4 is supported here). Bit 8 of first octet
	 * has value "1" and bits 7-1 give the number of additional length octets. Second and
	 * following octets give the length, base 256, most significant digit first.
	 * </ul>
	 * @return The length as integer
	 */
	private int getLength() throws IOException {

		int i = this.in.read();
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
			throw new IllegalStateException("Invalid DER: length field too big (" + i + ")");
		}

		byte[] bytes = new byte[num];
		int n = this.in.read(bytes);
		if (n < num) {
			throw new IllegalStateException("Invalid DER: length too short");
		}

		return new BigInteger(1, bytes).intValue();
	}

	public boolean hasLength() throws IOException {
		return this.in.available() > 0;
	}

	/**
	 * An ASN.1 TLV. The object is not parsed. It can only handle integers and strings.
	 */
	static class Asn1Object {

		private static final long LONG_LIMIT = (Long.MAX_VALUE >> 7) - 0x7f;

		private final int type;

		private final int tag;

		private final int tagNo;

		private final int length;

		private final byte[] value;

		/**
		 * Construct an ASN.1 TLV. The TLV could be either a constructed or primitive
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
		 * @param tag Tag or Identifier
		 * @param tagNo Tag Number
		 * @param length Length of the field
		 * @param value Encoded octet string for the field.
		 */
		Asn1Object(int tag, int tagNo, int length, byte[] value) {
			this.tag = tag;
			this.tagNo = tagNo;
			this.type = tag & 0x1F;
			this.length = length;
			this.value = value;
		}

		int getTagNo() {
			return tagNo;
		}

		boolean isTagged() {
			return (this.tag & TAGGED) != 0;
		}

		int getType() {
			return this.type;
		}

		int getLength() {
			return this.length;
		}

		byte[] getValue() {
			return this.value;
		}

		boolean isConstructed() {
			return (this.tag & CONSTRUCTED) == CONSTRUCTED;
		}

		/**
		 * For constructed field, return a parser for its content.
		 * @return A parser for the construct.
		 */
		DerParser createNestedParser() {

			if (!isConstructed()) {
				throw new IllegalStateException("Invalid DER: can't parse primitive entity");
			}

			return new DerParser(this.value);
		}

		/**
		 * Get the value as integer
		 * @return BigInteger
		 */
		BigInteger getInteger() {

			if (this.type != INTEGER) {
				throw new IllegalStateException("Invalid DER: object (%d) is not integer.".formatted(this.type));
			}

			return new BigInteger(this.value);
		}

		/**
		 * Get value as string. Most strings are treated as ISO-8859-1.
		 * @return Java string
		 */
		String getString() throws IOException {

			String encoding;

			switch (this.type) {

				// Not all are ISO-8859-1 but it's the closest thing
				case NUMERIC_STRING:
				case PRINTABLE_STRING:
				case VIDEOTEX_STRING:
				case IA5_STRING:
				case GRAPHIC_STRING:
				case ISO646_STRING:
				case GENERAL_STRING:
					encoding = "ISO-8859-1";
					break;

				case BMP_STRING:
					encoding = "UTF-16BE";
					break;

				case UTF8_STRING:
					encoding = "UTF-8";
					break;

				case UNIVERSAL_STRING:
					throw new IllegalStateException("Invalid DER: can't handle UCS-4 string");

				case OID:
					return getObjectIdentifier(this.value);
				default:
					throw new IllegalStateException("Invalid DER: object (%d) is not a string".formatted(this.type));
			}

			return new String(this.value, encoding);
		}

		private static String getObjectIdentifier(byte bytes[]) {
			StringBuffer objId = new StringBuffer();
			long value = 0;
			BigInteger bigValue = null;
			boolean first = true;

			for (int i = 0; i != bytes.length; i++) {
				int b = bytes[i] & 0xff;

				if (value <= LONG_LIMIT) {
					value += (b & 0x7f);
					if ((b & 0x80) == 0) // end of number reached
					{
						if (first) {
							if (value < 40) {
								objId.append('0');
							}
							else if (value < 80) {
								objId.append('1');
								value -= 40;
							}
							else {
								objId.append('2');
								value -= 80;
							}
							first = false;
						}

						objId.append('.');
						objId.append(value);
						value = 0;
					}
					else {
						value <<= 7;
					}
				}
				else {
					if (bigValue == null) {
						bigValue = BigInteger.valueOf(value);
					}
					bigValue = bigValue.or(BigInteger.valueOf(b & 0x7f));
					if ((b & 0x80) == 0) {
						if (first) {
							objId.append('2');
							bigValue = bigValue.subtract(BigInteger.valueOf(80));
							first = false;
						}

						objId.append('.');
						objId.append(bigValue);
						bigValue = null;
						value = 0;
					}
					else {
						bigValue = bigValue.shiftLeft(7);
					}
				}
			}

			return objId.toString();
		}

	}

}
