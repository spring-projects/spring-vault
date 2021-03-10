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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

/**
 * Authentication options for {@link GcpIamCredentialsAuthentication}.
 * <p/>
 * Authentication options provide the path, a {@link GoogleCredentialsSupplier}, role and
 * JWT expiry for GCP IAM authentication. Instances of this class are immutable once
 * constructed.
 *
 * @author Andreas Gebauer
 * @author Magnus Jungsbluth
 * @see GcpIamCredentialsAuthentication
 * @see #builder()
 * @since 2.3.2
 */
public class GcpIamCredentialsAuthenticationOptions extends GcpIamAuthenticationSupport {

	public static final String DEFAULT_GCP_AUTHENTICATION_PATH = "gcp";

	/**
	 * Provide the {@link GoogleCredentials}.
	 */
	private final GoogleCredentialsSupplier credentialSupplier;

	/**
	 * Provide the service account id to use as sub/iss claims.
	 */
	private final GoogleCredentialsAccountIdAccessor serviceAccountIdAccessor;

	private GcpIamCredentialsAuthenticationOptions(String path, GoogleCredentialsSupplier credentialSupplier,
			String role, Duration jwtValidity, Clock clock,
			GoogleCredentialsAccountIdAccessor serviceAccountIdAccessor) {

		super(path, role, jwtValidity, clock);
		this.credentialSupplier = credentialSupplier;
		this.serviceAccountIdAccessor = serviceAccountIdAccessor;
	}

	/**
	 * @return a new {@link GcpIamCredentialsAuthenticationOptionsBuilder}.
	 */
	public static GcpIamCredentialsAuthenticationOptionsBuilder builder() {
		return new GcpIamCredentialsAuthenticationOptionsBuilder();
	}

	/**
	 * @return the {@link GoogleCredentials} supplier.
	 */
	public GoogleCredentialsSupplier getCredentialSupplier() {
		return this.credentialSupplier;
	}

