/*
 * Copyright 2020-2025 the original author or authors.
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

package org.springframework.vault.client;

import reactor.core.publisher.Mono;

/**
 * Component that provides reactively a {@link VaultEndpoint}. Allows to use a
 * different {@link VaultEndpoint} for each Vault request.
 *
 * @author Mark Paluch
 * @since 2.3
 */
@FunctionalInterface
public interface ReactiveVaultEndpointProvider {

	/**
	 * Provides access to {@link VaultEndpoint}.
	 * @return a mono emitting the {@link VaultEndpoint}.
	 */
	Mono<VaultEndpoint> getVaultEndpoint();

}
