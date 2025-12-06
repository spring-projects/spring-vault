/*
 * Copyright 2017-2025 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;

import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestOperations;

/**
 * Kubernetes implementation of {@link ClientAuthentication}.
 * {@link KubernetesAuthentication} uses a Kubernetes Service Account JSON Web
 * Token to login into Vault. JWT and Role are sent in the login request to
 * Vault to obtain a {@link VaultToken}.
 *
 * @author Michal Budzyn
 * @author Mark Paluch
 * @since 2.0
 * @see KubernetesAuthenticationOptions
 * @see VaultClient
 * @see <a href="https://www.vaultproject.io/docs/auth/kubernetes.html">Auth
 * Backend: Kubernetes</a>
 */
public class KubernetesAuthentication implements ClientAuthentication, AuthenticationStepsFactory {

	private final KubernetesAuthenticationOptions options;

	private final VaultLoginClient loginClient;


	/**
	 * Create a {@link KubernetesAuthentication} using
	 * {@link KubernetesAuthenticationOptions} and {@link RestOperations}.
	 * @param options must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 * @deprecated since 4.1, use
	 * {@link #KubernetesAuthentication(KubernetesAuthenticationOptions, VaultClient)}
	 * instead.
	 */
	@Deprecated(since = "4.1")
	public KubernetesAuthentication(KubernetesAuthenticationOptions options, RestOperations restOperations) {
		this(options, ClientAdapter.from(restOperations).vaultClient());
	}

	/**
	 * Create a {@link KubernetesAuthentication} using
	 * {@link KubernetesAuthenticationOptions} and {@link RestOperations}.
	 * @param options must not be {@literal null}.
	 * @param client must not be {@literal null}.
	 * @since 4.0
	 * @deprecated since 4.1, use
	 * {@link #KubernetesAuthentication(KubernetesAuthenticationOptions, VaultClient)}
	 * instead.
	 */
	@Deprecated(since = "4.1")
	public KubernetesAuthentication(KubernetesAuthenticationOptions options, RestClient client) {
		this(options, ClientAdapter.from(client).vaultClient());
	}

	/**
	 * Create a {@link KubernetesAuthentication} using
	 * {@link KubernetesAuthenticationOptions} and {@link VaultClient}.
	 * @param options must not be {@literal null}.
	 * @param client must not be {@literal null}.
	 * @since 4.1
	 */
	public KubernetesAuthentication(KubernetesAuthenticationOptions options, VaultClient client) {
		Assert.notNull(options, "KubernetesAuthenticationOptions must not be null");
		Assert.notNull(client, "VaultClient must not be null");
		this.options = options;
		this.loginClient = VaultLoginClient.create(client, "Kubernetes");
	}


	/**
	 * Create {@link AuthenticationSteps} for kubernetes authentication given
	 * {@link KubernetesAuthenticationOptions}.
	 * @param options must not be {@literal null}.
	 * @return {@link AuthenticationSteps} for kubernetes authentication.
	 */
	public static AuthenticationSteps createAuthenticationSteps(KubernetesAuthenticationOptions options) {
		Assert.notNull(options, "KubernetesAuthenticationOptions must not be null");
		return AuthenticationSteps.fromSupplier(options.getJwtSupplier())
				.map(token -> getKubernetesLogin(options.getRole(), token))
				.loginAt(options.getPath());
	}


	@Override
	public VaultToken login() throws VaultException {
		Map<String, String> login = getKubernetesLogin(this.options.getRole(), this.options.getJwtSupplier().get());
		return this.loginClient.loginAt(this.options.getPath()).using(login).retrieve().loginToken();
	}

	@Override
	public AuthenticationSteps getAuthenticationSteps() {
		return createAuthenticationSteps(this.options);
	}

	private static Map<String, String> getKubernetesLogin(String role, String jwt) {
		Assert.hasText(role, "Role must not be empty");
		Assert.hasText(jwt, "JWT must not be empty");
		Map<String, String> login = new HashMap<>();
		login.put("jwt", jwt);
		login.put("role", role);
		return login;
	}

}
