/*
 * Copyright 2017-2025 the original author or authors.
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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import reactor.core.publisher.Mono;

import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultToken;

/**
 * Default implementation of {@link VaultTokenSupplier} caching the
 * {@link VaultToken} from a delegate {@link VaultTokenSupplier}.
 *
 * @author Mark Paluch
 * @since 2.0
 * @see VaultTokenSupplier
 * @see VaultToken
 */
public class CachingVaultTokenSupplier implements VaultTokenSupplier, ReactiveSessionManager {

	private static final Mono<VaultToken> EMPTY = Mono.empty();


	private final VaultTokenSupplier clientAuthentication;

	private final AtomicReference<Mono<VaultToken>> tokenRef = new AtomicReference<>(EMPTY);


	private CachingVaultTokenSupplier(VaultTokenSupplier clientAuthentication) {
		this.clientAuthentication = clientAuthentication;
	}


	/**
	 * Create a new {@code CachingVaultTokenSupplier} given a
	 * {@link VaultTokenSupplier delegate supplier}.
	 * @param delegate must not be {@literal null}.
	 * @return the {@code CachingVaultTokenSupplier} for a {@link VaultTokenSupplier
	 * delegate supplier}.
	 */
	public static CachingVaultTokenSupplier of(VaultTokenSupplier delegate) {
		return new CachingVaultTokenSupplier(delegate);
	}


	@Override
	public Mono<VaultToken> getVaultToken() throws VaultException {
		if (Objects.equals(this.tokenRef.get(), EMPTY)) {
			this.tokenRef.compareAndSet(EMPTY, this.clientAuthentication.getVaultToken().cache());
		}
		return this.tokenRef.get();
	}

}
