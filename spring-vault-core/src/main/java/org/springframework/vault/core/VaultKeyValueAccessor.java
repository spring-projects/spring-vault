/*
 * Copyright 2018-present the original author or authors.
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

import org.jspecify.annotations.Nullable;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.JacksonCompat;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * Base class for {@link VaultVersionedKeyValueTemplate} and
 * {@link VaultKeyValue2Template} and other Vault KV-accessing helpers, defining
 * common
 * <p>Not intended to be used directly. See
 * {@link VaultVersionedKeyValueTemplate} and {@link VaultKeyValue2Template}.
 *
 * @author Mark Paluch
 * @since 2.1
 */
abstract class VaultKeyValueAccessor implements VaultKeyValueOperationsSupport {

	private final VaultTemplate vaultOperations;

	private final String path;

	private final JacksonCompat.ObjectMapperAccessor mapper;


	/**
	 * Create a new {@link VaultKeyValueAccessor} given {@link VaultOperations} and
	 * the mount {@code path}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	VaultKeyValueAccessor(VaultOperations vaultOperations, String path) {
		Assert.notNull(vaultOperations, "VaultOperations must not be null");
		Assert.hasText(path, "Path must not be empty");
		this.vaultOperations = VaultTemplate.from(vaultOperations);
		this.path = path;
		this.mapper = JacksonCompat.ObjectMapperAccessor.from(vaultOperations);
	}


	@Override
	@SuppressWarnings("NullAway")
	public void delete(String path) {
		Assert.hasText(path, "Path must not be empty");
		this.vaultOperations.doWithSessionClient((RestClientCallback<@Nullable Void>) (client -> {
			return client.delete().uri(createDataPath(path)).retrieve().toBodilessEntity().getBody();
		}));
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
	<I, T> @Nullable T doRead(String path, Class<I> deserializeAs,
			BiFunction<VaultResponseSupport<?>, I, T> mappingFunction) {
		ParameterizedTypeReference<VaultResponseSupport<Object>> ref = VaultResponses
				.getTypeReference(JacksonCompat.instance().getJsonNodeClass());
		VaultResponseSupport<Object> response = doRead(createDataPath(path), ref);
		if (response != null) {
			Object jsonNode = getJsonNode(response);
			Object jsonMeta = JacksonCompat.instance().getAt(response.getRequiredData(), "/metadata");
			response.setMetadata(this.mapper.deserialize(jsonMeta, Map.class));
			return mappingFunction.apply(response, deserialize(jsonNode, deserializeAs));
		}
		return null;
	}

	/**
	 * Read a secret at {@code path} and deserialize the {@literal data} element to
	 * the given {@link ParameterizedTypeReference type}.
	 * @param path must not be {@literal null} or empty.
	 * @param typeReference must not be {@literal null}
	 * @return mapped value.
	 */
	<T> @Nullable T doRead(String path, ParameterizedTypeReference<T> typeReference) {
		return doRead((client) -> client.get().uri(path).retrieve().toEntity(typeReference));
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
	 * Perform a read action within a callback that gets access to a session-bound
	 * {@link RestClient} object. {@link HttpStatusCodeException} with
	 * {@link HttpStatus#NOT_FOUND} are translated to a {@literal null} response.
	 * @param callback must not be {@literal null}.
	 * @return can be {@literal null}.
	 */
	@SuppressWarnings("NullAway")
	<T extends @Nullable Object> @Nullable T doRead(Function<RestClient, ResponseEntity<T>> callback) {
		return this.vaultOperations.doWithSessionClient((RestClientCallback<@Nullable T>) (client) -> {
			try {
				return callback.apply(client).getBody();
			} catch (HttpStatusCodeException e) {
				if (HttpStatusUtil.isNotFound(e.getStatusCode())) {
					return null;
				}
				throw VaultResponses.buildException(e, this.path);
			}
		});
	}

	/**
	 * Write the {@code body} to the given Vault {@code path}.
	 * @param path must not be {@literal null} or empty.
	 * @param body the body to write.
	 * @return the response of this write action.
	 */
	@Nullable
	@SuppressWarnings("NullAway")
	VaultResponse doWrite(String path, Object body) {
		Assert.hasText(path, "Path must not be empty");
		try {
			return this.vaultOperations.doWithSessionClient((RestClientCallback<@Nullable VaultResponse>) (client) -> {
				return client.post().uri(path).body(body).retrieve().body(VaultResponse.class);
			});
		} catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e, path);
		}
	}

	/**
	 * Return the {@code JsonNode} that contains the actual response body.
	 * @param response the response to extract the appropriate node from.
	 * @return the extracted {@code JsonNode}.
	 */
	abstract Object getJsonNode(VaultResponseSupport<Object> response);

	/**
	 * @param path must not be {@literal null} or empty.
	 * @return secrets engine path representing the data path.
	 */
	abstract String createDataPath(String path);

}
