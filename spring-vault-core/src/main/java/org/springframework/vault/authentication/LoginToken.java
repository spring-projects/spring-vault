/*
 * Copyright 2016 the original author or authors.
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
package org.springframework.vault.authentication;

import lombok.ToString;

import org.springframework.util.Assert;
import org.springframework.vault.support.VaultToken;

/**
 * Value object for a Vault token obtained by a login method.
 *
 * @author Mark Paluch
 */
@ToString(exclude = "token")
class LoginToken extends VaultToken {

	private final boolean renewable;

	private final long leaseDuration;

	private LoginToken(char[] token, long leaseDuration, boolean renewable) {

		super(token);

		this.leaseDuration = leaseDuration;
		this.renewable = renewable;
	}

	/**
	 * Create a new {@link LoginToken}.
	 *
	 * @param token must not be {@literal null}.
	 * @return the created {@link VaultToken}
	 */
	public static LoginToken of(String token) {

		Assert.hasText(token, "Token must not be empty");

		return of(token, 0);
	}

	/**
	 * Create a new {@link LoginToken}.
	 *
	 * @param token must not be {@literal null}.
	 * @return the created {@link VaultToken}
	 * @since 1.1
	 */
	public static LoginToken of(char[] token) {
		return of(token, 0);
	}

	/**
	 * Create a new {@link LoginToken} with a {@code leaseDuration}.
	 *
	 * @param token must not be {@literal null}.
	 * @param leaseDuration the lease duration.
	 * @return the created {@link VaultToken}
	 */
	public static LoginToken of(String token, long leaseDuration) {

		Assert.hasText(token, "Token must not be empty");

		return of(token.toCharArray(), leaseDuration);
	}

	/**
	 * Create a new {@link LoginToken} with a {@code leaseDuration}.
	 *
	 * @param token must not be {@literal null}.
	 * @param leaseDuration the lease duration.
	 * @return the created {@link VaultToken}
	 * @since 1.1
	 */
	public static LoginToken of(char[] token, long leaseDuration) {

		Assert.notNull(token, "Token must not be null");
		Assert.isTrue(token.length > 0, "Token must not be empty");

		return new LoginToken(token, leaseDuration, false);
	}

	/**
	 * Create a new renewable {@link LoginToken} with a {@code leaseDuration}.
	 *
	 * @param token must not be {@literal null}.
	 * @param leaseDuration the lease duration.
	 * @return the created {@link VaultToken}
	 */
	public static LoginToken renewable(String token, long leaseDuration) {

		Assert.hasText(token, "Token must not be empty");

		return renewable(token.toCharArray(), leaseDuration);
	}

	/**
	 * Create a new renewable {@link LoginToken} with a {@code leaseDuration}.
	 *
	 * @param token must not be {@literal null}.
	 * @param leaseDuration the lease duration.
	 * @return the created {@link VaultToken}
	 * @since 1.1
	 */
	public static LoginToken renewable(char[] token, long leaseDuration) {

		Assert.notNull(token, "Token must not be null");
		Assert.isTrue(token.length > 0, "Token must not be empty");

		return new LoginToken(token, leaseDuration, true);
	}

	/**
	 * @return the lease duration. May be {@literal 0} if none.
	 */
	public long getLeaseDuration() {
		return leaseDuration;
	}

	/**
	 * @return {@literal true} if this token is renewable; {@literal false} otherwise.
	 */
	public boolean isRenewable() {
		return renewable;
	}

}
