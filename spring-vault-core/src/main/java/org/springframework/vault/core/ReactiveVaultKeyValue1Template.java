/*
 * Copyright 2018-2022 the original author or authors.
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.Assert;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link ReactiveVaultKeyValueOperations} for the Key/Value
 * backend version 1.
 *
 * @author Timothy R. Weiand
 * @since 3.999.999
 */
class ReactiveVaultKeyValue1Template extends ReactiveVaultKeyValueAccessor implements ReactiveVaultKeyValueOperations {

	/**
	 * Create a new {@link ReactiveVaultKeyValue1Template} given
	 * {@link ReactiveVaultOperations} and the mount {@code path}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	public ReactiveVaultKeyValue1Template(ReactiveVaultOperations vaultOperations, String path) {

		super(vaultOperations, path);
	}

	@Override
	public Flux<String> list(String path) {
		return reactiveVaultOperations.list(createDataPath(path));
	}

	@Override
	@SuppressWarnings("unchecked")
	public Mono<VaultResponse> get(String path) {
		var ref = new ParameterizedTypeReference<Map<String, Object>>() {
		};

		return doRead(path, ref).onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
			.map(response -> {
				VaultResponse vaultResponse = new VaultResponse();
				vaultResponse.setRenewable(response.isRenewable());
				vaultResponse.setAuth(response.getAuth());
				vaultResponse.setLeaseDuration(response.getLeaseDuration());
				vaultResponse.setLeaseId(response.getLeaseId());
				vaultResponse.setMetadata(response.getMetadata());
				vaultResponse.setRequestId(response.getRequestId());
				vaultResponse.setWarnings(response.getWarnings());
				vaultResponse.setWrapInfo(response.getWrapInfo());
				vaultResponse.setData(response.getData());
				return vaultResponse;
			});
	}

	@Override
	public <T> Mono<VaultResponseSupport<T>> get(String path, Class<T> responseType) {

		return doRead(path, responseType).onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
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
	String createDataPath(String path) {
		return String.format("%s/%s", this.path, path);
	}

}
