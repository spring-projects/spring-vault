/*
 * Copyright 2016-present the original author or authors.
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

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import org.springframework.lang.Contract;
import org.springframework.util.Assert;

/**
 * Value object for a Vault token.
 *
 * @author Mark Paluch
 */
public class VaultToken {

	private final char[] token;


	protected VaultToken(char[] token) {
		Assert.notNull(token, "Token must not be null");
		Assert.isTrue(token.length > 0, "Token must not be empty");
		this.token = Arrays.copyOf(token, token.length);
	}

	/**
	 * Create a new {@link VaultToken}.
	 * @param token must not be empty or {@literal null}.
	 * @return the created {@link VaultToken}
	 */
	@Contract("null -> fail")
	public static VaultToken of(@Nullable String token) {
		Assert.hasText(token, "Token must not be empty");
		return of(token.toCharArray());
	}

	/**
	 * Create a new {@link VaultToken}.
	 * @param token must not be empty or {@literal null}.
	 * @return the created {@link VaultToken}
	 * @since 1.1
	 */
	public static VaultToken of(char[] token) {
		return new VaultToken(token);
	}


	/**
	 * @return the token value.
	 */
	public String getToken() {
		return new String(this.token);
	}

	/**
	 * @return the token value.
	 * @since 1.1
	 */
	public char[] toCharArray() {
		return Arrays.copyOf(this.token, this.token.length);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof VaultToken that))
			return false;
		return Arrays.equals(this.token, that.token);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.token);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

}
