/*
 * Copyright 2023-present the original author or authors.
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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.util.Assert;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * Default implementation of {@link ReactiveVaultKeyValueOperations} for the
 * Key/Value secrets engine version 1.
 *
 * @author Timothy R. Weiand
 * @author Mark Paluch
 * @since 3.1
 */
class ReactiveVaultKeyValue1Template extends ReactiveVaultKeyValueAccessor implements ReactiveVaultKeyValueOperations {

	private final String path;


	/**
	 * Create a new {@link ReactiveVaultKeyValue1Template} given
	 * {@link ReactiveVaultOperations} and the mount {@code path}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	public ReactiveVaultKeyValue1Template(ReactiveVaultOperations vaultOperations, String path) {
		super(vaultOperations, path);
		this.path = path;
	}


	@Override
	public Flux<String> list(String path) {
		return reactiveVaultOperations.list(createDataPath(path));
	}

	@Override
	@SuppressWarnings("unchecked")
	public Mono<VaultResponse> get(String path) {
		return doRead(path, Map.class, (response, map) -> {
			VaultResponse vaultResponse = new VaultResponse();
			vaultResponse.applyMetadata(response);
			vaultResponse.setData(map);
			return vaultResponse;
		});
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public <T> Mono<VaultResponseSupport<T>> get(String path, Class<T> responseType) {
		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(responseType, "Response type must not be null");
		return doRead(path, responseType, (response, data) -> {
			VaultResponseSupport result = response;
			result.setData(data);
			return result;
		});
	}

	@Override
	public Mono<Boolean> patch(String path, Map<String, ?> patch) {
		throw new IllegalStateException("K/V engine mount must be version 2 for patch support");
	}

	@Override
	public Mono<Void> put(String path, Object body) {
		Assert.hasText(path, "Path must not be empty");
		return doWrite(createDataPath(path), body).then();
	}

	@Override
	public KeyValueBackend getApiVersion() {
		return KeyValueBackend.KV_1;
	}

	@Override
	Object getJsonNode(VaultResponseSupport<Object> response) {
		return response.getRequiredData();
	}

	@Override
	String createDataPath(String path) {
		return "%s/%s".formatted(this.path, path);
	}

}
