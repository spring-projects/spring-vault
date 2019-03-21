/*
 * Copyright 2016 the original author or authors.
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

import org.springframework.vault.support.VaultToken;

/**
 * Strategy interface that encapsulates the creation and management of Vault sessions
 * based on {@link VaultToken}.
 * <p>
 * {@link SessionManager} is used by {@link org.springframework.vault.core.VaultTemplate}
 * to initiate a session. Implementing classes usually use {@link ClientAuthentication} to
 * log into Vault and obtain tokens.
 * 
 * @author Mark Paluch
 * @see SimpleSessionManager
 * @see ClientAuthentication
 */
public interface SessionManager {

	/**
	 * Obtain a session token.
	 * 
	 * @return a session token.
	 */
	VaultToken getSessionToken();
}
