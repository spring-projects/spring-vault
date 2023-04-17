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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.BiFunction;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
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

	protected final ReactiveVaultOperations vaultOperations;

	protected final String path;

	private final ObjectMapper mapper;

	// private final ObjectMapper mapper;

	/**
	 * Create a new {@link ReactiveVaultKeyValueAccessor} given
	 * {@link ReactiveVaultOperations} and the mount {@code path}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	ReactiveVaultKeyValueAccessor(ReactiveVaultOperations vaultOperations, String path) {

		Assert.notNull(vaultOperations, "ReactiveVaultOperations must not be null");
		Assert.hasText(path, "Path must not be empty");

		this.vaultOperations = vaultOperations;
		this.path = path;
		this.mapper = extractObjectMapper(vaultOperations);
	}

	private static ObjectMapper extractObjectMapper(ReactiveVaultOperations vaultOperations) {
		// TODO: Pull pre-configured object mapper from WebClient
		return new ObjectMapper();
	}

	@Override
	public Mono<Void> delete(String path) {

		Assert.hasText(path, "Path must not be empty");
		var dataPath = createDataPath(path);

		return vaultOperations.doWithSession(webClient -> webClient.delete().uri(dataPath)
				.exchangeToMono(mapResponse(String.class, dataPath, HttpMethod.DELETE))).then();
	}

	/**
	 * Read a secret at {@code path} and deserialize the {@literal data} element to the
	 * given {@link Class type}.
	 * @param path must not be {@literal null}.
	 * @param deserializeAs must not be {@literal null}.
	 * @param mappingFunction Mapping function to convert from the intermediate to the
	 * target data type. Must not be {@literal null}.
	 * @param <I> intermediate data type for {@literal data} deserialization.
	 * @param <T> return type. Value is created by the {@code mappingFunction}.
	 * @return mapped value.
	 */
	<I, T> Mono<T> doRead(String path, Class<I> deserializeAs,
			BiFunction<VaultResponseSupport<?>, I, Mono<T>> mappingFunction) {

		return vaultOperations.read(createDataPath(path), deserializeAs)
				.flatMap(responseSupport -> ReactiveKeyValueHelper.getRequiredData(responseSupport)
						.flatMap(data -> mappingFunction.apply(responseSupport, data)));
	}

	/**
	 * Read a secret at {@code path} and deserialize the {@literal data} element to the
	 * given {@link ParameterizedTypeReference type}.
	 * @param path must not be {@literal null} or empty.
	 * @param responseType must not be {@literal null}
	 * @return mapped value.
	 */
	<T> Mono<T> doRead(String path, Class<T> responseType) {

		return vaultOperations.read(path, responseType)
				.flatMap(responseSupport -> Mono.justOrEmpty(responseSupport.getData()));
	}

	// /**
	// * Deserialize a {@link JsonNode} to the requested {@link Class type}.
	// * @param jsonNode must not be {@literal null}.
	// * @param type must not be {@literal null}.
	// * @return the deserialized object.
	// */
	// <T> Mono<T> deserialize(JsonNode jsonNode, Class<T> type) {
	//
	// Mono.create(sink -> {
	// try {
	// sink.success(mapper.reader().readValue(jsonNode.traverse()), type);
	// } catch (IOException e) {
	// return Mono.error(new VaultException("Cannot deserialize response", e));
	// }
	//
	// });
	//
	// try {
	// return this.mapper.reader().readValue(jsonNode.traverse(), type);
	// }
	// catch (IOException e) {
	// throw new VaultException("Cannot deserialize response", e);
	// }
	// }

	// /**
	// * Perform a read action within a callback that gets access to a session-bound
	// * {@link RestOperations} object. {@link HttpStatusCodeException} with
	// * {@link HttpStatus#NOT_FOUND} are translated to a {@literal Mono.empty()}
	// response.
	// * @param callback must not be {@literal null}.
	// * @return can be {@literal null}.
	// */
	// @Nullable
	// <T> Mono<T> doRead(Function<WebClient, ClientResponse> callback) {
	//
	// return vaultOperations.doWithSession((webClient) -> {
	//
	// try {
	// return callback.apply(webClient);
	// }
	// catch (HttpStatusCodeException e) {
	//
	// if (HttpStatusUtil.isNotFound(e.getStatusCode())) {
	// return null;
	// }
	//
	// throw VaultResponses.buildException(e, this.path);
	// }
	// });
	// }

	/**
	 * Write the {@code body} to the given Vault {@code path}.
	 * @param path must not be {@literal null} or empty.
	 * @param body
	 * @return the response of this write action.
	 */
	Mono<VaultResponse> doWrite(String path, @Nullable Object body) {
		return vaultOperations.write(path, body);
	}

	/**
	 * @param path must not be {@literal null} or empty.
	 * @return backend path representing the data path.
	 */
	abstract String createDataPath(String path);

}
