/*
 * Copyright 2020-2025 the original author or authors.
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

import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Value object representing plain text with an optional {@link VaultTransformContext}.
 * Plaintext is represented binary safe as {@code byte[]}.
 *
 * @author Lauren Voswinkel
 * @since 2.3
 */
public class TransformPlaintext {

	private static final TransformPlaintext EMPTY = new TransformPlaintext(new byte[0], VaultTransformContext.empty());

	private final byte[] plaintext;

	private final VaultTransformContext context;

	private TransformPlaintext(byte[] plaintext, VaultTransformContext context) {

		this.plaintext = plaintext;
		this.context = context;
	}

	/**
	 * Factory method to create an empty {@link TransformPlaintext}.
	 * @return the empty {@link TransformPlaintext} object.
	 */
	public static TransformPlaintext empty() {
		return EMPTY;
	}

	/**
	 * Factory method to create {@link TransformPlaintext} from a byte sequence.
	 * @param plaintext the plain text to encrypt, must not be {@literal null}.
	 * @return the {@link TransformPlaintext} for {@code plaintext}.
	 */
	public static TransformPlaintext of(byte[] plaintext) {

		Assert.notNull(plaintext, "Plaintext must not be null");

		if (plaintext.length == 0) {
			return empty();
		}

		return new TransformPlaintext(plaintext, VaultTransformContext.empty());
	}

	/**
	 * Factory method to create {@link TransformPlaintext} using from {@link String}.
	 * {@link String} is encoded to {@code byte} using the default
	 * {@link java.nio.charset.Charset}.
	 * @param plaintext the plain text to encrypt, must not be {@literal null}.
	 * @return the {@link TransformPlaintext} for {@code plaintext}.
	 */
	public static TransformPlaintext of(String plaintext) {
		return of(plaintext, Charset.defaultCharset());
	}

	/**
	 * Factory method to create {@link TransformPlaintext} using from a {@link String}
	 * using the given {@link java.nio.charset.Charset}. {@link java.nio.charset.Charset}.
	 * @param plaintext the plaintext to encrypt, must not be {@literal null}.
	 * @return the {@link Plaintext} for {@code plaintext}.
	 */
	public static TransformPlaintext of(String plaintext, Charset charset) {

		Assert.notNull(plaintext, "Plaintext must not be null");
		Assert.notNull(charset, "Charset must not be null");

		if (plaintext.length() == 0) {
			return empty();
		}

		return of(plaintext.getBytes(charset));
	}

	public byte[] getPlaintext() {
		return this.plaintext;
	}

	public VaultTransformContext getContext() {
		return this.context;
	}

	/**
	 * Create a new {@link TransformPlaintext} object from this plaintext associated with
	 * the given {@link VaultTransformContext}.
	 * @param context transform context.
	 * @return the new {@link TransformPlaintext} object.
	 */
	public TransformPlaintext with(VaultTransformContext context) {

		Assert.notNull(context, "VaultTransformContext must not be null");

		return new TransformPlaintext(getPlaintext(), context);
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
	 */
	public String asString(Charset charset) {

		Assert.notNull(charset, "Charset must not be null");

		return new String(getPlaintext(), charset);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof TransformPlaintext that))
			return false;
		if (!ObjectUtils.nullSafeEquals(this.plaintext, that.plaintext)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(this.context, that.context);
	}

	@Override
	public int hashCode() {
		int result = Arrays.hashCode(this.plaintext);
		result = 31 * result + ObjectUtils.nullSafeHashCode(this.context);
		return result;
	}

}
