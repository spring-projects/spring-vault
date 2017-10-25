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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

/**
 * Kubernetes implementation of {@link ClientAuthentication}. {@link KubeAuthentication}
 * uses a Kubernetes Service Account JSON Web Token to login into Vault. JWT and Role are
 * sent in the login request to Vault to obtain a {@link VaultToken}.
 *
 * @author Michal Budzyn
 * @see KubeAuthenticationOptions
 * @see RestOperations
 * @see <a href="https://www.vaultproject.io/docs/auth/kubernetes.html">Auth Backend:
 * Kubernetes</a>
 */
public class KubeAuthentication implements ClientAuthentication {

	private static final Log logger = LogFactory.getLog(KubeAuthentication.class);

	private final KubeAuthenticationOptions options;

	private final RestOperations restOperations;

	/**
	 * Create a {@link KubeAuthentication} using {@link KubeAuthenticationOptions} and
	 * {@link RestOperations}.
	 *
	 * @param options must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 */
	public KubeAuthentication(KubeAuthenticationOptions options,
			RestOperations restOperations) {

		Assert.notNull(options, "KubeAuthenticationOptions must not be null");
		Assert.notNull(restOperations, "RestOperations must not be null");

		this.options = options;
		this.restOperations = restOperations;
	}

	private static Map<String, String> getKubeLogin(String role, String jwt) {

		Assert.hasText(role, "role must not be empty");
		Assert.hasText(role, "jwt must not be empty");

		Map<String, String> login = new HashMap<>();

		login.put("jwt", jwt);
		login.put("role", role);

		return login;
	}

	@Override
	public VaultToken login() throws VaultException {
		return createTokenUsingKubernetes();
	}

	private VaultToken createTokenUsingKubernetes() {

		Map<String, String> login = getKubeLogin(options.getRole(),
				options.getJwtSupplier().getKubeJwt());

		try {
			VaultResponse response = restOperations.postForObject("auth/{mount}/login",
					login, VaultResponse.class, options.getPath());

			Assert.state(response != null && response.getAuth() != null,
					"Auth field must not be null");

			logger.debug("Login successful using Kubernetes authentication");

			return LoginTokenUtil.from(response.getAuth());
		}
		catch (HttpStatusCodeException e) {
			throw new VaultException(String.format("Cannot login using kubernetes: %s",
					VaultResponses.getError(e.getResponseBodyAsString())));
		}
	}
}
