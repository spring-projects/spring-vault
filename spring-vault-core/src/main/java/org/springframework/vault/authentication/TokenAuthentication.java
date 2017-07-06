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
import org.springframework.vault.support.VaultToken;

/**
 * Static Token-based {@link ClientAuthentication} method.
 *
 * @author Mark Paluch
 * @see VaultToken
 * @see <a href="https://www.vaultproject.io/docs/auth/token.html">Auth Backend: Token</a>
 */
public class TokenAuthentication implements ClientAuthentication,
		AuthenticationStepsFactory {

	private final VaultToken token;

	/**
	 * Create a new {@link TokenAuthentication} with a static {@code token}.
	 *
	 * @param token the Vault token, must not be empty or {@literal null}.
	 */
	public TokenAuthentication(String token) {

		Assert.hasText(token, "Token must not be empty");

		this.token = VaultToken.of(token);
	}

	/**
	 * Create a new {@link TokenAuthentication} with a static {@code token}.
	 *
	 * @param token the Vault token, must not be {@literal null}.
	 */
	public TokenAuthentication(VaultToken token) {

		Assert.notNull(token, "Token must not be null");

		this.token = token;
	}

	/**
	 * Creates a {@link AuthenticationSteps} for token authentication given
	 * {@link VaultToken}.
	 *
	 * @param token must not be {@literal null}.
	 * @return {@link AuthenticationSteps} for token authentication.
	 * @since 2.0
	 */
	public static AuthenticationSteps createAuthenticationSteps(VaultToken token) {

		Assert.notNull(token, "VaultToken must not be null");

		return AuthenticationSteps.just(token);
	}

	@Override
	public VaultToken login() {
		return this.token;
	}

	@Override
	public AuthenticationSteps getAuthenticationSteps() {
		return createAuthenticationSteps(this.token);
	}
}
