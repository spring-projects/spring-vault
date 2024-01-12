/*
 * Copyright 2016-2024 the original author or authors.
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

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.util.Assert;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.support.VaultHealth;

import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link ReactiveVaultSysOperations}.
 *
 * @author Mark Paluch
 */
public class ReactiveVaultSysTemplate implements ReactiveVaultSysOperations {

	private final ReactiveVaultOperations vaultOperations;

	/**
	 * Create a new {@link ReactiveVaultSysTemplate} with the given
	 * {@link ReactiveVaultOperations}.
	 * @param vaultOperations must not be {@literal null}.
	 */
	public ReactiveVaultSysTemplate(ReactiveVaultOperations vaultOperations) {

		Assert.notNull(vaultOperations, "ReactiveVaultOperations must not be null");

		this.vaultOperations = vaultOperations;

	}

	@Override
	public Mono<Boolean> isInitialized() {

		return this.vaultOperations.doWithSession(webClient -> {
			return webClient.get()
				.uri("sys/init")
				.header(VaultHttpHeaders.VAULT_NAMESPACE, "")
				.exchangeToMono(clientResponse -> clientResponse.toEntity(Map.class))
				.map(it -> (Boolean) it.getBody().get("initialized"));
		});
	}

	@Override
	public Mono<VaultHealth> health() {

		return this.vaultOperations.doWithVault(webClient -> {

			return webClient.get()
				.uri("sys/health")
				.header(VaultHttpHeaders.VAULT_NAMESPACE, "")
				.exchangeToMono(clientResponse -> {
					return clientResponse.toEntity(VaultSysTemplate.VaultHealthImpl.class).map(HttpEntity::getBody);
				});
		});
	}

}
