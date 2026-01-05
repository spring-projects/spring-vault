/*
 * Copyright 2019-present the original author or authors.
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
import java.util.function.Supplier;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Authentication options for {@link PcfAuthentication}.
 * <p>Authentication options provide the path, {@link Clock} and instance
 * key/instance certificate {@link Supplier}s. {@link PcfAuthenticationOptions}
 * can be constructed using {@link #builder()}. Instances of this class are
 * immutable once constructed.
 * <p>Defaults to platform-default instance certificate/key by resolving
 * {@code CF_INSTANCE_CERT} and {@code CF_INSTANCE_KEY} env variables.
 *
 * @author Mark Paluch
 * @see CredentialSupplier
 * @see ResourceCredentialSupplier
 * @see #builder()
 */
public class PcfAuthenticationOptions {

	public static final String DEFAULT_PCF_AUTHENTICATION_PATH = "pcf";


	/**
	 * Path of the pcf authentication method mount.
	 */
	private final String path;

	/**
	 * Name of the role against which the login is being attempted.
	 */
	private final String role;

	private final Clock clock;

	/**
	 * Supplier instance to obtain the instance certificate.
	 */
	private final Supplier<String> instanceCertSupplier;

	/**
	 * Supplier instance to obtain the instance key.
	 */
	private final Supplier<String> instanceKeySupplier;


	private PcfAuthenticationOptions(String path, String role, Clock clock, Supplier<String> instanceCertSupplier,
			Supplier<String> instanceKeySupplier) {
		this.path = path;
		this.role = role;
		this.clock = clock;
		this.instanceCertSupplier = instanceCertSupplier;
		this.instanceKeySupplier = instanceKeySupplier;
	}


	/**
	 * @return a new {@link PcfAuthenticationOptionsBuilder}.
	 */
	public static PcfAuthenticationOptionsBuilder builder() {
		return new PcfAuthenticationOptionsBuilder();
	}


	/**
	 * @return the path of the pcf authentication method mount.
	 */
	public String getPath() {
		return this.path;
	}

	/**
	 * @return name of the role against which the login is being attempted.
	 */
	public String getRole() {
		return this.role;
	}

	/**
	 * @return the {@link Clock}.
	 */
	public Clock getClock() {
		return this.clock;
	}

	/**
	 * @return the instance certificate {@link Supplier}.
	 */
	public Supplier<String> getInstanceCertSupplier() {
		return this.instanceCertSupplier;
	}

	/**
	 * @return the instance key {@link Supplier}.
	 */
	public Supplier<String> getInstanceKeySupplier() {
		return this.instanceKeySupplier;
	}


	/**
	 * Builder for {@link PcfAuthenticationOptions}.
	 */
	public static class PcfAuthenticationOptionsBuilder {

		private String path = DEFAULT_PCF_AUTHENTICATION_PATH;

		private Clock clock = Clock.systemUTC();

		@Nullable
		private String role;

		@Nullable
		private Supplier<String> instanceCertSupplier;

		@Nullable
		private Supplier<String> instanceKeySupplier;


		PcfAuthenticationOptionsBuilder() {
		}


		/**
		 * Configure the mount path.
		 * @param path must not be empty or {@literal null}.
		 * @return this builder.
		 * @see #DEFAULT_PCF_AUTHENTICATION_PATH
		 */
		public PcfAuthenticationOptionsBuilder path(String path) {
			Assert.hasText(path, "Path must not be empty");
			this.path = path;
			return this;
		}

		/**
		 * Configure the role.
		 * @param role name of the role against which the login is being attempted, must
		 * not be {@literal null} or empty.
		 * @return this builder.
		 */
		public PcfAuthenticationOptionsBuilder role(String role) {
			Assert.hasText(role, "Role must not be empty");
			this.role = role;
			return this;
		}

		/**
		 * Configure the {@link Clock}.
		 * @param clock must not be {@literal null}.
		 * @return this builder.
		 */
		public PcfAuthenticationOptionsBuilder clock(Clock clock) {
			Assert.notNull(clock, "Clock must not be null");
			this.clock = clock;
			return this;
		}

		/**
		 * Configure the {@link Supplier} to obtain the instance certificate.
		 * @param instanceCertSupplier the supplier, must not be {@literal null}.
		 * @return this builder.
		 * @see ResourceCredentialSupplier
		 */
		public PcfAuthenticationOptionsBuilder instanceCertificate(Supplier<String> instanceCertSupplier) {
			Assert.notNull(instanceCertSupplier, "Instance certificate supplier must not be null");
			this.instanceCertSupplier = instanceCertSupplier;
			return this;
		}

		/**
		 * Configure the {@link Supplier} to obtain the instance key.
		 * @param instanceKeySupplier the supplier, must not be {@literal null}.
		 * @return this builder.
		 * @see ResourceCredentialSupplier
		 */
		public PcfAuthenticationOptionsBuilder instanceKey(Supplier<String> instanceKeySupplier) {
			Assert.notNull(instanceKeySupplier, "Instance certificate supplier must not be null");
			this.instanceKeySupplier = instanceKeySupplier;
			return this;
		}

		/**
		 * Build a new {@link PcfAuthenticationOptions} instance.
		 * <p>Falls back to the instance certificate at {@code CF_INSTANCE_CERT} if
		 * {@link #instanceCertificate(Supplier)} is not configured respective
		 * {@code CF_INSTANCE_KEY} if {@link #instanceKey(Supplier)} is not configured.
		 * @return a new {@link PcfAuthenticationOptions}.
		 * @throws IllegalStateException if {@link #instanceCertificate(Supplier)} or
		 * {@link #instanceKey(Supplier)} are not set and the corresponding environment
		 * variable {@code CF_INSTANCE_CERT} respective {@code CF_INSTANCE_KEY} is not
		 * set.
		 */
		public PcfAuthenticationOptions build() {
			Assert.notNull(this.role, "Role must not be null");
			Supplier<String> instanceCertSupplier = this.instanceCertSupplier;
			if (instanceCertSupplier == null) {
				instanceCertSupplier = new ResourceCredentialSupplier(resolveEnvVariable("CF_INSTANCE_CERT"));
			}
			Supplier<String> instanceKeySupplier = this.instanceKeySupplier;
			if (instanceKeySupplier == null) {
				instanceKeySupplier = new ResourceCredentialSupplier(resolveEnvVariable("CF_INSTANCE_KEY"));
			}
			return new PcfAuthenticationOptions(this.path, this.role, this.clock, instanceCertSupplier,
					instanceKeySupplier);
		}

		private static String resolveEnvVariable(String name) {

			String value = System.getenv(name);

			if (ObjectUtils.isEmpty(value)) {
				throw new IllegalStateException("Environment variable %s not set".formatted(name));
			}

			return value;
		}

	}

}
