/*
 * Copyright 2017-2025 the original author or authors.
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

import java.util.function.Supplier;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Authentication options for {@link JwtAuthentication}.
 * <p>
 * Authentication options provide the role and the JWT. {@link JwtAuthenticationOptions}
 * can be constructed using {@link #builder()}. Instances of this class are immutable once
 * constructed.
 *
 * @author Nanne Baars
 * @author Mark Paluch
 * @since 3.1
 * @see JwtAuthentication
 * @see #builder()
 */
public class JwtAuthenticationOptions {

	public static final String DEFAULT_JWT_AUTHENTICATION_PATH = "jwt";

	/**
	 * Path of the JWT authentication backend mount. Optional and defaults to
	 * {@literal jwt}.
	 */
	private final String path;

	/**
	 * Name of the role against which the login is being attempted. Defaults to configured
	 * default_role if not provided. See
	 * <a href="https://developer.hashicorp.com/vault/api-docs/auth/jwt#configure">Vault
	 * JWT configuration</a>
	 */
	@Nullable
	private final String role;

	/**
	 * Supplier instance to obtain a service account JSON Web Tokens.
	 */
	private final Supplier<String> jwtSupplier;

	private JwtAuthenticationOptions(@Nullable String role, Supplier<String> jwtSupplier, String path) {

		this.role = role;
		this.jwtSupplier = jwtSupplier;
		this.path = path;
	}

	/**
	 * @return a new {@link JwtAuthenticationOptionsBuilder}.
	 */
	public static JwtAuthenticationOptionsBuilder builder() {
		return new JwtAuthenticationOptionsBuilder();
	}

	/**
	 * @return name of the role against which the login is being attempted. Can be
	 * {@literal null} if not configured.
	 */
	@Nullable
	public String getRole() {
		return this.role;
	}

	/**
	 * @return JSON Web Token.
	 */
	public Supplier<String> getJwtSupplier() {
		return this.jwtSupplier;
	}

	/**
	 * @return the path of the JWT authentication backend mount.
	 */
	public String getPath() {
		return this.path;
	}

	/**
	 * Builder for {@link JwtAuthenticationOptions}.
	 */
	public static class JwtAuthenticationOptionsBuilder {

		private String path = DEFAULT_JWT_AUTHENTICATION_PATH;

		@Nullable
		private String role;

		@Nullable
		private Supplier<String> jwtSupplier;

		/**
		 * Configure the mount path.
		 * @param path must not be {@literal null} or empty.
		 * @return {@code this} {@link JwtAuthenticationOptionsBuilder}.
		 */
		public JwtAuthenticationOptionsBuilder path(String path) {

			Assert.hasText(path, "Path must not be empty");

			this.path = path;
			return this;
		}

		/**
		 * Configure the role.
		 * @param role name of the role against which the login is being attempted, must
		 * not be {@literal null} or empty.
		 * @return {@code this} {@link JwtAuthenticationOptionsBuilder}.
		 */
		public JwtAuthenticationOptionsBuilder role(String role) {

			Assert.hasText(role, "Role must not be empty");

			this.role = role;
			return this;
		}

		/**
		 * Configure the JWT authentication token. Vault authentication will use this
		 * token as singleton. If you want to provide a dynamic token that can change over
		 * time, see {@link #jwtSupplier(Supplier)}.
		 * @param jwt must not be {@literal null}.
		 * @return {@code this} {@link JwtAuthenticationOptionsBuilder}.
		 */
		public JwtAuthenticationOptionsBuilder jwt(String jwt) {

			Assert.hasText(jwt, "JWT must not be empty");

			return jwtSupplier(() -> jwt);
		}

		/**
		 * Configure the {@link Supplier} to obtain a JWT authentication token.
		 * @param jwtSupplier must not be {@literal null}.
		 * @return {@code this} {@link JwtAuthenticationOptionsBuilder}.
		 */
		public JwtAuthenticationOptionsBuilder jwtSupplier(Supplier<String> jwtSupplier) {

			Assert.notNull(jwtSupplier, "JWT supplier must not be null");

			this.jwtSupplier = jwtSupplier;
			return this;
		}

		/**
		 * Build a new {@link JwtAuthenticationOptions} instance.
		 * @return a new {@link JwtAuthenticationOptions}.
		 */
		public JwtAuthenticationOptions build() {

			Assert.notNull(this.jwtSupplier, "JWT must not be null");

			return new JwtAuthenticationOptions(this.role, this.jwtSupplier, this.path);
		}

	}

}
