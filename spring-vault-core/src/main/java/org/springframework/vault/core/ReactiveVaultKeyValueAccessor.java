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

import static org.springframework.vault.core.ReactiveVaultTemplate.mapResponse;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import reactor.core.publisher.Mono;

/**
 * Base class for {@link ReactiveVaultVersionedKeyValueTemplate} and
 * {@link ReactiveVaultKeyValue2Template} and other Vault KV-accessing helpers, defining
 * common
 * <p/>
 * Not intended to be used directly. See {@link ReactiveVaultVersionedKeyValueTemplate}
 * and {@link ReactiveVaultKeyValue2Template}.
 *
 * @author Timothy R. Weiand
 * @since TBD
 */
abstract class ReactiveVaultKeyValueAccessor implements ReactiveVaultKeyValueOperationsSupport {

	protected final ReactiveVaultOperations reactiveVaultOperations;

	protected final String path;

	/**
	 * Create a new {@link ReactiveVaultKeyValueAccessor} given
	 * {@link ReactiveVaultOperations} and the mount {@code path}.
	 * @param reactiveVaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	ReactiveVaultKeyValueAccessor(ReactiveVaultOperations reactiveVaultOperations, String path) {

		Assert.notNull(reactiveVaultOperations, "ReactiveVaultOperations must not be null");
		Assert.hasText(path, "Path must not be empty");

		this.reactiveVaultOperations = reactiveVaultOperations;
		this.path = path;
	}

	@Override
	public Mono<Void> delete(String path) {

		Assert.hasText(path, "Path must not be empty");
		var dataPath = createDataPath(path);

		return reactiveVaultOperations.doWithSession(webClient -> webClient.delete().uri(dataPath)
				.exchangeToMono(mapResponse(String.class, path, HttpMethod.DELETE))).then();
	}

	<I> Mono<VaultResponseSupport<I>> doRead(String path, Class<I> deserializeAs) {
		var ref = VaultResponses.getTypeReference(deserializeAs);
		return doReadRaw(createDataPath(path), ref);
	}

	<I> Mono<VaultResponseSupport<I>> doRead(String path, ParameterizedTypeReference<I> deserializeAs) {
		var ref = VaultResponses.getTypeReference(deserializeAs);
		return doReadRaw(createDataPath(path), ref);
	}

	<I> Mono<I> doReadRaw(String path, ParameterizedTypeReference<I> rawRef) {
		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(rawRef, "Response type must not be null");

		return reactiveVaultOperations.doWithSession(
				webClient -> webClient.get().uri(path).exchangeToMono(mapResponse(rawRef, path, HttpMethod.GET)));
	}

	/**
	 * Write the {@code body} to the given Vault {@code path}.
	 * @param path must not be {@literal null} or empty.
	 * @param body to be written.
	 * @return the response of this write action.
	 */
	Mono<VaultResponse> doWrite(String path, @Nullable Object body) {
		Assert.hasText(path, "Path must not be empty");
		return reactiveVaultOperations.write(path, body);
	}

	/**
	 * @param path must not be {@literal null} or empty.
	 * @return backend path representing the data path.
	 */
	abstract String createDataPath(String path);

}
