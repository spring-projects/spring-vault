/*
 * Copyright 2018-2019 the original author or authors.
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

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Authentication options for {@link GcpComputeAuthentication}.
 * <p />
 * Authentication options provide the path, role and an optional service account
 * identifier. Instances of this class are immutable once constructed.
 *
 * @author Mark Paluch
 * @see GcpComputeAuthentication
 * @see #builder()
 * @since 2.1
 */
public class GcpComputeAuthenticationOptions {

	public static final String DEFAULT_GCP_AUTHENTICATION_PATH = "gcp";

	/**
	 * Path of the gcp authentication backend mount.
	 */
	private final String path;

	/**
	 * GCE service account identifier.
	 */
	private final String serviceAccount;

	/**
	 * Name of the role against which the login is being attempted. If role is not
	 * specified, the friendly name (i.e., role name or username) of the IAM principal
	 * authenticated. If a matching role is not found, login fails.
	 */
	private final String role;

	private GcpComputeAuthenticationOptions(String path, String serviceAccount,
			String role) {

		this.path = path;
		this.serviceAccount = serviceAccount;
		this.role = role;
	}

	/**
	 * @return a new {@link GcpComputeAuthenticationOptionsBuilder}.
	 */
	public static GcpComputeAuthenticationOptionsBuilder builder() {
		return new GcpComputeAuthenticationOptionsBuilder();
	}

	/**
	 * @return the path of the gcp authentication backend mount.
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return the GCE service account identifier.
	 */
	public String getServiceAccount() {
		return serviceAccount;
	}

	/**
	 * @return name of the role against which the login is being attempted.
	 */
	public String getRole() {
		return role;
	}

	/**
	 * Builder for {@link GcpComputeAuthenticationOptions}.
	 */
	public static class GcpComputeAuthenticationOptionsBuilder {

		private String path = DEFAULT_GCP_AUTHENTICATION_PATH;

		@Nullable
		private String role;

		private String serviceAccount = "default";

		GcpComputeAuthenticationOptionsBuilder() {
		}

		/**
		 * Configure the mount path, defaults to {@literal aws}.
		 *
		 * @param path must not be empty or {@literal null}.
		 * @return {@code this} {@link GcpComputeAuthenticationOptionsBuilder}.
		 */
		public GcpComputeAuthenticationOptionsBuilder path(String path) {

			Assert.hasText(path, "Path must not be empty");

			this.path = path;
			return this;
		}

		/**
		 * Configure the service account identifier. Uses the {@code default} service
		 * account if left unconfigured.
		 *
		 * @param serviceAccount must not be empty or {@literal null}.
		 * @return {@code this} {@link GcpComputeAuthenticationOptionsBuilder}.
		 */
		public GcpComputeAuthenticationOptionsBuilder serviceAccount(String serviceAccount) {

			Assert.hasText(serviceAccount, "Service account must not be null");

			this.serviceAccount = serviceAccount;
			return this;
		}

		/**
		 * Configure the name of the role against which the login is being attempted.
		 *
		 * @param role must not be empty or {@literal null}.
		 * @return {@code this} {@link GcpComputeAuthenticationOptionsBuilder}.
		 */
		public GcpComputeAuthenticationOptionsBuilder role(String role) {

			Assert.hasText(role, "Role must not be null or empty");

			this.role = role;
			return this;
		}

		/**
		 * Build a new {@link GcpComputeAuthenticationOptions} instance.
		 *
		 * @return a new {@link GcpComputeAuthenticationOptions}.
		 */
		public GcpComputeAuthenticationOptions build() {

			Assert.notNull(role, "Role must not be null");

			return new GcpComputeAuthenticationOptions(path, serviceAccount, role);
		}
	}
}
