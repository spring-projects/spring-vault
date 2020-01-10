/*
 * Copyright 2017-2020 the original author or authors.
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

import reactor.core.publisher.Mono;

import org.springframework.vault.support.VaultToken;

/**
 * {@link VaultTokenSupplier} provides a {@link VaultToken} to be used for authenticated
 * Vault access. Implementing classes usually use a login method to login and return a
 * {@link VaultToken} implementing {@link #getVaultToken()}.
 *
 * @author Mark Paluch
 * @since 2.0
 */
@FunctionalInterface
public interface VaultTokenSupplier {

	/**
	 * Return a {@link VaultToken}. This can declare a Vault login flow to obtain a
	 * {@link VaultToken token}.
	 *
	 * @return a {@link Mono} with the {@link VaultToken}.
	 */
	Mono<VaultToken> getVaultToken();
}