	/**
	 * @return the service account id to use as sub/iss claims.
	 */
	public GoogleCredentialsAccountIdAccessor getServiceAccountIdAccessor() {
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
		private GoogleCredentialsSupplier credentialsSupplier;

		private Duration jwtValidity = Duration.ofMinutes(15);

		private Clock clock = Clock.systemDefaultZone();

		private GoogleCredentialsAccountIdAccessor serviceAccountIdAccessor = DefaultGoogleCredentialsAccessors.INSTANCE;

		GcpIamCredentialsAuthenticationOptionsBuilder() {
		}

		/**
		 * Configure the mount path, defaults to {@literal aws}.
		 * @param path must not be empty or {@literal null}.
		 * @return {@code this} {@link GcpIamCredentialsAuthenticationOptionsBuilder}.
		 */
		public GcpIamCredentialsAuthenticationOptionsBuilder path(String path) {

			Assert.hasText(path, "Path must not be empty");

			this.path = path;
			return this;
		}

		/**
		 * Configure static Google credentials, required to create a signed JWT. Either
		 * use static credentials or provide a
		 * {@link #credentialsSupplier(GoogleCredentialsSupplier) credentials provider}.
		 * @param credentials must not be {@literal null}.
		 * @return {@code this} {@link GcpIamCredentialsAuthenticationOptionsBuilder}.
		 * @see #credentialsSupplier(GoogleCredentialsSupplier)
		 */
		public GcpIamCredentialsAuthenticationOptionsBuilder credentials(GoogleCredentials credentials) {

			Assert.notNull(credentials, "ServiceAccountCredentials must not be null");

			return credentialsSupplier(() -> credentials);
		}

		/**
		 * Configure a {@link GoogleCredentialsSupplier}, required to create a signed JWT.
		 * Alternatively, configure static {@link #credentials(GoogleCredentials)
		 * credentials}.
		 * @param credentialsSupplier must not be {@literal null}.
		 * @return {@code this} {@link GcpIamCredentialsAuthenticationOptionsBuilder}.
		 * @see #credentials(GoogleCredentials)
		 */
		public GcpIamCredentialsAuthenticationOptionsBuilder credentialsSupplier(
				GoogleCredentialsSupplier credentialsSupplier) {

			Assert.notNull(credentialsSupplier, "GcpServiceAccountCredentialsSupplier must not be null");

			this.credentialsSupplier = credentialsSupplier;
			return this;
		}

		/**
		 * Configure an explicit service account id to use in GCP IAM calls. If none is
		 * configured, falls back to using {@link ServiceAccountCredentials#getAccount()}.
		 * @param serviceAccountId the service account id (email) to use
		 * @return {@code this} {@link GcpIamCredentialsAuthenticationOptionsBuilder}.
		 * @since 2.1
		 */
		public GcpIamCredentialsAuthenticationOptionsBuilder serviceAccountId(String serviceAccountId) {

			Assert.notNull(serviceAccountId, "Service account id may not be null");

			return serviceAccountIdAccessor((GoogleCredentials credentials) -> serviceAccountId);
		}

		/**
		 * Configure an {@link GoogleCredentialsAccountIdAccessor} to obtain the service
		 * account id used in GCP IAM calls. If none is configured, falls back to using
		 * {@link ServiceAccountCredentials#getAccount()}.
		 * @param serviceAccountIdAccessor the service account id provider to use
		 * @return {@code this} {@link GcpIamCredentialsAuthenticationOptionsBuilder}.
		 * @see GoogleCredentialsAccountIdAccessor
		 */
		GcpIamCredentialsAuthenticationOptionsBuilder serviceAccountIdAccessor(
				GoogleCredentialsAccountIdAccessor serviceAccountIdAccessor) {

			Assert.notNull(serviceAccountIdAccessor, "GcpServiceAccountIdAccessor must not be null");

			this.serviceAccountIdAccessor = serviceAccountIdAccessor;
			return this;
		}

		/**
		 * Configure the name of the role against which the login is being attempted.
		 * @param role must not be empty or {@literal null}.
		 * @return {@code this} {@link GcpIamCredentialsAuthenticationOptionsBuilder}.
		 */
		public GcpIamCredentialsAuthenticationOptionsBuilder role(String role) {

			Assert.hasText(role, "Role must not be null or empty");

			this.role = role;
			return this;
		}

		/**
		 * Configure the {@link Duration} for the JWT expiration. This defaults to 15
		 * minutes and cannot be more than a hour.
		 * @param jwtValidity must not be {@literal null}.
		 * @return {@code this} {@link GcpIamCredentialsAuthenticationOptionsBuilder}.
		 */
		public GcpIamCredentialsAuthenticationOptionsBuilder jwtValidity(Duration jwtValidity) {

			Assert.hasText(this.role, "JWT validity duration must not be null");

			this.jwtValidity = jwtValidity;
			return this;
		}

		/**
		 * Configure the {@link Clock} used to calculate epoch seconds until the JWT
		 * expiration.
		 * @param clock must not be {@literal null}.
		 * @return {@code this} {@link GcpIamCredentialsAuthenticationOptionsBuilder}.
		 */
		public GcpIamCredentialsAuthenticationOptionsBuilder clock(Clock clock) {

			Assert.hasText(this.role, "Clock must not be null");

			this.clock = clock;
			return this;
		}

		/**
		 * Build a new {@link GcpIamCredentialsAuthenticationOptions} instance.
		 * @return a new {@link GcpIamCredentialsAuthenticationOptions}.
		 */
		public GcpIamCredentialsAuthenticationOptions build() {

			Assert.notNull(this.credentialsSupplier, "GoogleCredentialsSupplier must not be null");
			Assert.notNull(this.role, "Role must not be null");

			return new GcpIamCredentialsAuthenticationOptions(this.path, this.credentialsSupplier, this.role,
					this.jwtValidity, this.clock, this.serviceAccountIdAccessor);
		}

	}

}
