/*
 * Copyright 2016-2024 the original author or authors.
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
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Authentication options for {@link AwsEc2Authentication}.
 * <p>
 * Authentication options provide the path, the Identity Document URI and an optional
 * role. {@link AwsEc2AuthenticationOptions} can be constructed using {@link #builder()}.
 * Instances of this class are immutable once constructed.
 *
 * @author Mark Paluch
 * @see AwsEc2Authentication
 * @see #builder()
 */
public class AwsEc2AuthenticationOptions {

	public static final URI DEFAULT_PKCS7_IDENTITY_DOCUMENT_URI = URI
		.create("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7");

	/**
	 * @since 3.2
	 */
	public static final URI DEFAULT_IMDSV2_TOKEN_URI = URI.create("http://169.254.169.254/latest/api/token");

	public static final String DEFAULT_AWS_AUTHENTICATION_PATH = "aws-ec2";

	/**
	 * Default {@link AwsEc2AuthenticationOptions} using
	 * {@link #DEFAULT_AWS_AUTHENTICATION_PATH} and
	 * {@link #DEFAULT_PKCS7_IDENTITY_DOCUMENT_URI}.
	 */
	public static final AwsEc2AuthenticationOptions DEFAULT = new AwsEc2AuthenticationOptions();

	/**
	 * Path of the aws-ec2 authentication backend mount.
	 */
	private final String path;

	/**
	 * {@link URI} to the AWS EC2 PKCS#7-signed identity document.
	 */
	private final URI identityDocumentUri;

	/**
	 * EC2 instance role name. May be {@literal null} if none.
	 */
	@Nullable
	private final String role;

	/**
	 * Authentication nonce.
	 */
	private final Nonce nonce;

	/**
	 * IMDSv2 token TTL.
	 * @since 3.2
	 */
	private final Duration metadataTokenTtl;

	/**
	 * {@link URI} to request a token for the AWS EC2 Instance Metadata service v2.
	 * @since 3.2
	 */
	private final URI metadataTokenRequestUri;

	/**
	 * Metadata service version.
	 * @since 3.2
	 */
	private final InstanceMetadataServiceVersion version;

	private AwsEc2AuthenticationOptions() {
		this(DEFAULT_AWS_AUTHENTICATION_PATH, DEFAULT_PKCS7_IDENTITY_DOCUMENT_URI, "", Nonce.generated(),
				Duration.ofMinutes(1), DEFAULT_IMDSV2_TOKEN_URI, InstanceMetadataServiceVersion.V1);
	}

	private AwsEc2AuthenticationOptions(String path, URI identityDocumentUri, @Nullable String role, Nonce nonce,
			Duration metadataTokenTtl, URI metadataTokenRequestUri, InstanceMetadataServiceVersion version) {

		this.path = path;
		this.identityDocumentUri = identityDocumentUri;
		this.role = role;
		this.nonce = nonce;
		this.metadataTokenTtl = metadataTokenTtl;
		this.metadataTokenRequestUri = metadataTokenRequestUri;
		this.version = version;
	}

	/**
	 * @return a new {@link AwsEc2AuthenticationOptionsBuilder}.
	 */
	public static AwsEc2AuthenticationOptionsBuilder builder() {
		return new AwsEc2AuthenticationOptionsBuilder();
	}

	/**
	 * @return the path of the aws-ec2 authentication backend mount.
	 */
	public String getPath() {
		return this.path;
	}

	/**
	 * @return the {@link URI} to the AWS EC2 PKCS#7-signed identity document.
	 */
	public URI getIdentityDocumentUri() {
		return this.identityDocumentUri;
	}

	/**
	 * @return the role, may be {@literal null} if none.
	 */
	@Nullable
	public String getRole() {
		return this.role;
	}

	/**
	 * @return the configured {@link Nonce}.
	 */
	public Nonce getNonce() {
		return this.nonce;
	}

	/**
	 * @return the configured {@link InstanceMetadataServiceVersion}.
	 * @since 3.2
	 */
	public InstanceMetadataServiceVersion getVersion() {
		return version;
	}

	/**
	 * @return the configured IMDSv2 token TTL.
	 * @since 3.2
	 */
	public Duration getMetadataTokenTtl() {
		return metadataTokenTtl;
	}

	/**
	 * @return the {@link URI} to the AWS EC2 Metadata Service to obtain IMDSv2 tokens.
	 * @since 3.2
	 */
	public URI getMetadataTokenRequestUri() {
		return this.metadataTokenRequestUri;
	}

	/**
	 * Builder for {@link AwsEc2AuthenticationOptionsBuilder}.
	 */
	public static class AwsEc2AuthenticationOptionsBuilder {

		private String path = DEFAULT_AWS_AUTHENTICATION_PATH;

		private URI identityDocumentUri = DEFAULT_PKCS7_IDENTITY_DOCUMENT_URI;

		@Nullable
		private String role;

		private Nonce nonce = Nonce.generated();

		private Duration metadataTokenTtl = Duration.ofMinutes(1);

		private URI metadataTokenRequestUri = DEFAULT_IMDSV2_TOKEN_URI;

		private InstanceMetadataServiceVersion version = InstanceMetadataServiceVersion.V1;

		AwsEc2AuthenticationOptionsBuilder() {
		}

		/**
		 * Configure the mount path.
		 * @param path must not be empty or {@literal null}.
		 * @return {@code this} {@link AwsEc2AuthenticationOptionsBuilder}.
		 */
		public AwsEc2AuthenticationOptionsBuilder path(String path) {

			Assert.hasText(path, "Path must not be empty");

			this.path = path;
			return this;
		}

