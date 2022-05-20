/*
 * Copyright 2017-2022 the original author or authors.
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

import java.net.URI;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Authentication options for {@link AwsIamAuthentication}.
 * <p>
 * Authentication options provide the path, a {@link AwsCredentialsProvider} optional role
 * and server name ({@literal Vault-AWS-IAM-Server-ID} header).
 * {@link AwsIamAuthenticationOptions} can be constructed using {@link #builder()}.
 * Instances of this class are immutable once constructed.
 *
 * @author Mark Paluch
 * @since 1.1
 * @see AwsIamAuthentication
 * @see #builder()
 */
public class AwsIamAuthenticationOptions {

	public static final String DEFAULT_AWS_AUTHENTICATION_PATH = "aws";

	/**
	 * Path of the aws authentication backend mount.
	 */
	private final String path;

	/**
	 * Credential provider.
	 */
	private final AwsCredentialsProvider credentialsProvider;

	/**
	 * Region provider.
	 */
	private final AwsRegionProvider regionProvider;

	/**
	 * Name of the role against which the login is being attempted. If role is not
	 * specified, the friendly name (i.e., role name or username) of the IAM principal
	 * authenticated. If a matching role is not found, login fails.
	 */
	@Nullable
	private final String role;

	/**
	 * Server name to mitigate risk of replay attacks, preferably set to Vault server's
	 * DNS name. Used for {@literal Vault-AWS-IAM-Server-ID} header.
	 */
	@Nullable
	private final String serverId;

	/**
	 * STS server URI.
	 */
	private final URI endpointUri;

	private AwsIamAuthenticationOptions(String path, AwsCredentialsProvider credentialsProvider,
			AwsRegionProvider regionProvider, @Nullable String role, @Nullable String serverId, URI endpointUri) {

		this.path = path;
		this.credentialsProvider = credentialsProvider;
		this.regionProvider = regionProvider;
		this.role = role;
		this.serverId = serverId;
		this.endpointUri = endpointUri;
	}

	/**
	 * @return a new {@link AwsIamAuthenticationOptionsBuilder}.
	 */
	public static AwsIamAuthenticationOptionsBuilder builder() {
		return new AwsIamAuthenticationOptionsBuilder();
	}

	/**
	 * @return the path of the aws authentication backend mount.
	 */
	public String getPath() {
		return this.path;
	}

	/**
	 * @return the credentials provider to obtain AWS credentials.
	 */
	public AwsCredentialsProvider getCredentialsProvider() {
		return this.credentialsProvider;
	}

	/**
	 * @return the region provider to obtain the AWS region to be used for computing the
	 * signature.
	 * @since 3.0
	 */
	public AwsRegionProvider getRegionProvider() {
		return this.regionProvider;
	}

	/**
	 * @return the role, may be {@literal null} if none.
	 */
	@Nullable
	public String getRole() {
		return this.role;
	}

	/**
	 * @return Server name to mitigate risk of replay attacks, preferably set to Vault
	 * server's DNS name, may be {@literal null}. Used for
	 * {@literal Vault-AWS-IAM-Server-ID} header.
	 * @since 2.0
	 */
	@Nullable
	public String getServerId() {
		return this.serverId;
	}

	/**
	 * @return Server name to mitigate risk of replay attacks, preferably set to Vault
	 * server's DNS name, may be {@literal null}.
	 * @deprecated since 2.0, renamed to {@link #getServerId()}.
	 */
	@Nullable
	@Deprecated
	public String getServerName() {
		return this.serverId;
	}

	/**
	 * @return STS server URI.
	 */
	public URI getEndpointUri() {
		return this.endpointUri;
	}

	/**
	 * Builder for {@link AwsIamAuthenticationOptions}.
	 */
	public static class AwsIamAuthenticationOptionsBuilder {

		private String path = DEFAULT_AWS_AUTHENTICATION_PATH;

		@Nullable
		private AwsCredentialsProvider credentialsProvider;

		private AwsRegionProvider regionProvider = DefaultAwsRegionProviderChain.builder().build();

		@Nullable
		private String role;

		@Nullable
		private String serverId;

		private URI endpointUri = URI.create("https://sts.amazonaws.com/");

		AwsIamAuthenticationOptionsBuilder() {
		}

		/**
		 * Configure the mount path, defaults to {@literal aws}.
		 * @param path must not be empty or {@literal null}.
		 * @return {@code this} {@link AwsIamAuthenticationOptionsBuilder}.
		 */
		public AwsIamAuthenticationOptionsBuilder path(String path) {

			Assert.hasText(path, "Path must not be empty");

			this.path = path;
			return this;
		}

