/*
 * Copyright 2016-2025 the original author or authors.
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

import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultToken;

/**
 * Strategy interface representing an authentication mechanism to obtain a
 * {@link VaultToken} for authenticated Vault access.
 *
 * <p>Implementations typically use authentication methods (e.g., AppRole, AWS)
 * to obtain a token from Vault by providing authentication-specific credential.
 * The returned token may be renewable or non-renewable. Implementations can
 * return a {@link LoginToken} to provide additional metadata such as accessor,
 * renewability, and time-to-live for session management purposes. Token
 * rotation (after token expiry or reaching the max time to live) uses the same
 * ClientAuthentication to obtain a new token.
 *
 * <p>Authentication mechanisms may additionally implement
 * {@link AuthenticationStepsFactory} to expose authentication steps for
 * reactive/non-blocking usage.
 *
 * @author Mark Paluch
 * @since 1.0
 * @see LoginToken
 * @see AuthenticationStepsFactory
 * @see AuthenticationSteps
 */
@FunctionalInterface
public interface ClientAuthentication {


	/**
	 * Obtain a {@link VaultToken} for authenticated Vault access.
	 * <p>This method may perform an authentication request to Vault or return a
	 * cached or pre-configured token.
	 * @return the Vault token for subsequent authenticated requests
	 * @throws VaultLoginException if authentication fails.
	 * @see LoginToken
	 */
	VaultToken login() throws VaultException;

}
