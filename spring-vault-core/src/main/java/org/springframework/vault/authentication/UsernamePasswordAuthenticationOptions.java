/*
 * Copyright 2021-present the original author or authors.
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

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Authentication options for {@link UsernamePasswordAuthentication}.
 *
 * @author Mikhael Sokolov
 * @author Mark Paluch
 * @since 2.4
 * @see UsernamePasswordAuthentication
 * @see #builder()
 */
public class UsernamePasswordAuthenticationOptions {

	public static final String DEFAULT_USERPASS_AUTHENTICATION_PATH = "userpass";


	/**
	 * Path of the userpass authentication method mount.
	 */
	private final String path;

	/**
	 * Username of the userpass authentication method mount.
	 */
	private final String username;

	/**
	 * Password of the userpass authentication method mount.
	 */
	private final CharSequence password;

	/**
	 * TOTP (one-time-token, optional).
	 */
	@Nullable
	private final CharSequence totp;


	private UsernamePasswordAuthenticationOptions(String path, String username, CharSequence password,
			@Nullable CharSequence totp) {
		this.username = username;
		this.password = password;
		this.path = path;
		this.totp = totp;
	}


	/**
	 * @return a new {@link UsernamePasswordAuthenticationBuilder}.
	 */
	public static UsernamePasswordAuthenticationBuilder builder() {
		return new UsernamePasswordAuthenticationBuilder();
	}


	/**
	 * @return the path of the userpass authentication method mount.
	 */
	public String getPath() {
		return this.path;
	}

	/**
	 * @return the username.
	 */
	public String getUsername() {
		return this.username;
	}

	/**
	 * @return the password.
	 */
	public CharSequence getPassword() {
		return this.password;
	}

	/**
	 * @return the totp (one-time-token). Can be {@code null}.
	 */
	@Nullable
	public CharSequence getTotp() {
		return this.totp;
	}


	/**
	 * Builder for {@link UsernamePasswordAuthenticationOptions}.
	 */
	public static class UsernamePasswordAuthenticationBuilder {

		private String path = DEFAULT_USERPASS_AUTHENTICATION_PATH;

		@Nullable
		private String username;

		@Nullable
		private CharSequence password;

		@Nullable
		private CharSequence totp;


		UsernamePasswordAuthenticationBuilder() {
		}


		/**
		 * Configure a {@code username} for userpass authentication.
		 * @param username must not be empty or {@literal null}.
		 * @return this builder.
		 */
		public UsernamePasswordAuthenticationBuilder username(String username) {
			Assert.hasText(username, "Username must not be null and not be empty");
			this.username = username;
			return this;
		}

		/**
		 * Configure a {@code password} for userpass authentication.
		 * @param password must not be {@literal null}.
		 * @return this builder.
		 */
		public UsernamePasswordAuthenticationBuilder password(CharSequence password) {
			Assert.notNull(password, "Password must not be null");
			this.password = password;
			return this;
		}

		/**
		 * Configure an optional {@code totp} (time-based one-time token) for
		 * userpass/Okta authentication.
		 * @param totp must not be {@literal null}.
		 * @return this builder.
		 */
		public UsernamePasswordAuthenticationBuilder totp(CharSequence totp) {
			Assert.notNull(password, "One-time token must not be null");
			this.totp = totp;
			return this;
		}

		/**
		 * Configure the mount path.
		 * @param path must not be {@literal null} or empty.
		 * @return this builder.
		 */
		public UsernamePasswordAuthenticationBuilder path(String path) {
			Assert.hasText(path, "Path must not be empty");
			this.path = path;
			return this;
		}

		/**
		 * Build a new {@link UsernamePasswordAuthenticationOptions} instance.
		 * @return a new {@link UsernamePasswordAuthenticationOptions}.
		 */
		public UsernamePasswordAuthenticationOptions build() {
			Assert.hasText(this.username, "Username must not be null and not be empty");
			Assert.notNull(this.password, "Password must not be null");
			return new UsernamePasswordAuthenticationOptions(path, username, password, totp);
		}

	}

}
