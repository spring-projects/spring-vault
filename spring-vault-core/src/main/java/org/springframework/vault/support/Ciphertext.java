/*
 * Copyright 2017-2021 the original author or authors.
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
 * Value object representing ciphertext with an optional {@link VaultTransitContext}.
 *
 * @author Praveendra Singh
 * @author Mark Paluch
 * @since 1.1
 */
public class Ciphertext {

	private final String ciphertext;

	private final VaultTransitContext context;

	private Ciphertext(String ciphertext, VaultTransitContext context) {

		this.ciphertext = ciphertext;
		this.context = context;
	}

	/**
	 * Factory method to create {@link Ciphertext} from the given {@code ciphertext}.
	 * @param ciphertext the ciphertext to decrypt, must not be {@literal null} or empty.
	 * @return the {@link Ciphertext} for {@code ciphertext}.
	 */
	public static Ciphertext of(String ciphertext) {

		Assert.hasText(ciphertext, "Ciphertext must not be null or empty");

		return new Ciphertext(ciphertext, VaultTransitContext.empty());
	}

	public String getCiphertext() {
		return this.ciphertext;
	}

	public VaultTransitContext getContext() {
		return this.context;
	}

	/**
	 * Create a new {@link Ciphertext} object from this ciphertext associated with the
	 * given {@link VaultTransitContext}.
	 * @param context transit context, must not be {@literal null}.
	 * @return the new {@link Ciphertext} object.
	 */
	public Ciphertext with(VaultTransitContext context) {

		Assert.notNull(context, "VaultTransitContext must not be null");

		return new Ciphertext(getCiphertext(), context);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Ciphertext))
			return false;
		Ciphertext that = (Ciphertext) o;
		return this.ciphertext.equals(that.ciphertext) && this.context.equals(that.context);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.ciphertext, this.context);
	}

}
