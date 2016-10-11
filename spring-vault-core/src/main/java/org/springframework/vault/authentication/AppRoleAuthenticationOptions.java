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

import org.springframework.util.Assert;

/**
 * Authentication options for {@link AppRoleAuthentication}.
 * <p>
 * Authentication options provide the path, roleId and pull/push mode. {@link AppRoleAuthentication} can be
 * constructed using {@link #builder()}. Instances of this class are immutable once constructed.
 * 
 * @author Mark Paluch
 * @see AppRoleAuthentication
 * @see #builder()
 */
public class AppRoleAuthenticationOptions {

	public final static String DEFAULT_APPROLE_AUTHENTICATION_PATH = "approle";

	/**
	 * Path of the apprile authentication backend mount.
	 */
	private final String path;

	/**
	 * The RoleId.
	 */
	private final String roleId;

	/**
	 * The Bind SecretId.
	 */
	private final String secretId;

	private AppRoleAuthenticationOptions(String path, String roleId, String secretId) {

		this.path = path;
		this.roleId = roleId;
		this.secretId = secretId;
	}

	/**
	 * @return a new {@link AppRoleAuthenticationOptionsBuilder}.
	 */
	public static AppRoleAuthenticationOptionsBuilder builder() {
		return new AppRoleAuthenticationOptionsBuilder();
	}

	/**
	 * @return the mount path.
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return the RoleId.
	 */
	public String getRoleId() {
		return roleId;
	}

	/**
	 * @return the bound SecretId.
	 */
	public String getSecretId() {
		return secretId;
	}

	/**
	 * Builder for {@link AppRoleAuthenticationOptions}.
	 */
	public static class AppRoleAuthenticationOptionsBuilder {

		private String path = DEFAULT_APPROLE_AUTHENTICATION_PATH;

		private String roleId;

		private String secretId;

		AppRoleAuthenticationOptionsBuilder() {}

		/**
		 * Configure the mount path.
		 * 
		 * @param path must not be empty or {@literal null}.
		 * @return {@code this} {@link AppRoleAuthenticationOptionsBuilder}.
		 * @see #DEFAULT_APPROLE_AUTHENTICATION_PATH
		 */
		public AppRoleAuthenticationOptionsBuilder path(String path) {

			Assert.hasText(path, "Path must not be empty");

			this.path = path;
			return this;
		}

		/**
		 * Configure the RoleId.
		 *
		 * @param roleId must not be empty or {@literal null}.
		 * @return {@code this} {@link AppRoleAuthenticationOptionsBuilder}.
		 */
		public AppRoleAuthenticationOptionsBuilder roleId(String roleId) {

			Assert.hasText(roleId, "RoleId must not be empty");

			this.roleId = roleId;
			return this;
		}

		/**
		 * Configure a {@code secretId}.
		 *
		 * @param secretId must not be empty or {@literal null}.
		 * @return {@code this} {@link AppRoleAuthenticationOptionsBuilder}.
		 */
		public AppRoleAuthenticationOptionsBuilder secretId(String secretId) {

			Assert.hasText(secretId, "SecretId must not be empty");

			this.secretId = secretId;
			return this;
		}

		/**
		 * Build a new {@link AppRoleAuthenticationOptions} instance. Requires {@link #roleId(String)} to be configured.
		 * 
		 * @return a new {@link AppRoleAuthenticationOptions}.
		 */
		public AppRoleAuthenticationOptions build() {

			Assert.hasText(path, "Path must not be empty");
			Assert.notNull(roleId, "RoleId must not be null");

			return new AppRoleAuthenticationOptions(path, roleId, secretId);
		}
	}
}
