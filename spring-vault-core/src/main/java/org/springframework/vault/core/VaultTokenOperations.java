/*
 * Copyright 2016 the original author or authors.
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
package org.springframework.vault.core;

import org.springframework.vault.client.VaultException;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.VaultTokenRequest;
import org.springframework.vault.support.VaultTokenResponse;

/**
 * Interface that specifies token-related operations.
 * 
 * @author Mark Paluch
 */
public interface VaultTokenOperations {

	/**
	 * Creates a new token.
	 * 
	 * @return a {@link VaultTokenResponse}
	 */
	VaultTokenResponse create() throws VaultException;

	/**
	 * Creates a new token for the given {@link VaultTokenRequest}.
	 * 
	 * @param request must not be {@literal null}.
	 * @return a {@link VaultTokenResponse}
	 */
	VaultTokenResponse create(VaultTokenRequest request) throws VaultException;

	/**
	 * Creates a new orphan token.
	 *
	 * @return a {@link VaultTokenResponse}
	 */
	VaultTokenResponse createOrphan();

	/**
	 * Creates a new orphan token for the given {@link VaultTokenRequest}.
	 *
	 * @param request must not be {@literal null}.
	 * @return a {@link VaultTokenResponse}
	 */
	VaultTokenResponse createOrphan(VaultTokenRequest request);

	/**
	 * Renews a {@link VaultToken}.
	 * 
	 * @param vaultToken must not be {@literal null}.
	 * @return a {@link VaultTokenResponse}
	 */
	VaultTokenResponse renew(VaultToken vaultToken);

	/**
	 * Revokes a {@link VaultToken}.
	 * 
	 * @param vaultToken must not be {@literal null}.
	 */
	void revoke(VaultToken vaultToken);

	/**
	 * Revokes a {@link VaultToken} but not its child tokens.
	 *
	 * @param vaultToken must not be {@literal null}.
	 */
	void revokeOrphan(VaultToken vaultToken);
}
