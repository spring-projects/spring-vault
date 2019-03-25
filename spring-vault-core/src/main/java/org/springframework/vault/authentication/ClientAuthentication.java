/*
 * Copyright 2016-2019 the original author or authors.
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
 * {@link ClientAuthentication} provides {@link VaultToken} to be used for authenticated
 * Vault access. Implementing classes usually use a login method to login and return a
 * {@link VaultToken} when implementing {@link #login()}.
 *
 * @author Mark Paluch
 */
@FunctionalInterface
public interface ClientAuthentication {

	/**
	 * Return a {@link VaultToken}. This method can optionally log into Vault to obtain a
	 * {@link VaultToken token}.
	 *
	 * @return a {@link VaultToken}.
	 */
	VaultToken login() throws VaultException;
}
