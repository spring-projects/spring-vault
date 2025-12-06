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

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.Assert;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.JacksonCompat;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.reactive.function.client.ClientResponse;

/**
 * Base class for {@link ReactiveVaultVersionedKeyValueTemplate} and
 * {@link ReactiveVaultKeyValue2Template} and other Vault KV-accessing helpers,
 * defining common
 * <p>Not intended to be used directly. See
 * {@link ReactiveVaultVersionedKeyValueTemplate} and
 * {@link ReactiveVaultKeyValue2Template}.
 *
 * @author Timothy R. Weiand
 * @author Mark Paluch
 * @since 3.1
 */
abstract class ReactiveVaultKeyValueAccessor implements ReactiveVaultKeyValueOperationsSupport {

	protected final ReactiveVaultTemplate reactiveVaultOperations;

	private final String path;

	private final JacksonCompat.ObjectMapperAccessor mapper;


	/**
	 * Create a new {@link ReactiveVaultKeyValueAccessor} given
	 * {@link ReactiveVaultOperations} and the mount {@code path}.
	 * @param reactiveVaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	ReactiveVaultKeyValueAccessor(ReactiveVaultTemplate reactiveVaultOperations, String path) {
		Assert.notNull(reactiveVaultOperations, "ReactiveVaultOperations must not be null");
		Assert.hasText(path, "Path must not be empty");
		this.reactiveVaultOperations = reactiveVaultOperations;
		this.path = path;
		this.mapper = JacksonCompat.instance().getObjectMapperAccessor();
	}


	@Override
	public Mono<Void> delete(String path) {
		Assert.hasText(path, "Path must not be empty");
		String dataPath = createDataPath(path);
		return reactiveVaultOperations
				.doWithSessionClient(
						webClient -> webClient.delete().path(dataPath).retrieve().toBodilessEntity().then());
	}

	/**
	 * Read a secret at {@code path} and deserialize the {@literal data} element to
	 * the given {@link Class type}.
	 * @param path must not be {@literal null}.
	 * @param deserializeAs must not be {@literal null}.
	 * @param mappingFunction Mapping function to convert from the intermediate to
	 * the target data type. Must not be {@literal null}.
	 * @param <I> intermediate data type for {@literal data} deserialization.
	 * @param <T> return type. Value is created by the {@code mappingFunction}.
	 * @return mapped value.
	 */
	<I, T> Mono<T> doRead(String path, Class<I> deserializeAs,
			BiFunction<VaultResponseSupport<?>, I, T> mappingFunction) {
		ParameterizedTypeReference<VaultResponseSupport<Object>> ref = VaultResponses
				.getTypeReference(JacksonCompat.instance().getJsonNodeClass());
		return doRead(createDataPath(path), response -> {
			return response.bodyToMono(ref).filter(VaultResponseSupport::hasData).map(it -> {
				Object jsonNode = getJsonNode(it);
				Object jsonMeta = JacksonCompat.instance().getAt(it.getRequiredData(), "/metadata");
				it.setMetadata(this.mapper.deserialize(jsonMeta, Map.class));
				return mappingFunction.apply(it, deserialize(jsonNode, deserializeAs));
			});
		});
	}

	/**
	 * Read a secret at {@code path}.
	 * @param path must not be {@literal null} or empty.
	 * @param responseFunction function to perform the actual reading.
	 * @return mapped value.
	 */
	<T> Mono<T> doRead(String path, Function<ClientResponse, Mono<T>> responseFunction) {
		return reactiveVaultOperations.doWithSessionClient((client) -> client.get().path(path).exchangeToMono(it -> {
			if (HttpStatusUtil.isNotFound(it.statusCode())) {
				return Mono.empty();
			}
			if (!it.statusCode().is2xxSuccessful()) {
				return it.createError();
			}
			return responseFunction.apply(it);
		}));
	}

	/**
	 * Read a secret at {@code path} and deserialize the {@literal data} element to
	 * the given {@link ParameterizedTypeReference type}.
	 * @param path must not be {@literal null} or empty.
	 * @param typeReference must not be {@literal null}
	 * @return mapped value.
	 */
	<T> Mono<T> doRead(String path, Class<T> typeReference) {
		return reactiveVaultOperations.doWithSessionClient((client) -> client.get()
				.path(path)
				.retrieve()
				.onStatus(HttpStatusUtil::isNotFound, response -> Mono.empty())
				.onStatus(Predicate.not(HttpStatusCode::is2xxSuccessful), ClientResponse::createError)
				.bodyToMono(typeReference));
	}

	/**
	 * Deserialize a {@code JsonNode} to the requested {@link Class type}.
	 * @param jsonNode must not be {@literal null}.
	 * @param type must not be {@literal null}.
	 * @return the deserialized object.
	 */
	<T> T deserialize(Object jsonNode, Class<T> type) {
		return this.mapper.deserialize(jsonNode, type);
	}

	/**
	 * Write the {@code body} to the given Vault {@code path}.
	 * @param path must not be {@literal null} or empty.
	 * @param body the body to write.
	 * @return the response of this write action.
	 */
	Mono<VaultResponse> doWrite(String path, @Nullable Object body) {
		Assert.hasText(path, "Path must not be empty");
		return reactiveVaultOperations.write(path, body);
	}

	/**
	 * Return the {@code JsonNode} that contains the actual response body.
	 * @param response the response to extract the appropriate node from.
	 * @return the extracted {@code JsonNode}.
	 */
	abstract Object getJsonNode(VaultResponseSupport<Object> response);

	/**
	 * @param path must not be {@literal null} or empty.
	 * @return backend path representing the data path.
	 */
	abstract String createDataPath(String path);

}
