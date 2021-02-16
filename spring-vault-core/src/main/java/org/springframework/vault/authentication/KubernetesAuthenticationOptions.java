/*
 * Copyright 2017-2021 the original author or authors.
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

import java.util.function.Supplier;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Authentication options for {@link KubernetesAuthentication}.
 * <p>
 * Authentication options provide the path, role and jwt supplier.
 * {@link KubernetesAuthentication} can be constructed using {@link #builder()}. Instances
 * of this class are immutable once constructed.
 * <p>
 * Defaults to obtain the token from
 * {@code /var/run/secrets/kubernetes.io/serviceaccount/token} on each login.
 *
 * @author Michal Budzyn
 * @author Mark Paluch
 * @since 2.0
 * @see KubernetesAuthentication
 * @see KubernetesJwtSupplier
 * @see KubernetesServiceAccountTokenFile
 * @see #builder()
 */
public class KubernetesAuthenticationOptions {

	public static final String DEFAULT_KUBERNETES_AUTHENTICATION_PATH = "kubernetes";

	/**
	 * Path of the kubernetes authentication backend mount.
	 */
	private final String path;

	/**
	 * Name of the role against which the login is being attempted.
	 */
	private final String role;

	/**
	 * Supplier instance to obtain a service account JSON Web Tokens.
	 */
	private final Supplier<String> jwtSupplier;

	private KubernetesAuthenticationOptions(String path, String role, Supplier<String> jwtSupplier) {

		this.path = path;
		this.role = role;
		this.jwtSupplier = jwtSupplier;
	}

	/**
	 * @return a new {@link KubernetesAuthenticationOptionsBuilder}.
	 */
	public static KubernetesAuthenticationOptionsBuilder builder() {
		return new KubernetesAuthenticationOptionsBuilder();
	}

	/**
	 * @return the path of the kubernetes authentication backend mount.
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
	 * @return JSON Web Token supplier.
	 */
	public Supplier<String> getJwtSupplier() {
		return this.jwtSupplier;
	}

	/**
	 * Builder for {@link KubernetesAuthenticationOptions}.
	 */
	public static class KubernetesAuthenticationOptionsBuilder {

		private String path = DEFAULT_KUBERNETES_AUTHENTICATION_PATH;

		@Nullable
		private String role;

		@Nullable
		private Supplier<String> jwtSupplier;

		/**
		 * Configure the mount path.
		 * @param path must not be {@literal null} or empty.
		 * @return {@code this} {@link KubernetesAuthenticationOptionsBuilder}.
		 */
		public KubernetesAuthenticationOptionsBuilder path(String path) {

			Assert.hasText(path, "Path must not be empty");

			this.path = path;
			return this;
		}

		/**
		 * Configure the role.
		 * @param role name of the role against which the login is being attempted, must
		 * not be {@literal null} or empty.
		 * @return {@code this} {@link KubernetesAuthenticationOptionsBuilder}.
		 */
		public KubernetesAuthenticationOptionsBuilder role(String role) {

			Assert.hasText(role, "Role must not be empty");

			this.role = role;
			return this;
		}

		/**
		 * Configure the {@link Supplier} to obtain a Kubernetes authentication token.
		 * @param jwtSupplier the supplier, must not be {@literal null}.
		 * @return {@code this} {@link KubernetesAuthenticationOptionsBuilder}.
		 * @see KubernetesJwtSupplier
		 */
		public KubernetesAuthenticationOptionsBuilder jwtSupplier(Supplier<String> jwtSupplier) {

			Assert.notNull(jwtSupplier, "JwtSupplier must not be null");

			this.jwtSupplier = jwtSupplier;
			return this;
		}

		/**
		 * Build a new {@link KubernetesAuthenticationOptions} instance.
		 * @return a new {@link KubernetesAuthenticationOptions}.
		 */
		public KubernetesAuthenticationOptions build() {

			Assert.notNull(this.role, "Role must not be null");

			return new KubernetesAuthenticationOptions(this.path, this.role,
					this.jwtSupplier == null ? new KubernetesServiceAccountTokenFile() : this.jwtSupplier);
		}

	}

}
