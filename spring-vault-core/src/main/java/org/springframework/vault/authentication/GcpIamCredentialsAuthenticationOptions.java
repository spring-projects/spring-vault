/*
 * Copyright 2021 the original author or authors.
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

import java.time.Clock;
import java.time.Duration;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.core.ApiClock;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

public class GcpIamCredentialsAuthenticationOptions {

	public static final String DEFAULT_GCP_AUTHENTICATION_PATH = "gcp";

	/**
	 * Path of the gcp authentication backend mount.
	 */
	private final String path;

	private final GcpCredentialsSupplier credentialSupplier;

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
	 * {@link ApiClock} to calculate JWT expiration.
	 */
	private final Clock clock;

	/**
	 * Provide the service account id to use as sub/iss claims.
	 */
	private final GcpCredentialsAccountIdAccessor serviceAccountIdAccessor;

	private GcpIamCredentialsAuthenticationOptions(String path, GcpCredentialsSupplier credentialSupplier, String role,
			Duration jwtValidity, Clock clock, GcpCredentialsAccountIdAccessor serviceAccountIdSupplier) {

		this.path = path;
		this.credentialSupplier = credentialSupplier;
		this.role = role;
		this.jwtValidity = jwtValidity;
		this.clock = clock;
		this.serviceAccountIdAccessor = serviceAccountIdSupplier;
	}

	/**
	 * @return a new
	 * {@link GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder}.
	 */
	public static GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder builder() {
		return new GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder();
	}

	/**
	 * @return the path of the gcp authentication backend mount.
	 */
	public String getPath() {
		return this.path;
	}

	/**
	 * @return the gcp {@link Credential} supplier.
	 */
	public GcpCredentialsSupplier getCredentialSupplier() {
		return this.credentialSupplier;
	}

	/**
	 * @return name of the role against which the login is being attempted.
	 */
	public String getRole() {
		return this.role;
	}

	/**
	 * @return {@link Duration} of the JWT to generate.
	 */
	public Duration getJwtValidity() {
		return this.jwtValidity;
	}

	/**
	 * @return {@link Clock} used to calculate epoch seconds until the JWT expires.
	 */
	public Clock getClock() {
		return this.clock;
	}

	/**
	 * @return the service account id to use as sub/iss claims.
	 * @since 2.1
	 */
	public GcpCredentialsAccountIdAccessor getServiceAccountIdAccessor() {
		return this.serviceAccountIdAccessor;
	}

	/**
	 * Builder for {@link GcpIamCredentialsAuthenticationOptions}.
	 */
	public static class GcpIamCredentialsAuthenticationOptionsBuilder {

		private String path = DEFAULT_GCP_AUTHENTICATION_PATH;

		@Nullable
		private String role;

		@Nullable
		private GcpCredentialsSupplier credentialsSupplier;

		private Duration jwtValidity = Duration.ofMinutes(15);

		private Clock clock = Clock.systemDefaultZone();

		private GcpCredentialsAccountIdAccessor serviceAccountIdAccessor = DefaultGcpCredentialsAccessors.INSTANCE;

		GcpIamCredentialsAuthenticationOptionsBuilder() {
		}

		/**
		 * Configure the mount path, defaults to {@literal aws}.
		 * @param path must not be empty or {@literal null}.
		 * @return {@code this}
		 * {@link GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder}.
		 */
		public GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder path(String path) {

			Assert.hasText(path, "Path must not be empty");

			this.path = path;
			return this;
		}

		/**
		 * Configure static Google credentials, required to create a signed JWT. Either
		 * use static credentials or provide a
		 * {@link #credentialsSupplier(GcpCredentialsSupplier) credentials provider}.
		 * @param credentials must not be {@literal null}.
		 * @return {@code this}
		 * {@link GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder}.
		 * @see #credentialsSupplier(GcpCredentialsSupplier)
		 */
		public GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder credentials(
				GoogleCredentials credentials) {

			Assert.notNull(credentials, "ServiceAccountCredentials must not be null");

			return credentialsSupplier(() -> credentials);
		}

		/**
		 * Configure a {@link GcpCredentialsSupplier}, required to create a signed JWT.
		 * Alternatively, configure static {@link #credentials(GoogleCredentials)
		 * credentials}.
		 * @param credentialsSupplier must not be {@literal null}.
		 * @return {@code this}
		 * {@link GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder}.
		 * @see #credentials(GoogleCredentials)
		 */
		public GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder credentialsSupplier(
				GcpCredentialsSupplier credentialsSupplier) {

			Assert.notNull(credentialsSupplier, "GcpServiceAccountCredentialsSupplier must not be null");

			this.credentialsSupplier = credentialsSupplier;
			return this;
		}

		/**
		 * Configure an explicit service account id to use in GCP IAM calls. If none is
		 * configured, falls back to using {@link ServiceAccountCredentials#getAccount()}.
		 * @param serviceAccountId the service account id (email) to use
		 * @return {@code this}
		 * {@link GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder}.
		 * @since 2.1
		 */
		public GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder serviceAccountId(
				String serviceAccountId) {

			Assert.notNull(serviceAccountId, "Service account id may not be null");

			return serviceAccountIdAccessor((GoogleCredentials credentials) -> serviceAccountId);
		}

		/**
		 * Configure an {@link GcpCredentialsAccountIdAccessor} to obtain the service
		 * account id used in GCP IAM calls. If none is configured, falls back to using
		 * {@link ServiceAccountCredentials#getAccount()}.
		 * @param serviceAccountIdAccessor the service account id provider to use
		 * @return {@code this}
		 * {@link GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder}.
		 * @see GcpCredentialsAccountIdAccessor
		 * @since 2.1
		 */
		GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder serviceAccountIdAccessor(
				GcpCredentialsAccountIdAccessor serviceAccountIdAccessor) {

			Assert.notNull(serviceAccountIdAccessor, "GcpServiceAccountIdAccessor must not be null");

			this.serviceAccountIdAccessor = serviceAccountIdAccessor;
			return this;
		}

		/**
		 * Configure the name of the role against which the login is being attempted.
		 * @param role must not be empty or {@literal null}.
		 * @return {@code this}
		 * {@link GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder}.
		 */
		public GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder role(String role) {

			Assert.hasText(role, "Role must not be null or empty");

			this.role = role;
			return this;
		}

		/**
		 * Configure the {@link Duration} for the JWT expiration. This defaults to 15
		 * minutes and cannot be more than a hour.
		 * @param jwtValidity must not be {@literal null}.
		 * @return {@code this}
		 * {@link GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder}.
		 */
		public GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder jwtValidity(
				Duration jwtValidity) {

			Assert.hasText(this.role, "JWT validity duration must not be null");

			this.jwtValidity = jwtValidity;
			return this;
		}

		/**
		 * Configure the {@link Clock} used to calculate epoch seconds until the JWT
		 * expiration.
		 * @param clock must not be {@literal null}.
		 * @return {@code this}
		 * {@link GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder}.
		 */
		public GcpIamCredentialsAuthenticationOptions.GcpIamCredentialsAuthenticationOptionsBuilder clock(Clock clock) {

			Assert.hasText(this.role, "Clock must not be null");

			this.clock = clock;
			return this;
		}

		/**
		 * Build a new {@link GcpIamCredentialsAuthenticationOptions} instance.
		 * @return a new {@link GcpIamCredentialsAuthenticationOptions}.
		 */
		public GcpIamCredentialsAuthenticationOptions build() {

			Assert.notNull(this.credentialsSupplier, "GcpServiceAccountCredentialsSupplier must not be null");
			Assert.notNull(this.role, "Role must not be null");

			return new GcpIamCredentialsAuthenticationOptions(this.path, this.credentialsSupplier, this.role,
					this.jwtValidity, this.clock, this.serviceAccountIdAccessor);
		}

	}

}
