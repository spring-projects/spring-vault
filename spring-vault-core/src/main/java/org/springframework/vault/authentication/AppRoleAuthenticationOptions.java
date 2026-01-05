/*
 * Copyright 2016-present the original author or authors.
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
import org.springframework.vault.authentication.AppRoleTokens.AbsentSecretId;
import org.springframework.vault.authentication.AppRoleTokens.Provided;
import org.springframework.vault.authentication.AppRoleTokens.Pull;
import org.springframework.vault.support.VaultToken;

/**
 * Authentication options for {@link AppRoleAuthentication}.
 * <p>Authentication options provide the path, roleId and pull/push mode.
 * {@link AppRoleAuthentication} can be constructed using {@link #builder()}.
 * Instances of this class are immutable once constructed.
 *
 * @author Mark Paluch
 * @author Vincent Le Nair
 * @author Christophe Tafani-Dereeper
 * @see AppRoleAuthentication
 * @see #builder()
 */
public class AppRoleAuthenticationOptions {

	public static final String DEFAULT_APPROLE_AUTHENTICATION_PATH = "approle";


	/**
	 * Path of the approle authentication method mount.
	 */
	private final String path;

	/**
	 * The RoleId.
	 */
	private final RoleId roleId;

	/**
	 * The Bind SecretId.
	 */
	private final SecretId secretId;

	/**
	 * Role name used to get roleId and secretID
	 */
	@Nullable
	private final String appRole;

	/**
	 * Unwrapping endpoint to cater for functionality across various Vault versions.
	 */
	private final UnwrappingEndpoints unwrappingEndpoints;


	private AppRoleAuthenticationOptions(String path, RoleId roleId, SecretId secretId, @Nullable String appRole,
			UnwrappingEndpoints unwrappingEndpoints) {
		this.path = path;
		this.roleId = roleId;
		this.secretId = secretId;
		this.appRole = appRole;
		this.unwrappingEndpoints = unwrappingEndpoints;
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
		return this.path;
	}

	/**
	 * @return the RoleId.
	 */
	public RoleId getRoleId() {
		return this.roleId;
	}

	/**
	 * @return the bound SecretId.
	 */
	public SecretId getSecretId() {
		return this.secretId;
	}

	/**
	 * @return the bound AppRole.
	 * @since 1.1
	 */
	@Nullable
	public String getAppRole() {
		return this.appRole;
	}

	/**
	 * @return the endpoint configuration.
	 * @since 2.2
	 */
	public UnwrappingEndpoints getUnwrappingEndpoints() {
		return this.unwrappingEndpoints;
	}


	/**
	 * Builder for {@link AppRoleAuthenticationOptions}.
	 */
	public static class AppRoleAuthenticationOptionsBuilder {

		private String path = DEFAULT_APPROLE_AUTHENTICATION_PATH;

		@Nullable
		private String providedRoleId;

		@Nullable
		private RoleId roleId;

		@Nullable
		private String providedSecretId;

		@Nullable
		private SecretId secretId;

		@Nullable
		private String appRole;

		private UnwrappingEndpoints unwrappingEndpoints = UnwrappingEndpoints.SysWrapping;


		AppRoleAuthenticationOptionsBuilder() {
		}


		/**
		 * Configure the mount path.
		 * @param path must not be empty or {@literal null}.
		 * @return this builder.
		 * @see #DEFAULT_APPROLE_AUTHENTICATION_PATH
		 */
		public AppRoleAuthenticationOptionsBuilder path(String path) {
			Assert.hasText(path, "Path must not be empty");
			this.path = path;
			return this;
		}

		/**
		 * Configure the RoleId.
		 * @param roleId must not be empty or {@literal null}.
		 * @return this builder.
		 * @since 2.0
		 */
		public AppRoleAuthenticationOptionsBuilder roleId(RoleId roleId) {
			Assert.notNull(roleId, "RoleId must not be null");
			this.roleId = roleId;
			return this;
		}

		/**
		 * Configure a {@code secretId}.
		 * @param secretId must not be empty or {@literal null}.
		 * @return this builder.
		 * @since 2.0
		 */
		public AppRoleAuthenticationOptionsBuilder secretId(SecretId secretId) {
			Assert.notNull(secretId, "SecretId must not be null");
			this.secretId = secretId;
			return this;
		}

		/**
		 * Configure a {@code appRole}.
		 * @param appRole must not be empty or {@literal null}.
		 * @return this builder.
		 * @since 1.1
		 */
		public AppRoleAuthenticationOptionsBuilder appRole(String appRole) {
			Assert.hasText(appRole, "AppRole must not be empty");
			this.appRole = appRole;
			return this;
		}

		/**
		 * Configure the {@link UnwrappingEndpoints} to use.
		 * @param endpoints must not be {@literal null}.
		 * @return this builder
		 * @since 2.2
		 */
		public AppRoleAuthenticationOptionsBuilder unwrappingEndpoints(UnwrappingEndpoints endpoints) {
			Assert.notNull(endpoints, "UnwrappingEndpoints must not be empty");
			this.unwrappingEndpoints = endpoints;
			return this;
		}

