/*
 * Copyright 2018 the original author or authors.
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

import java.time.Clock;
import java.time.Duration;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Authentication options for {@link GcpIamAuthentication}.
 * <p/>
 * Authentication options provide the path, a {@link GcpCredentialSupplier}, role and JWT
 * expiry for GCP IAM authentication. Instances of this class are immutable once
 * constructed.
 *
 * @author Mark Paluch
 * @see GcpIamAuthentication
 * @see #builder()
 * @since 2.1
 */
public class GcpIamAuthenticationOptions {

	public static final String DEFAULT_GCP_AUTHENTICATION_PATH = "gcp";

	/**
	 * Path of the gcp authentication backend mount.
	 */
	private final String path;

	private final GcpCredentialSupplier credentialSupplier;

	/**
	 * Name of the role against which the login is being attempted. If role is not
	 * specified, the friendly name (i.e., role name or username) of the IAM principal
	 * authenticated. If a matching role is not found, login fails.
	 */
	private final String role;

	/**
	 * JWT validity/expiration.
	 */
	private final Duration jwtValidity;

	/**
	 * {@link Clock} to calculate JWT expiration.
	 */
	private final Clock clock;

	private GcpIamAuthenticationOptions(String path,
			GcpCredentialSupplier credentialSupplier, String role, Duration jwtValidity,
			Clock clock) {

		this.path = path;
		this.credentialSupplier = credentialSupplier;
		this.role = role;
		this.jwtValidity = jwtValidity;
		this.clock = clock;
	}

	/**
	 * @return a new {@link GcpIamAuthenticationOptionsBuilder}.
	 */
	public static GcpIamAuthenticationOptionsBuilder builder() {
		return new GcpIamAuthenticationOptionsBuilder();
	}

	/**
	 * @return the path of the gcp authentication backend mount.
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return the gcp {@link Credential} supplier.
	 */
	public GcpCredentialSupplier getCredentialSupplier() {
		return credentialSupplier;
	}

	/**
	 * @return name of the role against which the login is being attempted.
	 */
	public String getRole() {
		return role;
	}

	/**
	 * @return {@link Duration} of the JWT to generate.
	 */
	public Duration getJwtValidity() {
		return jwtValidity;
	}

	/**
	 * @return {@link Clock} used to calculate epoch seconds until the JWT expires.
	 */
	public Clock getClock() {
		return clock;
	}

	/**
	 * Builder for {@link GcpIamAuthenticationOptions}.
	 */
	public static class GcpIamAuthenticationOptionsBuilder {

		private String path = DEFAULT_GCP_AUTHENTICATION_PATH;

		@Nullable
		private String role;

		@Nullable
		private GcpCredentialSupplier credentialSupplier;

		private Duration jwtValidity = Duration.ofMinutes(15);

		private Clock clock = Clock.systemDefaultZone();

		GcpIamAuthenticationOptionsBuilder() {
		}

		/**
		 * Configure the mount path, defaults to {@literal aws}.
		 *
		 * @param path must not be empty or {@literal null}.
		 * @return {@code this} {@link GcpIamAuthenticationOptionsBuilder}.
		 */
		public GcpIamAuthenticationOptionsBuilder path(String path) {

			Assert.hasText(path, "Path must not be empty");

			this.path = path;
			return this;
		}

		/**
		 * Configure static Google credentials, required to create a signed JWT. Either
		 * use static credentials or provide a
		 * {@link #credentialSupplier(GcpCredentialSupplier) credentials provider}.
		 *
		 * @param credential must not be {@literal null}.
		 * @return {@code this} {@link GcpIamAuthenticationOptionsBuilder}.
		 * @see #credentialSupplier(GcpCredentialSupplier)
		 */
		public GcpIamAuthenticationOptionsBuilder credential(GoogleCredential credential) {

			Assert.notNull(credential, "Credential must not be null");

			return credentialSupplier(() -> credential);
		}

		/**
		 * Configure an {@link AWSCredentialsProvider}, required to create a signed JWT.
		 * Alternatively, configure static {@link #credential(GoogleCredential)
		 * credentials}.
		 *
		 * @param credentialSupplier must not be {@literal null}.
		 * @return {@code this} {@link GcpIamAuthenticationOptionsBuilder}.
		 * @see #credential(GoogleCredential)
		 */
		public GcpIamAuthenticationOptionsBuilder credentialSupplier(
				GcpCredentialSupplier credentialSupplier) {

			Assert.notNull(credentialSupplier, "GcpCredentialSupplier must not be null");

			this.credentialSupplier = credentialSupplier;
			return this;
		}

		/**
		 * Configure the name of the role against which the login is being attempted.
		 *
		 * @param role must not be empty or {@literal null}.
		 * @return {@code this} {@link GcpIamAuthenticationOptionsBuilder}.
		 */
		public GcpIamAuthenticationOptionsBuilder role(String role) {

			Assert.hasText(role, "Role must not be null or empty");

			this.role = role;
			return this;
		}

		/**
		 * Configure the {@link Duration} for the JWT expiration. This defaults to 15
		 * minutes and cannot be more than a hour.
		 *
		 * @param jwtValidity must not be {@literal null}.
		 * @return {@code this} {@link GcpIamAuthenticationOptionsBuilder}.
		 */
		public GcpIamAuthenticationOptionsBuilder jwtValidity(Duration jwtValidity) {

			Assert.hasText(role, "JWT validity duration must not be null");

			this.jwtValidity = jwtValidity;
			return this;
		}

		/**
		 * Configure the {@link Clock} used to calculate epoch seconds until the JWT
		 * expiration.
		 *
		 * @param clock must not be {@literal null}.
		 * @return {@code this} {@link GcpIamAuthenticationOptionsBuilder}.
		 */
		public GcpIamAuthenticationOptionsBuilder clock(Clock clock) {

			Assert.hasText(role, "Clock must not be null");

			this.clock = clock;
			return this;
		}

		/**
		 * Build a new {@link GcpIamAuthenticationOptions} instance.
		 *
		 * @return a new {@link GcpIamAuthenticationOptions}.
		 */
		public GcpIamAuthenticationOptions build() {

			Assert.notNull(credentialSupplier, "GcpCredentialSupplier must not be null");
			Assert.notNull(role, "Role must not be null");

			return new GcpIamAuthenticationOptions(path, credentialSupplier, role,
					jwtValidity, clock);
		}
	}
}
