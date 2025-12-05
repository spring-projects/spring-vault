/*
 * Copyright 2023-2025 the original author or authors.
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

import java.util.Collections;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * Default implementation of {@link VaultKeyValueOperations} for the key-value
 * backend version 2.
 *
 * @author Timothy R. Weiand
 * @author Mark Paluch
 * @since 3.1
 */
class ReactiveVaultKeyValue2Template extends ReactiveVaultKeyValue2Accessor implements ReactiveVaultKeyValueOperations {

	private final String path;


	/**
	 * Create a new {@link ReactiveVaultKeyValue2Template} given
	 * {@link VaultOperations} and the mount {@code path}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	public ReactiveVaultKeyValue2Template(ReactiveVaultTemplate vaultOperations, String path) {
		super(vaultOperations, path);
		this.path = path;
	}


	@Override
	@SuppressWarnings("unchecked")
	public Mono<VaultResponse> get(String path) {
		Assert.hasText(path, "Path must not be empty");
		return doRead(path, Map.class, (response, data) -> {
			VaultResponse vaultResponse = new VaultResponse();
			vaultResponse.applyMetadata(response);
			vaultResponse.setData(data);
			return vaultResponse;
		});
	}

	@Override
	@SuppressWarnings("unchecked")
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
		Assert.notNull(patch, "Patch body must not be null");
		return get(path).filter(it -> it.getData() != null)
				.switchIfEmpty(Mono.error(new SecretNotFoundException(
						"No data found at '%s'; patch only works on existing data".formatted(createDataPath(path)),
						createLogicalPath(path))))
				.flatMap(readResponse -> {

					if (readResponse.getMetadata() == null) {
						return Mono.error(new VaultException("Metadata must not be null"));
					}

					Map<String, Object> body = KeyValueUtilities.createPatchRequest(patch,
							readResponse.getRequiredData(),
							readResponse.getMetadata());
					return doWrite(createDataPath(path), body).thenReturn(true).onErrorResume(VaultException.class,
							e -> {
								if (e.getMessage() != null && (e.getMessage().contains("check-and-set")
										|| e.getMessage().contains("did not match the current version"))) {
									return Mono.just(Boolean.FALSE);
								}
								return Mono.error(e);
							});
				});
	}

	@Override
	public Mono<Void> put(String path, Object body) {
		return doWrite(createDataPath(path), Collections.singletonMap("data", body)).then();
	}

	private String createLogicalPath(String path) {
		return "%s/%s".formatted(this.path, path);
	}

}
