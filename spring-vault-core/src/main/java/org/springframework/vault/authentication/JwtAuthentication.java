/*
 * Copyright 2017-2024 the original author or authors.
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

/**
 * JWT implementation of {@link ClientAuthentication}. {@link JwtAuthentication} uses a
 * JSON Web Token to login into Vault. JWT and Role are sent in the login request to Vault
 * to obtain a {@link VaultToken}.
 *
 * @author Nanne Baars
 * @since 3.1
 * @see JwtAuthenticationOptions
 * @see RestOperations
 * @see <a href="https://www.vaultproject.io/api-docs/auth/jwt">Vault Auth Backend:
 * JWT</a>
 */
public class JwtAuthentication implements ClientAuthentication, AuthenticationStepsFactory {

	public static final String DEFAULT_JWT_AUTHENTICATION_PATH = "jwt";

	private static final Log logger = LogFactory.getLog(JwtAuthentication.class);

	private final JwtAuthenticationOptions options;

	private final RestOperations restOperations;

	/**
	 * Create a {@link JwtAuthentication} using {@link JwtAuthenticationOptions} and
	 * {@link RestOperations}.
	 * @param options must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 */
	public JwtAuthentication(JwtAuthenticationOptions options, RestOperations restOperations) {

		Assert.notNull(options, "JwtAuthenticationOptions must not be null");
		Assert.notNull(restOperations, "RestOperations must not be null");

		this.options = options;
		this.restOperations = restOperations;
	}

	@Override
	public AuthenticationSteps getAuthenticationSteps() {
		return AuthenticationSteps.fromSupplier(options.getJwtSupplier())
			.map(token -> getJwtLogin(options.getRole(), token))
			.login(AuthenticationUtil.getLoginPath(this.options.getPath()));
	}

	@Override
	public VaultToken login() throws VaultException {

		Map<String, String> login = getJwtLogin(this.options.getRole(), this.options.getJwtSupplier().get());

		try {

			VaultResponse response = this.restOperations
				.postForObject(AuthenticationUtil.getLoginPath(this.options.getPath()), login, VaultResponse.class);
			Assert.state(response != null && response.getAuth() != null, "Auth field must not be null");

			logger.debug("Login successful using JWT authentication");

			return LoginTokenUtil.from(response.getAuth());
		}
		catch (RestClientException e) {
			throw VaultLoginException.create("JWT", e);
		}
	}

	private static Map<String, String> getJwtLogin(@Nullable String role, String jwt) {

		Map<String, String> login = new HashMap<>(2);

		login.put("jwt", jwt);

		if (StringUtils.hasText(role)) {
			login.put("role", role);
		}

		return login;
	}

}
