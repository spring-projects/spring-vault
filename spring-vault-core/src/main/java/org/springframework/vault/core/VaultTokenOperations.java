/*
 * Copyright 2016-2022 the original author or authors.
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
package org.springframework.vault.core;

import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.VaultTokenRequest;
import org.springframework.vault.support.VaultTokenResponse;

/**
 * Interface that specifies token-related operations.
 *
 * @author Mark Paluch
 * @author Nanne Baars
 * @see <a href="https://www.vaultproject.io/docs/auth/token.html">Auth Backend: Token</a>
 */
public interface VaultTokenOperations {

	/**
	 * Create a new token.
	 * @return a {@link VaultTokenResponse}
	 * @see <a href="https://www.vaultproject.io/docs/auth/token.html">POST
	 * /auth/token/create</a>
	 */
	default VaultTokenResponse create() throws VaultException {
		return create(VaultTokenRequest.empty());
	}

	/**
	 * Create a new token for the given {@link VaultTokenRequest}.
	 * @param request must not be {@literal null}.
	 * @return a {@link VaultTokenResponse}
	 * @see <a href="https://www.vaultproject.io/docs/auth/token.html">POST
	 * /auth/token/create</a>
	 */
	VaultTokenResponse create(VaultTokenRequest request) throws VaultException;

	/**
	 * Create a new token for the given {@code role}.
	 * @param role must not be {@literal null}.
	 * @return a {@link VaultTokenResponse}
	 * @see <a href="https://www.vaultproject.io/docs/auth/token.html">POST
	 * /auth/token/create/:role</a>
	 * @since 3.1
	 */
	default VaultTokenResponse create(String role) throws VaultException {
		return create(role, VaultTokenRequest.empty());
	}

	/**
	 * Create a new token for the given {@code role} and {@link VaultTokenRequest}.
	 * @param role must not be {@literal null} or empty.
	 * @param request must not be {@literal null}.
	 * @return a {@link VaultTokenResponse}
	 * @see <a href="https://www.vaultproject.io/docs/auth/token.html">POST
	 * /auth/token/create/:role</a>
	 * @since 3.1
	 */
	VaultTokenResponse create(String role, VaultTokenRequest request) throws VaultException;

	/**
	 * Create a new orphan token.
	 * @return a {@link VaultTokenResponse}
	 * @see <a href="https://www.vaultproject.io/docs/auth/token.html">POST
	 * /auth/token/create-orphan</a>
	 */
	default VaultTokenResponse createOrphan() {
		return createOrphan(VaultTokenRequest.empty());
	}

	/**
	 * Create a new orphan token for the given {@link VaultTokenRequest}.
	 * @param request must not be {@literal null}.
	 * @return a {@link VaultTokenResponse}
	 * @see <a href="https://www.vaultproject.io/docs/auth/token.html">POST
	 * /auth/token/create-orphan</a>
	 */
	VaultTokenResponse createOrphan(VaultTokenRequest request);

	/**
	 * Renew a {@link VaultToken}.
	 * @param vaultToken must not be {@literal null}.
	 * @return a {@link VaultTokenResponse}
	 * @see <a href="https://www.vaultproject.io/docs/auth/token.html">POST
	 * /auth/token/renew</a>
	 */
	VaultTokenResponse renew(VaultToken vaultToken);

	/**
	 * Revoke a {@link VaultToken}.
	 * @param vaultToken must not be {@literal null}.
	 * @see <a href="https://www.vaultproject.io/docs/auth/token.html">POST
	 * /auth/token/revoke</a>
	 */
	void revoke(VaultToken vaultToken);

	/**
	 * Revoke a {@link VaultToken} but not its child tokens.
	 * @param vaultToken must not be {@literal null}.
	 * @see <a href="https://www.vaultproject.io/docs/auth/token.html">POST
	 * /auth/token/revoke-orphan</a>
	 */
	void revokeOrphan(VaultToken vaultToken);

}
