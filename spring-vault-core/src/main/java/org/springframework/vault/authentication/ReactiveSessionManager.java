/*
 * Copyright 2018-2024 the original author or authors.
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
 * Strategy interface that encapsulates the creation and management of Vault sessions
 * based on {@link VaultToken} used by reactive components.
 * <p>
 * {@link ReactiveSessionManager} is used by
 * {@link org.springframework.vault.core.ReactiveVaultTemplate} to initiate a session.
 * Implementing classes usually use {@link VaultTokenSupplier} to log into Vault and
 * obtain tokens.
 *
 * @author Mark Paluch
 * @see CachingVaultTokenSupplier
 * @see ReactiveLifecycleAwareSessionManager
 * @since 2.0
 */
public interface ReactiveSessionManager extends VaultTokenSupplier {

	/**
	 * Obtain a session token.
	 * @return a session token.
	 */
	default Mono<VaultToken> getSessionToken() {
		return getVaultToken();
	}

}