		/**
		 * Build a new {@link AppRoleAuthenticationOptions} instance. Requires
		 * {@link #roleId(RoleId)} for push mode or {@link #appRole(String)} and
		 * {@link #secretId SecretId.pull(VaultToken)} for pull mode to be configured.
		 * @return a new {@link AppRoleAuthenticationOptions}.
		 */
		public AppRoleAuthenticationOptions build() {
			Assert.hasText(this.path, "Path must not be empty");
			if (this.secretId == null) {
				if (this.providedSecretId != null) {
					secretId(SecretId.provided(this.providedSecretId));
				} else {
					secretId(SecretId.absent());
				}
			}

			if (this.roleId == null) {
				Assert.notNull(this.providedRoleId,
						"AppRole authentication configured for pull mode. Role Identifier must be provided.");
				roleId(RoleId.provided(this.providedRoleId));
			}

			if (this.roleId instanceof Pull || this.secretId instanceof Pull) {
				Assert.notNull(this.appRole,
						"AppRole authentication configured for pull mode. AppRole must not be null.");
			}

			Assert.notNull(this.roleId, "RoleId must not be null");
			Assert.notNull(this.secretId, "SecretId must not be null");
			return new AppRoleAuthenticationOptions(this.path, this.roleId, this.secretId, this.appRole,
					this.unwrappingEndpoints);
		}

	}


	/**
	 * RoleId type encapsulating how the roleId is actually obtained. Provides
	 * factory methods to obtain a {@link RoleId} by wrapping, pull-mode or whether
	 * to use a string literal.
	 *
	 * @since 2.0
	 */
	public interface RoleId {

		/**
		 * Create a {@link RoleId} object that obtains its value from unwrapping a
		 * response using the {@link VaultToken initial token} from a Cubbyhole.
		 * @param initialToken must not be {@literal null}.
		 * @return {@link RoleId} object that obtains its value from unwrapping a
		 * response using the {@link VaultToken initial token}.
		 * @see org.springframework.vault.client.VaultResponses#unwrap(String, Class)
		 */
		static RoleId wrapped(VaultToken initialToken) {
			Assert.notNull(initialToken, "Initial token must not be null");
			return new AppRoleTokens.Wrapped(initialToken);
		}

		/**
		 * Create a {@link RoleId} that obtains its value using pull-mode, specifying a
		 * {@link VaultToken initial token}. The token policy must allow reading the
		 * roleId from {@code auth/approle/role/(role-name)/role-id}.
		 * @param initialToken must not be {@literal null}.
		 * @return {@link RoleId} that obtains its value using pull-mode.
		 */
		static RoleId pull(VaultToken initialToken) {
			Assert.notNull(initialToken, "Initial token must not be null");
			return new AppRoleTokens.Pull(initialToken);
		}

		/**
		 * Create a {@link RoleId} that encapsulates a static {@code roleId}.
		 * @param roleId must not be {@literal null} or empty.
		 * @return {@link RoleId} that encapsulates a static {@code roleId}.
		 */
		static RoleId provided(String roleId) {
			Assert.hasText(roleId, "RoleId must not be null or empty");
			return new Provided(roleId);
		}

	}


	/**
	 * SecretId type encapsulating how the secretId is actually obtained. Provides
	 * factory methods to obtain a {@link SecretId} by wrapping, pull-mode or
	 * whether to use a string literal.
	 *
	 * @since 2.0
	 */
	public interface SecretId {

		/**
		 * Create a {@link SecretId} object that obtains its value from unwrapping a
		 * response using the {@link VaultToken initial token} from a Cubbyhole.
		 * @param initialToken must not be {@literal null}.
		 * @return {@link SecretId} object that obtains its value from unwrapping a
		 * response using the {@link VaultToken initial token}.
		 * @see org.springframework.vault.client.VaultResponses#unwrap(String, Class)
		 */
		static SecretId wrapped(VaultToken initialToken) {
			Assert.notNull(initialToken, "Initial token must not be null");
			return new AppRoleTokens.Wrapped(initialToken);
		}

		/**
		 * Create a {@link SecretId} that obtains its value using pull-mode, specifying
		 * a {@link VaultToken initial token}. The token policy must allow reading the
		 * SecretId from {@code auth/approle/role/(role-name)/secret-id}.
		 * @param initialToken must not be {@literal null}.
		 * @return {@link SecretId} that obtains its value using pull-mode.
		 */
		static SecretId pull(VaultToken initialToken) {
			Assert.notNull(initialToken, "Initial token must not be null");
			return new AppRoleTokens.Pull(initialToken);
		}

		/**
		 * Create a {@link SecretId} that encapsulates a static {@code secretId}.
		 * @param secretId must not be {@literal null} or empty.
		 * @return {@link SecretId} that encapsulates a static {@code SecretId}.
		 */
		static SecretId provided(String secretId) {
			Assert.hasText(secretId, "SecretId must not be null or empty");
			return new Provided(secretId);
		}

		/**
		 * Create a {@link SecretId} that represents an absent secretId. Using this
		 * object will not send a secretId during AppRole login.
		 * @return a {@link SecretId} that represents an absent secretId
		 */
		static SecretId absent() {
			return AbsentSecretId.ABSENT_SECRET_ID;
		}

	}

}
