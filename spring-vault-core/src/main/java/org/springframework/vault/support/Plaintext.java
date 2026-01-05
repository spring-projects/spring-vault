/*
 * Copyright 2017-present the original author or authors.
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

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;

import org.springframework.util.Assert;

/**
 * Value object representing plain text with an optional
 * {@link VaultTransitContext}. Plaintext is represented binary safe as
 * {@code byte[]}.
 *
 * @author Praveendra Singh
 * @author Mark Paluch
 * @author Nanne Baars
 * @since 1.1
 */
public class Plaintext {

	private static final Plaintext EMPTY = new Plaintext(new byte[0], VaultTransitContext.empty());


	private final byte[] plaintext;

	private final VaultTransitContext context;


	private Plaintext(byte[] plaintext, VaultTransitContext context) {
		this.plaintext = plaintext;
		this.context = context;
	}


	/**
	 * Factory method to create an empty {@link Plaintext}.
	 * @return the empty {@link Plaintext} object.
	 * @since 1.1.2
	 */
	public static Plaintext empty() {
		return EMPTY;
	}

	/**
	 * Factory method to create {@link Plaintext} from a byte sequence.
	 * @param plaintext the plaintext to encrypt, must not be {@literal null}.
	 * @return the {@link Plaintext} for {@code plaintext}.
	 */
	public static Plaintext of(byte[] plaintext) {
		Assert.notNull(plaintext, "Plaintext must not be null");
		return plaintext.length == 0 ? empty() : new Plaintext(plaintext, VaultTransitContext.empty());
	}

	/**
	 * Factory method to create {@link Plaintext} using from a {@link String}.
	 * {@link String} is encoded to {@code byte} using the default
	 * {@link java.nio.charset.Charset}. Use
	 * {@link #of(String, java.nio.charset.Charset)} to control the
	 * {@link java.nio.charset.Charset} to use.
	 * @param plaintext the plaintext to encrypt, must not be {@literal null}.
	 * @return the {@link Plaintext} for {@code plaintext}.
	 */
	public static Plaintext of(String plaintext) {
		return of(plaintext, Charset.defaultCharset());
	}

	/**
	 * Factory method to create {@link Plaintext} using from a {@link String} using
	 * the given {@link java.nio.charset.Charset}. {@link java.nio.charset.Charset}.
	 * @param plaintext the plaintext to encrypt, must not be {@literal null}.
	 * @return the {@link Plaintext} for {@code plaintext}.
	 * @since 2.3
	 */
	public static Plaintext of(String plaintext, Charset charset) {
		Assert.notNull(plaintext, "Plaintext must not be null");
		Assert.notNull(charset, "Charset must not be null");
		return plaintext.isEmpty() ? empty() : of(plaintext.getBytes(charset));
	}


	public byte[] getPlaintext() {
		return this.plaintext;
	}

	public VaultTransitContext getContext() {
		return this.context;
	}

	/**
	 * Create a new {@link Plaintext} object from this plain text associated with
	 * the given {@link VaultTransitContext}.
	 * @param context transit context.
	 * @return the new {@link Plaintext} object.
	 */
	public Plaintext with(VaultTransitContext context) {
		return new Plaintext(getPlaintext(), context);
	}

	/**
	 * @return the plain text as {@link String} decoded using the default
	 * {@link java.nio.charset.Charset}.
	 */
	public String asString() {
		return asString(Charset.defaultCharset());
	}

	/**
	 * @param charset the charset to use for decoding.
	 * @return the plain text as {@link String} decoded using the default
	 * {@link java.nio.charset.Charset}.
	 * @since 2.3
	 */
	public String asString(Charset charset) {
		Assert.notNull(charset, "Charset must not be null");
		return new String(getPlaintext(), charset);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Plaintext plaintext1))
			return false;
		return Arrays.equals(this.plaintext, plaintext1.plaintext) && this.context.equals(plaintext1.context);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(this.context);
		result = 31 * result + Arrays.hashCode(this.plaintext);
		return result;
	}

}
