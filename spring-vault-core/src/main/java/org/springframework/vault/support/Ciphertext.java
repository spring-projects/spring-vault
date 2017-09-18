/*
 * Copyright 2017 the original author or authors.
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
 * Value object representing ciphertext with an optional {@link VaultTransitContext}.
 *
 * @author Praveendra Singh
 * @author Mark Paluch
 * @since 1.1
 */
@EqualsAndHashCode
public class Ciphertext {

	private final String ciphertext;

	private final VaultTransitContext context;

	private Ciphertext(String ciphertext, VaultTransitContext context) {

		this.ciphertext = ciphertext;
		this.context = context;
	}

	/**
	 * Factory method to create {@link Ciphertext} from the given {@code ciphertext}.
	 *
	 * @param ciphertext the ciphertext to decrypt, must not be {@literal null} or empty.
	 * @return the {@link Ciphertext} for {@code ciphertext}.
	 */
	public static Ciphertext of(String ciphertext) {

		Assert.hasText(ciphertext, "Ciphertext must not be null or empty");

		return new Ciphertext(ciphertext, VaultTransitContext.empty());
	}

	public String getCiphertext() {
		return ciphertext;
	}

	public VaultTransitContext getContext() {
		return context;
	}

	/**
	 * Create a new {@link Ciphertext} object from this ciphertext associated with the
	 * given {@link VaultTransitContext}.
	 *
	 * @param context transit context.
	 * @return the new {@link Ciphertext} object.
	 */
	public Ciphertext with(VaultTransitContext context) {
		return new Ciphertext(getCiphertext(), context);
	}
}
