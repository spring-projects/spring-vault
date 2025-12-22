/*
 * Copyright 2020-2025 the original author or authors.
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

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Authentication options for {@link ClientCertificateAuthentication}.
 * <p>Authentication options provide the path.
 * {@link ClientCertificateAuthenticationOptions} can be constructed using
 * {@link #builder()}. Instances of this class are immutable once constructed.
 *
 * @author Mark Paluch
 * @author Andy Lintner
 * @since 2.3
 * @see ClientCertificateAuthenticationOptions
 * @see #builder()
 */
public class ClientCertificateAuthenticationOptions {

	public static final String DEFAULT_CERT_PATH = "cert";


	/**
	 * Path of the cert authentication method mount.
	 */
	private final String path;

	/**
	 * Named certificate role to authenticate against. Can be {@literal null}.
	 */
	private final @Nullable String role;


	private ClientCertificateAuthenticationOptions(String path, @Nullable String role) {
		this.path = path;
		this.role = role;
	}


	/**
	 * @return a new {@link ClientCertificateAuthenticationOptionsBuilder}.
	 */
	public static ClientCertificateAuthenticationOptionsBuilder builder() {
		return new ClientCertificateAuthenticationOptionsBuilder();
	}


	/**
	 * @return the path of the azure authentication method mount.
	 */
	public String getPath() {
		return this.path;
	}

	/**
	 * @return the optional named certificate role to authenticate against.
	 * @since 2.3.4
	 */
	public @Nullable String getRole() {
		return this.role;
	}


	/**
	 * Builder for {@link ClientCertificateAuthenticationOptions}.
	 */
	public static class ClientCertificateAuthenticationOptionsBuilder {

		private String path = DEFAULT_CERT_PATH;

		private @Nullable String role;


		ClientCertificateAuthenticationOptionsBuilder() {
		}


		/**
		 * Configure the mount path, defaults to {@literal azure}.
		 * @param path must not be empty or {@literal null}.
		 * @return this builder.
		 */
		public ClientCertificateAuthenticationOptionsBuilder path(String path) {

			Assert.hasText(path, "Path must not be empty");

			this.path = path;
			return this;
		}

		/**
		 * Configure the named certificate role to authenticate against.
		 * @param name must not be empty or {@literal null}.
		 * @return this builder.
		 * @since 2.3.4
		 */
		public ClientCertificateAuthenticationOptionsBuilder role(String name) {

			Assert.hasText(name, "Role must not be empty");

			this.role = name;
			return this;
		}

		/**
		 * Build a new {@link ClientCertificateAuthenticationOptions} instance.
		 * @return a new {@link ClientCertificateAuthenticationOptions}.
		 */
		public ClientCertificateAuthenticationOptions build() {
			return new ClientCertificateAuthenticationOptions(this.path, this.role);
		}

	}

}
