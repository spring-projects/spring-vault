/*
 * Copyright 2016-2017 the original author or authors.
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
 * Authentication options for {@link KubeAuthentication}.
 * <p>
 * Authentication options provide the path, role and jwt supplier.
 * {@link KubeAuthentication} can be constructed using {@link #builder()}. Instances of
 * this class are immutable once constructed.
 *
 * @author Michal Budzyn
 * @see KubeAuthentication
 * @see #builder()
 */
public class KubeAuthenticationOptions {

	public static final String DEFAULT_KUBERNETES_AUTHENTICATION_PATH = "kubernetes";

	/**
	 * Path of the kubernetes authentication backend mount.
	 */
	private final String path;

	/**
	 * The Role.
	 */
	private final String role;

	/**
	 * {@link KubeJwtSupplier} instance to obtain a service account JSON Web Tokens.
	 */
	private final KubeJwtSupplier jwtSupplier;

	private KubeAuthenticationOptions(String path, String role,
			KubeJwtSupplier jwtSupplier) {

		this.path = path;
		this.role = role;
		this.jwtSupplier = jwtSupplier;
	}

	public static KubernetesAuthenticationOptionsBuilder builder() {
		return new KubernetesAuthenticationOptionsBuilder();
	}

	public String getPath() {
		return path;
	}

	public String getRole() {
		return role;
	}

	public KubeJwtSupplier getJwtSupplier() {
		return jwtSupplier;
	}

	/**
	 * Builder for {@link KubeAuthenticationOptions}.
	 */
	public static class KubernetesAuthenticationOptionsBuilder {
		private String path = DEFAULT_KUBERNETES_AUTHENTICATION_PATH;

		private String role;

		private KubeJwtSupplier jwtSupplier;

		public KubernetesAuthenticationOptionsBuilder path(String path) {

			Assert.hasText(path, "Path must not be empty");

			this.path = path;
			return this;
		}

		public KubernetesAuthenticationOptionsBuilder role(String role) {

			Assert.hasText(role, "Role must not be empty");

			this.role = role;
			return this;
		}

		public KubernetesAuthenticationOptionsBuilder jwtSupplier(
				KubeJwtSupplier jwtSupplier) {

			Assert.notNull(jwtSupplier, "JwtSupplier must not be null");

			this.jwtSupplier = jwtSupplier;
			return this;
		}

		public KubeAuthenticationOptions build() {

			Assert.notNull(role, "Role must not be null");
			Assert.notNull(path, "Path must not be null");
			Assert.notNull(jwtSupplier, "JwtSupplier must not be null");

			return new KubeAuthenticationOptions(path, role, jwtSupplier);
		}
	}
}