/*
 * Copyright 2023-2024 the original author or authors.
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

import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;

import static org.springframework.vault.core.ReactiveVaultTemplate.mapResponse;

/**
 * Base class for {@link ReactiveVaultVersionedKeyValueTemplate} and
 * {@link ReactiveVaultKeyValue2Template} and other Vault KV-accessing helpers, defining
 * common
 * <p/>
 * Not intended to be used directly. See {@link ReactiveVaultVersionedKeyValueTemplate}
 * and {@link ReactiveVaultKeyValue2Template}.
 *
 * @author Timothy R. Weiand
 * @author Mark Paluch
 * @since 3.1
 */
abstract class ReactiveVaultKeyValueAccessor implements ReactiveVaultKeyValueOperationsSupport {

	protected final ReactiveVaultOperations reactiveVaultOperations;

	private final String path;

	private final ObjectMapper mapper = new ObjectMapper();

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
		String dataPath = createDataPath(path);

		return reactiveVaultOperations
			.doWithSession(webClient -> webClient.delete()
				.uri(dataPath)
				.exchangeToMono(mapResponse(String.class, path, HttpMethod.DELETE)))
			.then();
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
			BiFunction<VaultResponseSupport<?>, I, T> mappingFunction) {

		ParameterizedTypeReference<VaultResponseSupport<JsonNode>> ref = VaultResponses
			.getTypeReference(JsonNode.class);

		Mono<VaultResponseSupport<JsonNode>> response = doRead(createDataPath(path), ref);

		return response.map(it -> {

			JsonNode jsonNode = getJsonNode(it);
			JsonNode jsonMeta = it.getRequiredData().at("/metadata");
			it.setMetadata(this.mapper.convertValue(jsonMeta, new TypeReference<>() {
			}));

			return mappingFunction.apply(it, deserialize(jsonNode, deserializeAs));
		});
	}

	/**
	 * Read a secret at {@code path} and deserialize the {@literal data} element to the
	 * given {@link ParameterizedTypeReference type}.
	 * @param path must not be {@literal null} or empty.
	 * @param typeReference must not be {@literal null}
	 * @return mapped value.
	 */
	<T> Mono<T> doRead(String path, ParameterizedTypeReference<T> typeReference) {
		return doRead((webClient) -> webClient.get().uri(path),
				new ResponseFunction<>(cr -> cr.toEntity(typeReference)));
	}

	/**
	 * Read a secret at {@code path} and deserialize the {@literal data} element to the
	 * given {@link ParameterizedTypeReference type}.
	 * @param path must not be {@literal null} or empty.
	 * @param typeReference must not be {@literal null}
	 * @return mapped value.
	 */
	<T> Mono<T> doRead(String path, Class<T> typeReference) {
		return doRead((webClient) -> webClient.get().uri(path),
				new ResponseFunction<>(cr -> cr.toEntity(typeReference)));
	}

	/**
	 * Deserialize a {@link JsonNode} to the requested {@link Class type}.
	 * @param jsonNode must not be {@literal null}.
	 * @param type must not be {@literal null}.
	 * @return the deserialized object.
	 */
	<T> T deserialize(JsonNode jsonNode, Class<T> type) {

		try {
			return this.mapper.reader().readValue(jsonNode.traverse(), type);
		}
		catch (IOException e) {
			throw new VaultException("Cannot deserialize response", e);
		}
	}

	/**
	 * Perform a read action within a callback that gets access to a session-bound
	 * {@link WebClient} object. {@link ClientResponse} with {@link HttpStatus#NOT_FOUND}
	 * are translated to a {@literal Mono.empty()} response.
	 * @param callback must not be {@literal null}.
	 * @param responseFunction must not be {@literal null}.
	 * @return can be {@literal null}.
	 */
	<T> Mono<T> doRead(Function<WebClient, RequestHeadersSpec<?>> callback,
			Function<ClientResponse, Mono<T>> responseFunction) {
		return this.reactiveVaultOperations
			.doWithSession((restOperations) -> callback.apply(restOperations).exchangeToMono(responseFunction));
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
	 * Return the {@link JsonNode} that contains the actual response body.
	 * @param response the response to extract the appropriate node from.
	 * @return the extracted {@link JsonNode}.
	 */
	abstract JsonNode getJsonNode(VaultResponseSupport<JsonNode> response);

	/**
	 * @param path must not be {@literal null} or empty.
	 * @return backend path representing the data path.
	 */
	abstract String createDataPath(String path);

	final class ResponseFunction<T> implements Function<ClientResponse, Mono<T>> {

		private final Function<ClientResponse, Mono<ResponseEntity<T>>> toEntity;

		public ResponseFunction(Function<ClientResponse, Mono<ResponseEntity<T>>> toEntity) {
			this.toEntity = toEntity;
		}

		@Override
		public Mono<T> apply(ClientResponse clientResponse) {

			if (HttpStatusUtil.isNotFound(clientResponse.statusCode())) {
				return Mono.empty();
			}

			if (clientResponse.statusCode().is2xxSuccessful()) {
				return toEntity.apply(clientResponse).mapNotNull(HttpEntity::getBody);
			}

			return clientResponse.bodyToMono(String.class)
				.flatMap(error -> Mono.error(VaultResponses.buildException(clientResponse.statusCode(), path, error)));
		}

	}

}
