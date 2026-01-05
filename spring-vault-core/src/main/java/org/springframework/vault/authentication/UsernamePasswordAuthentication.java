/*
 * Copyright 2021-present the original author or authors.
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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestOperations;

import static org.springframework.vault.authentication.AuthenticationUtil.*;

/**
 * Username and password implementation of {@link ClientAuthentication}. Can be
 * used for {@code userpass}, {@code ldap}, {@code okta}, and {@code radius}
 * authentication methods.
 *
 * @author Mikhael Sokolov
 * @author Mark Paluch
 * @see UsernamePasswordAuthenticationOptions
 * @see RestOperations
 * @see <a href="https://www.vaultproject.io/docs/auth/userpass">Username and
 * password</a>
 * @see <a href="https://www.vaultproject.io/docs/auth/ldap">LDAP
 * authentication</a>
 * @see <a href="https://www.vaultproject.io/docs/auth/okta">Okta
 * authentication</a>
 * @see <a href="https://www.vaultproject.io/docs/auth/radius">RADIUS
 * authentication</a>
 * @since 2.4
 */
public class UsernamePasswordAuthentication implements ClientAuthentication, AuthenticationStepsFactory {

	private static final Log logger = LogFactory.getLog(UsernamePasswordAuthentication.class);


	private final UsernamePasswordAuthenticationOptions options;

	private final ClientAdapter adapter;

	/**
	 * Create a {@link UsernamePasswordAuthentication} using
	 * {@link UsernamePasswordAuthenticationOptions} and {@link RestOperations}.
	 * @param options must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 */
	public UsernamePasswordAuthentication(UsernamePasswordAuthenticationOptions options,
			RestOperations restOperations) {
		Assert.notNull(options, "UsernamePasswordAuthenticationOptions must not be null");
		Assert.notNull(restOperations, "RestOperations must not be null");
		this.options = options;
		this.adapter = ClientAdapter.from(restOperations);
	}

	/**
	 * Create a {@link UsernamePasswordAuthentication} using
	 * {@link UsernamePasswordAuthenticationOptions} and {@link RestClient}.
	 * @param options must not be {@literal null}.
	 * @param client must not be {@literal null}.
	 * @since 4.0
	 */
	public UsernamePasswordAuthentication(UsernamePasswordAuthenticationOptions options, RestClient client) {
		Assert.notNull(options, "UsernamePasswordAuthenticationOptions must not be null");
		Assert.notNull(client, "RestClient must not be null");
		this.options = options;
		this.adapter = ClientAdapter.from(client);
	}

	/**
	 * Create {@link AuthenticationSteps} for username/password authentication
	 * given {@link UsernamePasswordAuthenticationOptions}.
	 * @param options must not be {@literal null}.
	 * @return {@link AuthenticationSteps} for username/password authentication.
	 */
	public static AuthenticationSteps createAuthenticationSteps(UsernamePasswordAuthenticationOptions options) {
		Assert.notNull(options, "UsernamePasswordAuthenticationOptions must not be null");
		Map<String, Object> body = createLoginBody(options);
		return AuthenticationSteps.fromSupplier(() -> body)
				.login("%s/%s".formatted(getLoginPath(options.getPath()), options.getUsername()));
	}

	@Override
	public VaultToken login() throws VaultException {
		return createTokenUsingUsernamePasswordAuthentication();
	}

	@Override
	public AuthenticationSteps getAuthenticationSteps() {
		return createAuthenticationSteps(this.options);
	}

	@SuppressWarnings("NullAway")
	private VaultToken createTokenUsingUsernamePasswordAuthentication() {
		try {
			VaultResponse response = adapter.postForObject(
					"%s/%s".formatted(getLoginPath(options.getPath()), options.getUsername()), createLoginBody(options),
					VaultResponse.class);
			logger.debug("Login successful using username and password credentials");
			return LoginTokenUtil.from(response.getAuth());
		} catch (HttpStatusCodeException e) {
			throw new VaultException("Cannot login using username and password: %s"
					.formatted(VaultResponses.getError(e.getResponseBodyAsString())), e);
		}
	}

	private static Map<String, Object> createLoginBody(UsernamePasswordAuthenticationOptions options) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("password", options.getPassword());
		CharSequence totp = options.getTotp();
		if (totp != null) {
			body.put("totp", totp);
		}
		return body;
	}

}
