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

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Authentication options for {@link GitHubAuthentication}.
 * <p>
 * Authentication options provide the role and the token.
 * {@link GitHubAuthenticationOptions} can be constructed using {@link #builder()}.
 * Instances of this class are immutable once constructed.
 *
 * @author Nanne Baars
 * @since 3.2
 * @see GitHubAuthentication
 * @see #builder()
 */
public class GitHubAuthenticationOptions {

	public static final String DEFAULT_GITHUB_AUTHENTICATION_PATH = "github";

	/**
	 * Path of the GitHub authentication backend mount. Optional and defaults to
	 * {@literal github}.
	 */
	private final String path;

	/**
	 * Supplier instance to obtain the GitHub personal access token.
	 */
	private final Supplier<String> tokenSupplier;

	private GitHubAuthenticationOptions(Supplier<String> tokenSupplier, String path) {

		this.tokenSupplier = tokenSupplier;
		this.path = path;
	}

	/**
	 * @return a new {@link GitHubAuthenticationOptions}.
	 */
	public static GithubAuthenticationOptionsBuilder builder() {
		return new GithubAuthenticationOptionsBuilder();
	}

	/**
	 * @return access token to use.
	 */
	public Supplier<String> getTokenSupplier() {
		return this.tokenSupplier;
	}

	/**
	 * @return the path of the GitHub authentication backend mount.
	 */
	public String getPath() {
		return this.path;
	}

	/**
	 * Builder for {@link GitHubAuthenticationOptions}.
	 */
	public static class GithubAuthenticationOptionsBuilder {

		private String path = DEFAULT_GITHUB_AUTHENTICATION_PATH;

		private @Nullable Supplier<String> tokenSupplier;

		/**
		 * Configure the mount path.
		 * @param path must not be {@literal null} or empty.
		 * @return {@code this} {@link GithubAuthenticationOptionsBuilder}.
		 */
		public GithubAuthenticationOptionsBuilder path(String path) {

			Assert.hasText(path, "Path must not be empty");

			this.path = path;
			return this;
		}

		/**
		 * Configure the GitHub token. Vault authentication will use this token as
		 * singleton. If you want to provide a dynamic token that can change over time,
		 * see {@link #tokenSupplier(Supplier)}.
		 * @param token must not be {@literal null}.
		 * @return {@code this} {@link GithubAuthenticationOptionsBuilder}.
		 */
		public GithubAuthenticationOptionsBuilder token(String token) {

			Assert.hasText(token, "Token must not be empty");

			return tokenSupplier(() -> token);
		}

		/**
		 * Configure the {@link Supplier} to obtain a token.
		 * @param tokenSupplier must not be {@literal null}.
		 * @return {@code this} {@link GithubAuthenticationOptionsBuilder}.
		 */
		public GithubAuthenticationOptionsBuilder tokenSupplier(Supplier<String> tokenSupplier) {

			Assert.notNull(tokenSupplier, "Token supplier must not be null");

			this.tokenSupplier = tokenSupplier;
			return this;
		}

		/**
		 * Build a new {@link GitHubAuthenticationOptions} instance.
		 * @return a new {@link GitHubAuthenticationOptions}.
		 */
		public GitHubAuthenticationOptions build() {

			Assert.notNull(this.tokenSupplier, "Token must not be null");

			return new GitHubAuthenticationOptions(this.tokenSupplier, this.path);
		}

	}

}
