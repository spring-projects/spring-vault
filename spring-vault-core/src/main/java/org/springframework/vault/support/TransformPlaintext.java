/*
 * Copyright 2020 the original author or authors.
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

import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.Objects;

/**
 * Value object representing plaintext with an optional {@link VaultTransformContext}.
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
	 * @since 1.1.2
	 */
	public static TransformPlaintext empty() {
		return EMPTY;
	}

	/**
	 * Factory method to create {@link TransformPlaintext} from a byte sequence.
	 * @param plaintext the plaintext to encrypt, must not be {@literal null}.
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
	 * @param plaintext the plaintext to encrypt, must not be {@literal null}.
	 * @return the {@link TransformPlaintext} for {@code plaintext}.
	 */
	public static TransformPlaintext of(String plaintext) {

		Assert.notNull(plaintext, "Plaintext must not be null");

		if (plaintext.length() == 0) {
			return empty();
		}

		return of(plaintext.getBytes());
	}

	public byte[] getPlaintext() {
		return this.plaintext;
	}

	public VaultTransformContext getContext() {
		return this.context;
	}

	/**
	 * Create a new {@link TransformPlaintext} object from this plaintext associated with the given
	 * {@link VaultTransformContext}.
	 * @param context transform context.
	 * @return the new {@link TransformPlaintext} object.
	 */
	public TransformPlaintext with(VaultTransformContext context) {
		return new TransformPlaintext(getPlaintext(), context);
	}

	/**
	 * @return the plaintext as {@link String} decoded using the default
	 * {@link java.nio.charset.Charset}.
	 */
	public String asString() {
		return new String(getPlaintext());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof TransformPlaintext))
			return false;
		TransformPlaintext plaintext1 = (TransformPlaintext) o;
		return Arrays.equals(this.plaintext, plaintext1.plaintext) && this.context.equals(plaintext1.context);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(this.context);
		result = 31 * result + Arrays.hashCode(this.plaintext);
		return result;
	}

}
