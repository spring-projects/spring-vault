/*
 * Copyright 2021-2025 the original author or authors.
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

import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.support.VaultToken;
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
 * @see VaultClient
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

	private final UsernamePasswordAuthenticationOptions options;

	private final VaultLoginClient loginClient;


	/**
	 * Create a {@link UsernamePasswordAuthentication} using
	 * {@link UsernamePasswordAuthenticationOptions} and {@link RestOperations}.
	 * @param options must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 * @deprecated since 4.1, use
	 * {@link #UsernamePasswordAuthentication(UsernamePasswordAuthenticationOptions, VaultClient)}
	 * instead.
	 */
	@Deprecated(since = "4.1")
	public UsernamePasswordAuthentication(UsernamePasswordAuthenticationOptions options,
			RestOperations restOperations) {
		this(options, ClientAdapter.from(restOperations).vaultClient());
	}

	/**
	 * Create a {@link UsernamePasswordAuthentication} using
	 * {@link UsernamePasswordAuthenticationOptions} and {@link RestClient}.
	 * @param options must not be {@literal null}.
	 * @param client must not be {@literal null}.
	 * @since 4.0
	 */
	public UsernamePasswordAuthentication(UsernamePasswordAuthenticationOptions options, RestClient client) {
		this(options, ClientAdapter.from(client).vaultClient());
	}

	/**
	 * Create a {@link UsernamePasswordAuthentication} using
	 * {@link UsernamePasswordAuthenticationOptions} and {@link VaultClient}.
	 * @param options must not be {@literal null}.
	 * @param client must not be {@literal null}.
	 * @since 4.1
	 */
	public UsernamePasswordAuthentication(UsernamePasswordAuthenticationOptions options, VaultClient client) {
		Assert.notNull(options, "UsernamePasswordAuthenticationOptions must not be null");
		Assert.notNull(client, "VaultClient must not be null");
		this.options = options;
		this.loginClient = VaultLoginClient.create(client, "Username and Password (" + options.getPath() + ")");
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
		return loginClient.login()
				.path("auth/{mount}/login/{username}", options.getPath(), options.getUsername())
				.using(createLoginBody(options))
				.retrieve()
				.loginToken();
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