		/**
		 * Configure static AWS credentials, required to calculate the signature. Either
		 * use static credentials or provide a
		 * {@link #credentialsProvider(AwsCredentialsProvider) credentials provider}.
		 * @param credentials must not be {@literal null}.
		 * @return {@code this} {@link AwsIamAuthenticationOptionsBuilder}.
		 * @see #credentialsProvider(AwsCredentialsProvider)
		 */
		public AwsIamAuthenticationOptionsBuilder credentials(AwsCredentials credentials) {

			Assert.notNull(credentials, "Credentials must not be null");

			return credentialsProvider(StaticCredentialsProvider.create(credentials));
		}

		/**
		 * Configure an {@link AwsCredentialsProvider}, required to calculate the
		 * signature. Alternatively, configure static {@link #credentials(AwsCredentials)
		 * credentials}.
		 * @param credentialsProvider must not be {@literal null}.
		 * @return {@code this} {@link AwsIamAuthenticationOptionsBuilder}.
		 * @see #credentials(AwsCredentials)
		 */
		public AwsIamAuthenticationOptionsBuilder credentialsProvider(AwsCredentialsProvider credentialsProvider) {

			Assert.notNull(credentialsProvider, "AwsCredentialsProvider must not be null");

			this.credentialsProvider = credentialsProvider;
			return this;
		}

		/**
		 * Configure an {@link AwsRegionProvider}, required to calculate the region to be
		 * used for computing the signature.
		 * @param regionProvider must not be {@literal null}.
		 * @return {@code this} {@link AwsIamAuthenticationOptionsBuilder}.
		 * @since 3.0
		 */
		public AwsIamAuthenticationOptionsBuilder regionProvider(AwsRegionProvider regionProvider) {

			Assert.notNull(regionProvider, "AwsRegionProvider must not be null");

			this.regionProvider = regionProvider;
			return this;
		}

		/**
		 * Configure the name of the role against which the login is being attempted. If
		 * role is not specified, the friendly name (i.e., role name or username) of the
		 * IAM principal authenticated. If a matching role is not found, login fails.
		 * @param role must not be empty or {@literal null}.
		 * @return {@code this} {@link AwsIamAuthenticationOptionsBuilder}.
		 */
		public AwsIamAuthenticationOptionsBuilder role(String role) {

			Assert.hasText(role, "Role must not be null or empty");

			this.role = role;
			return this;
		}

		/**
		 * Configure a server name (used for {@literal Vault-AWS-IAM-Server-ID}) that is
		 * included in the signature to mitigate the risk of replay attacks. Preferably
		 * use the Vault server DNS name.
		 * @param serverId must not be {@literal null} or empty.
		 * @return {@code this} {@link AwsIamAuthenticationOptionsBuilder}.
		 * @since 2.1
		 */
		public AwsIamAuthenticationOptionsBuilder serverId(String serverId) {

			Assert.hasText(serverId, "Server name must not be null or empty");

			this.serverId = serverId;
			return this;
		}

		/**
		 * Configure a server name that is included in the signature to mitigate the risk
		 * of replay attacks. Preferably use the Vault server DNS name.
		 * @param serverName must not be {@literal null} or empty.
		 * @return {@code this} {@link AwsIamAuthenticationOptionsBuilder}.
		 */
		public AwsIamAuthenticationOptionsBuilder serverName(String serverName) {
			return serverId(serverName);
		}

		/**
		 * Configure an endpoint URI of the STS API, defaults to
		 * {@literal https://sts.amazonaws.com/}.
		 * @param endpointUri must not be {@literal null}.
		 * @return {@code this} {@link AwsIamAuthenticationOptionsBuilder}.
		 */
		public AwsIamAuthenticationOptionsBuilder endpointUri(URI endpointUri) {

			Assert.notNull(endpointUri, "Endpoint URI must not be null");

			this.endpointUri = endpointUri;
			return this;
		}

		/**
		 * Build a new {@link AwsIamAuthenticationOptions} instance.
		 * @return a new {@link AwsIamAuthenticationOptions}.
		 */
		public AwsIamAuthenticationOptions build() {

			Assert.state(this.credentialsProvider != null, "Credentials or CredentialProvider must not be null");

			return new AwsIamAuthenticationOptions(this.path, this.credentialsProvider, this.regionProvider, this.role,
					this.serverId, this.endpointUri);
		}

	}

}
