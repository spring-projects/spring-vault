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

import java.net.URI;

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
	private final String role;

	private AwsEc2AuthenticationOptions() {
		this(DEFAULT_AWS_AUTHENTICATION_PATH, DEFAULT_PKCS7_IDENTITY_DOCUMENT_URI, "");
	}

	private AwsEc2AuthenticationOptions(String path, URI identityDocumentUri, String role) {

		this.path = path;
		this.identityDocumentUri = identityDocumentUri;
		this.role = role;
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
		return path;
	}

	/**
	 * @return the {@link URI} to the AWS EC2 PKCS#7-signed identity document.
	 */
	public URI getIdentityDocumentUri() {
		return identityDocumentUri;
	}

	/**
	 * @return the role, may be {@literal null} if none.
	 */
	public String getRole() {
		return role;
	}

	/**
	 * Builder for {@link AwsEc2AuthenticationOptionsBuilder}.
	 */
	public static class AwsEc2AuthenticationOptionsBuilder {

		private String path = DEFAULT_AWS_AUTHENTICATION_PATH;
		private URI identityDocumentUri = DEFAULT_PKCS7_IDENTITY_DOCUMENT_URI;
		private String role;

		AwsEc2AuthenticationOptionsBuilder() {
		}

		/**
		 * Configure the mount path.
		 *
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
		 *
		 * @param identityDocumentUri must not be empty or {@literal null}.
		 * @return {@code this} {@link AwsEc2AuthenticationOptionsBuilder}.
		 * @see #DEFAULT_PKCS7_IDENTITY_DOCUMENT_URI
		 */
		public AwsEc2AuthenticationOptionsBuilder identityDocumentUri(
				URI identityDocumentUri) {

			this.identityDocumentUri = identityDocumentUri;
			return this;
		}

		/**
		 * Configure the name of the role against which the login is being attempted.If
		 * role is not specified, then the login endpoint looks for a role bearing the
		 * name of the AMI ID of the EC2 instance that is trying to login.
		 *
		 * @param role may be empty or {@literal null}.
		 * @return {@code this} {@link AwsEc2AuthenticationOptionsBuilder}.
		 */
		public AwsEc2AuthenticationOptionsBuilder role(String role) {

			this.role = role;
			return this;
		}

		/**
		 * Build a new {@link AwsEc2AuthenticationOptions} instance.
		 *
		 * @return a new {@link AppIdAuthenticationOptions}.
		 */
		public AwsEc2AuthenticationOptions build() {

			Assert.notNull(identityDocumentUri, "IdentityDocumentUri must not be null");

			return new AwsEc2AuthenticationOptions(path, identityDocumentUri, role);
		}
	}
}
