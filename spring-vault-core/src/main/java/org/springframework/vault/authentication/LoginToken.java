/*
 * Copyright 2016-2022 the original author or authors.
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
package org.springframework.vault.authentication;

import java.time.Duration;

import org.springframework.util.Assert;
import org.springframework.vault.support.VaultToken;

/**
 * Value object for a Vault token obtained by a login method.
 *
 * @author Mark Paluch
 */
public class LoginToken extends VaultToken {

	private final boolean renewable;

	/**
	 * Duration in seconds.
	 */
	private final Duration leaseDuration;

	private LoginToken(char[] token, Duration duration, boolean renewable) {

		super(token);

		this.leaseDuration = duration;
		this.renewable = renewable;
	}

	/**
	 * Create a new {@link LoginToken}.
	 * @param token must not be {@literal null}.
	 * @return the created {@link VaultToken}
	 */
	public static LoginToken of(String token) {

		Assert.hasText(token, "Token must not be empty");

		return of(token.toCharArray(), Duration.ZERO);
	}

	/**
	 * Create a new {@link LoginToken}.
	 * @param token must not be {@literal null}.
	 * @return the created {@link VaultToken}
	 * @since 1.1
	 */
	public static LoginToken of(char[] token) {
		return of(token, Duration.ZERO);
	}

	/**
	 * Create a new {@link LoginToken} with a {@code leaseDurationSeconds}.
	 * @param token must not be {@literal null}.
	 * @param leaseDurationSeconds the lease duration in seconds, must not be negative.
	 * @return the created {@link VaultToken}
	 * @deprecated since 2.0, use {@link #of(char[], Duration)} for time unit safety.
	 */
	@Deprecated
	public static LoginToken of(String token, long leaseDurationSeconds) {

		Assert.hasText(token, "Token must not be empty");
		Assert.isTrue(leaseDurationSeconds >= 0, "Lease duration must not be negative");

		return of(token.toCharArray(), Duration.ofSeconds(leaseDurationSeconds));
	}

	/**
	 * Create a new {@link LoginToken} with a {@code leaseDurationSeconds}.
	 * @param token must not be {@literal null}.
	 * @param leaseDurationSeconds the lease duration in seconds, must not be negative.
	 * @return the created {@link VaultToken}
	 * @since 1.1
	 * @deprecated since 2.0, use {@link #of(char[], Duration)} for time unit safety.
	 */
	@Deprecated
	public static LoginToken of(char[] token, long leaseDurationSeconds) {

		Assert.notNull(token, "Token must not be null");
		Assert.isTrue(token.length > 0, "Token must not be empty");
		Assert.isTrue(leaseDurationSeconds >= 0, "Lease duration must not be negative");

		return new LoginToken(token, Duration.ofSeconds(leaseDurationSeconds), false);
	}

	/**
	 * Create a new {@link LoginToken} with a {@code leaseDurationSeconds}.
	 * @param token must not be {@literal null}.
	 * @param leaseDuration the lease duration, must not be negative and not be
	 * {@literal null}.
	 * @return the created {@link VaultToken}
	 * @since 2.0
	 */
	public static LoginToken of(char[] token, Duration leaseDuration) {

		Assert.notNull(token, "Token must not be null");
		Assert.isTrue(token.length > 0, "Token must not be empty");
		Assert.notNull(leaseDuration, "Lease duration must not be null");
		Assert.isTrue(!leaseDuration.isNegative(), "Lease duration must not be negative");

		return new LoginToken(token, leaseDuration, false);
	}

	/**
	 * Create a new renewable {@link LoginToken} with a {@code leaseDurationSeconds}.
	 * @param token must not be {@literal null}.
	 * @param leaseDurationSeconds the lease duration in seconds, must not be negative.
	 * @return the created {@link VaultToken}
	 * @deprecated since 2.0, use {@link #renewable(char[], Duration)} for time unit
	 * safety.
	 */
	@Deprecated
	public static LoginToken renewable(String token, long leaseDurationSeconds) {

		Assert.hasText(token, "Token must not be empty");
		Assert.isTrue(leaseDurationSeconds >= 0, "Lease duration must not be negative");

		return renewable(token.toCharArray(), Duration.ofSeconds(leaseDurationSeconds));
	}

	/**
	 * Create a new renewable {@link LoginToken} with a {@code leaseDurationSeconds}.
	 * @param token must not be {@literal null}.
	 * @param leaseDurationSeconds the lease duration in seconds, must not be negative.
	 * @return the created {@link VaultToken}
	 * @since 2.0
	 * @deprecated since 2.0, use {@link #renewable(char[], Duration)} for time unit
	 * safety.
	 */
	@Deprecated
	public static LoginToken renewable(char[] token, long leaseDurationSeconds) {

		Assert.notNull(token, "Token must not be null");
		Assert.isTrue(token.length > 0, "Token must not be empty");
		Assert.isTrue(leaseDurationSeconds >= 0, "Lease duration must not be negative");

		return new LoginToken(token, Duration.ofSeconds(leaseDurationSeconds), true);
	}

	/**
	 * Create a new renewable {@link LoginToken} with a {@code leaseDurationSeconds}.
	 * @param token must not be {@literal null}.
	 * @param leaseDuration the lease duration, must not be {@literal null} or negative.
	 * @return the created {@link VaultToken}
	 * @since 2.0
	 */
	public static LoginToken renewable(VaultToken token, Duration leaseDuration) {

		Assert.notNull(token, "Token must not be null");

		return renewable(token.toCharArray(), leaseDuration);
	}

	/**
	 * Create a new renewable {@link LoginToken} with a {@code leaseDurationSeconds}.
	 * @param token must not be {@literal null}.
	 * @param leaseDuration the lease duration, must not be {@literal null} or negative.
	 * @return the created {@link VaultToken}
	 * @since 2.0
	 */
	public static LoginToken renewable(char[] token, Duration leaseDuration) {

		Assert.notNull(token, "Token must not be null");
		Assert.isTrue(token.length > 0, "Token must not be empty");
		Assert.notNull(leaseDuration, "Lease duration must not be null");
		Assert.isTrue(!leaseDuration.isNegative(), "Lease duration must not be negative");

		return new LoginToken(token, leaseDuration, true);
	}

	/**
	 * @return the lease duration in seconds. May be {@literal 0} if none.
	 */
	public Duration getLeaseDuration() {
		return this.leaseDuration;
	}

	/**
	 * @return {@literal true} if this token is renewable; {@literal false} otherwise.
	 */
	public boolean isRenewable() {
		return this.renewable;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(getClass().getSimpleName());
		sb.append(" [renewable=").append(this.renewable);
		sb.append(", leaseDuration=").append(this.leaseDuration);
		sb.append(']');
		return sb.toString();
	}

}