		/**
		 * Configure the Identity Document {@link URI}.
		 * @param identityDocumentUri must not be {@literal null}.
		 * @return {@code this} {@link AwsEc2AuthenticationOptionsBuilder}.
		 * @see #DEFAULT_PKCS7_IDENTITY_DOCUMENT_URI
		 */
		public AwsEc2AuthenticationOptionsBuilder identityDocumentUri(URI identityDocumentUri) {

			Assert.notNull(identityDocumentUri, "Identity document URI must not be null");

			this.identityDocumentUri = identityDocumentUri;
			return this;
		}

		/**
		 * Configure the name of the role against which the login is being attempted.If
		 * role is not specified, then the login endpoint looks for a role bearing the
		 * name of the AMI ID of the EC2 instance that is trying to login.
		 * @param role may be empty or {@literal null}.
		 * @return {@code this} {@link AwsEc2AuthenticationOptionsBuilder}.
		 */
		public AwsEc2AuthenticationOptionsBuilder role(@Nullable String role) {

			this.role = role;
			return this;
		}

		/**
		 * Configure a {@link Nonce} for login requests. Defaults to
		 * {@link Nonce#generated()}.
		 * @param nonce must not be {@literal null}.
		 * @return {@code this} {@link AwsEc2AuthenticationOptionsBuilder}.
		 * @since 1.1
		 */
		public AwsEc2AuthenticationOptionsBuilder nonce(Nonce nonce) {

			Assert.notNull(nonce, "Nonce must not be null");

			this.nonce = nonce;
			return this;
		}

		/**
		 * Configure the Instance Service Metadata v2 Token TTL. Defaults to 1 minute.
		 * @param ttl must not be {@literal null}.
		 * @return {@code this} {@link AwsEc2AuthenticationOptionsBuilder}.
		 * @since 3.2
		 */
		public AwsEc2AuthenticationOptionsBuilder metadataTokenTtl(Duration ttl) {

			Assert.notNull(ttl, "Duration must not be null");
			Assert.isTrue(!ttl.isNegative() && !ttl.isZero(), "Duration must not be zero or negative");

			this.metadataTokenTtl = ttl;
			return this;
		}

		/**
		 * Configure the Identity Metadata token request {@link URI}.
		 * @param metadataTokenRequestUri must not be {@literal null}.
		 * @return {@code this} {@link AwsEc2AuthenticationOptionsBuilder}.
		 * @since 3.2
		 * @see #DEFAULT_IMDSV2_TOKEN_URI
		 */
		public AwsEc2AuthenticationOptionsBuilder metadataTokenRequestUri(URI metadataTokenRequestUri) {

			Assert.notNull(metadataTokenRequestUri, "Metadata token request URI must not be null");

			this.metadataTokenRequestUri = metadataTokenRequestUri;
			return this;
		}

		/**
		 * Configure the Instance Service Metadata {@link InstanceMetadataServiceVersion
		 * version}. Defaults to {@link InstanceMetadataServiceVersion#V1}.
		 * @param version must not be {@literal null}.
		 * @return {@code this} {@link AwsEc2AuthenticationOptionsBuilder}.
		 * @since 3.2
		 */
		public AwsEc2AuthenticationOptionsBuilder version(InstanceMetadataServiceVersion version) {

			Assert.notNull(version, "Version must not be null");

			this.version = version;
			return this;
		}

		/**
		 * Build a new {@link AwsEc2AuthenticationOptions} instance.
		 * @return a new {@link AwsEc2AuthenticationOptions}.
		 */
		public AwsEc2AuthenticationOptions build() {

			Assert.notNull(this.identityDocumentUri, "IdentityDocumentUri must not be null");

			return new AwsEc2AuthenticationOptions(this.path, this.identityDocumentUri, this.role, this.nonce,
					this.metadataTokenTtl, this.metadataTokenRequestUri, this.version);
		}

	}

	/**
	 * Value object for an authentication nonce.
	 *
	 * @since 1.1
	 */
	public static class Nonce {

		private final char[] value;

		protected Nonce(char[] value) {
			this.value = value;
		}

		/**
		 * Create a new generated {@link Nonce} using {@link UUID}.
		 * @return a new generated {@link Nonce} using {@link UUID}.
		 */
		public static Nonce generated() {
			return new Generated();
		}

		/**
		 * Create a wrapped {@link Nonce} given a {@code nonce} value.
		 * @return a wrapped {@link Nonce} given for the {@code nonce} value.
		 */
		public static Nonce provided(char[] nonce) {

			Assert.notNull(nonce, "Nonce must not be null");

			return new Provided(Arrays.copyOf(nonce, nonce.length));
		}

		/**
		 * @return the nonce value.
		 */
		public char[] getValue() {
			return this.value;
		}

		static class Generated extends Nonce {

			Generated() {
				super(UUID.randomUUID().toString().toCharArray());
			}

		}

		static class Provided extends Nonce {

			Provided(char[] nonce) {
				super(nonce);
			}

		}

	}

	/**
	 * Enumeration for the Instance metadata service version.
	 *
	 * @since 3.2
	 */
	public enum InstanceMetadataServiceVersion {

		/**
		 * Request/Response (default) oriented version 1.
		 */
		V1,

		/**
		 * Session-oriented version 2.
		 */

		V2;

	}

}
