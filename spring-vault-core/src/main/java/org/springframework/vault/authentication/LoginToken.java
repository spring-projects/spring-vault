/*
 * Copyright 2016-2025 the original author or authors.
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
import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
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

	private final @Nullable String accessor;

	private final @Nullable String type;

	private LoginToken(char[] token, Duration duration, boolean renewable, @Nullable String accessor,
			@Nullable String type) {

		super(token);

		this.leaseDuration = duration;
		this.renewable = renewable;
		this.accessor = accessor;
		this.type = type;
	}

	/**
	 * @return a new {@link LoginTokenBuilder}.
	 * @since 3.0.2
	 */
	public static LoginTokenBuilder builder() {
		return new LoginTokenBuilder();
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

		return new LoginToken(token, leaseDuration, false, null, null);
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

		return new LoginToken(token, leaseDuration, true, null, null);
	}

	static boolean hasAccessor(VaultToken token) {
		return token instanceof LoginToken && StringUtils.hasText(((LoginToken) token).getAccessor());
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

	/**
	 * @return the token accessor.
	 * @since 3.0.2
	 */
	public @Nullable String getAccessor() {
		return accessor;
	}

	/**
	 * @return the token type.
	 * @since 3.0.2
	 * @see #isBatchToken()
	 * @see #isServiceToken())
	 */
	public @Nullable String getType() {
		return type;
	}

	/**
	 * @return {@literal true} if the token is a batch token.
	 * @since 3.0.2
	 */
	public boolean isBatchToken() {
		return "batch".equals(this.type);
	}

	/**
	 * @return {@literal true} if the token is a service token.
	 * @since 3.0.2
	 */
	public boolean isServiceToken() {
		return this.type == null || "service".equals(this.type);
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(getClass().getSimpleName());
		sb.append(" [renewable=").append(this.renewable);
		sb.append(", leaseDuration=").append(this.leaseDuration);
		sb.append(", type=").append(this.type);
		sb.append(']');
		return sb.toString();
	}

	/**
	 * Builder for {@link LoginToken}.
	 *
	 * @since 3.0.2
	 */
	public static class LoginTokenBuilder {

		private char @Nullable [] token;

		private boolean renewable;

		/**
		 * Duration in seconds.
		 */
		private Duration leaseDuration = Duration.ZERO;

		private @Nullable String accessor;

		private @Nullable String type;

		private LoginTokenBuilder() {
		}

		/**
		 * Configure the token value. This is a required builder property. Without this
		 * property, you cannot {@link #build()} a {@link LoginToken}.
		 * @param token must not be empty or {@literal null}.
		 * @return {@code this} {@link LoginTokenBuilder}.
		 */
		public LoginTokenBuilder token(String token) {

			Assert.hasText(token, "Token must not be empty");

			return token(token.toCharArray());
		}

		/**
		 * Configure the token value. This is a required builder property. Without this
		 * property, you cannot {@link #build()} a {@link LoginToken}.
		 * @param token must not be empty or {@literal null}.
		 * @return {@code this} {@link LoginTokenBuilder}.
		 */
		public LoginTokenBuilder token(char[] token) {

			Assert.notNull(token, "Token must not be null");
			Assert.isTrue(token.length > 0, "Token must not be empty");

			this.token = token;
			return this;
		}

		/**
		 * Configure whether the token is renewable.
		 * @param renewable
		 * @return {@code this} {@link LoginTokenBuilder}.
		 */
		public LoginTokenBuilder renewable(boolean renewable) {

			this.renewable = renewable;
			return this;
		}

		/**
		 * Configure the lease duration.
		 * @param leaseDuration must not be {@literal null}.
		 * @return {@code this} {@link LoginTokenBuilder}.
		 */
		public LoginTokenBuilder leaseDuration(Duration leaseDuration) {

			Assert.notNull(leaseDuration, "Lease duration must not be empty");

			this.leaseDuration = leaseDuration;
			return this;
		}

		/**
		 * Configure the token accessor.
		 * @param accessor must not be empty or {@literal null}.
		 * @return {@code this} {@link LoginTokenBuilder}.
		 */
		public LoginTokenBuilder accessor(String accessor) {

			Assert.hasText(accessor, "Token accessor must not be empty");

			this.accessor = accessor;
			return this;
		}

		/**
		 * Configure the token type.
		 * @param type must not be empty or {@literal null}.
		 * @return {@code this} {@link LoginTokenBuilder}.
		 */
		public LoginTokenBuilder type(String type) {

			Assert.hasText(type, "Token type must not be empty");

			this.type = type;
			return this;
		}

		/**
		 * Build a new {@link LoginToken} instance. {@link #token} must be configured.
		 * @return a new {@link LoginToken} instance.
		 */
		public LoginToken build() {

			Assert.notNull(token, "Token must not be null");

			return new LoginToken(Arrays.copyOf(this.token, this.token.length), this.leaseDuration, this.renewable,
					this.accessor, this.type);
		}

	}

}
