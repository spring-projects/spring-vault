/*
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.vault.support;

import lombok.EqualsAndHashCode;

import org.springframework.util.Assert;

/**
 * Value object representing plaintext with an optional {@link VaultTransitContext}.
 * Plaintext is represented binary safe as {@code byte[]}.
 *
 * @author Praveendra Singh
 * @author Mark Paluch
 * @since 1.1
 */
@EqualsAndHashCode
public class Plaintext {

	private final byte[] plaintext;

	private final VaultTransitContext context;

	private Plaintext(byte[] plaintext, VaultTransitContext context) {

		this.plaintext = plaintext;
		this.context = context;
	}

	/**
	 * Factory method to create {@link Plaintext} from a byte sequence.
	 *
	 * @param plaintext the plaintext to encrypt, must not be {@literal null}.
	 * @return the {@link Plaintext} for {@code plaintext}.
	 */
	public static Plaintext of(byte[] plaintext) {

		Assert.notNull(plaintext, "Plaintext must not be null");

		return new Plaintext(plaintext, null);
	}

	/**
	 * Factory method to create {@link Plaintext} using from {@link String}.
	 * {@link String} is encoded to {@code byte} using the default
	 * {@link java.nio.charset.Charset}.
	 *
	 * @param plaintext the plaintext to encrypt, must not be {@literal null}.
	 * @return the {@link Plaintext} for {@code plaintext}.
	 */
	public static Plaintext of(String plaintext) {

		Assert.notNull(plaintext, "Plaintext must not be null");

		return of(plaintext.getBytes());
	}

	public byte[] getPlaintext() {
		return plaintext;
	}

	public VaultTransitContext getContext() {
		return context;
	}

	/**
	 * Create a new {@link Plaintext} object from this plaintext associated with the given
	 * {@link VaultTransitContext}.
	 *
	 * @param context transit context.
	 * @return the new {@link Plaintext} object.
	 */
	public Plaintext with(VaultTransitContext context) {
		return new Plaintext(getPlaintext(), context);
	}

	/**
	 * @return the plaintext as {@link String} decoded using the default
	 * {@link java.nio.charset.Charset}.
	 */
	public String asString() {
		return new String(getPlaintext());
	}
}
