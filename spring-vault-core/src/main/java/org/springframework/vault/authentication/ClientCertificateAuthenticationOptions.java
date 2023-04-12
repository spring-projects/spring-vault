/*
 * Copyright 2020-2022 the original author or authors.
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
 * Authentication options for {@link ClientCertificateAuthentication}.
 * <p>
 * Authentication options provide the path. {@link ClientCertificateAuthenticationOptions}
 * can be constructed using {@link #builder()}. Instances of this class are immutable once
 * constructed.
 *
 * @author Mark Paluch
 * @since 2.3
 * @see ClientCertificateAuthenticationOptions
 * @see #builder()
 */
public class ClientCertificateAuthenticationOptions {

	public static final String DEFAULT_CERT_PATH = "cert";

	/**
	 * Path of the cert authentication backend mount.
	 */
	private final String path;

	/**
	 * Optional named certificate role to authenticate against.
	 */
	@Nullable
	private final String name;

	private ClientCertificateAuthenticationOptions(String path, String name) {
		this.path = path;
		this.name = name;
	}

	/**
	 * @return a new {@link ClientCertificateAuthenticationOptionsBuilder}.
	 */
	public static ClientCertificateAuthenticationOptionsBuilder builder() {
		return new ClientCertificateAuthenticationOptionsBuilder();
	}

	/**
	 * @return the path of the azure authentication backend mount.
	 */
	public String getPath() {
		return this.path;
	}

	/**
	 * @return the optional named certificate role to authenticate against.
	 */
	@Nullable
	public String getName() {
		return this.name;
	}

	/**
	 * Builder for {@link ClientCertificateAuthenticationOptions}.
	 */
	public static class ClientCertificateAuthenticationOptionsBuilder {

		private String path = DEFAULT_CERT_PATH;

		@Nullable
		private String name;

		ClientCertificateAuthenticationOptionsBuilder() {
		}

		/**
		 * Configure the mount path, defaults to {@literal azure}.
		 * @param path must not be empty or {@literal null}.
		 * @return {@code this} {@link ClientCertificateAuthenticationOptionsBuilder}.
		 */
		public ClientCertificateAuthenticationOptionsBuilder path(String path) {

			Assert.hasText(path, "Path must not be empty");

			this.path = path;
			return this;
		}

		/**
		 * Configure the named certificate role to authenticate against.
		 * @param name must not be empty or {@literal null}.
		 * @return {@code this} {@link ClientCertificateAuthenticationOptionsBuilder}.
		 */
		public ClientCertificateAuthenticationOptionsBuilder name(String name) {

			Assert.hasText(name, "Name must not be empty");

			this.name = name;
			return this;
		}

		/**
		 * Build a new {@link ClientCertificateAuthenticationOptions} instance.
		 * @return a new {@link ClientCertificateAuthenticationOptions}.
		 */
		public ClientCertificateAuthenticationOptions build() {
			return new ClientCertificateAuthenticationOptions(this.path, this.name);
		}

	}

}
