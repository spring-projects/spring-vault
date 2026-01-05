/*
 * Copyright 2020-present the original author or authors.
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

import java.util.Objects;

import org.springframework.util.Assert;

/**
 * Value object representing cipher text with an optional
 * {@link VaultTransformContext}.
 *
 * @author Lauren Voswinkel
 * @since 2.3
 */
public class TransformCiphertext {

	private final String ciphertext;

	private final VaultTransformContext context;


	private TransformCiphertext(String ciphertext, VaultTransformContext context) {
		this.ciphertext = ciphertext;
		this.context = context;
	}


	/**
	 * Factory method to create {@link TransformCiphertext} from the given
	 * {@code ciphertext}.
	 * @param ciphertext the cipher text to decrypt, must not be {@literal null} or
	 * empty.
	 * @return the {@link TransformCiphertext} for {@code ciphertext}.
	 */
	public static TransformCiphertext of(String ciphertext) {
		Assert.hasText(ciphertext, "Ciphertext must not be null or empty");
		return new TransformCiphertext(ciphertext, VaultTransformContext.empty());
	}


	public String getCiphertext() {
		return this.ciphertext;
	}

	public VaultTransformContext getContext() {
		return this.context;
	}

	/**
	 * Create a new {@link TransformCiphertext} object from this ciphertext
	 * associated with the given {@link VaultTransformContext}.
	 * @param context transit context, must not be {@literal null}.
	 * @return the new {@link TransformCiphertext} object.
	 */
	public TransformCiphertext with(VaultTransformContext context) {
		Assert.notNull(context, "VaultTransitContext must not be null");
		return new TransformCiphertext(getCiphertext(), context);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof TransformCiphertext that))
			return false;
		return this.ciphertext.equals(that.ciphertext) && this.context.equals(that.context);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.ciphertext, this.context);
	}

}
