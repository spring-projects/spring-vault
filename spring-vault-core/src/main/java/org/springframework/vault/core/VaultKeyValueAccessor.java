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

import java.io.IOException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

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

	private final VaultOperations vaultOperations;

	private final String path;

	private final ObjectMapper mapper;


	/**
	 * Create a new {@link VaultKeyValueAccessor} given {@link VaultOperations} and
	 * the mount {@code path}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	VaultKeyValueAccessor(VaultOperations vaultOperations, String path) {
		Assert.notNull(vaultOperations, "VaultOperations must not be null");
		Assert.hasText(path, "Path must not be empty");
		this.vaultOperations = vaultOperations;
		this.path = path;
		this.mapper = extractObjectMapper(vaultOperations);
	}


	@Override
	public void delete(String path) {
		Assert.hasText(path, "Path must not be empty");
		this.vaultOperations.doWithSession((restOperations -> {
			restOperations.exchange(createDataPath(path), HttpMethod.DELETE, null, Void.class);

			return null;
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
	@Nullable
	<I, T> T doRead(String path, Class<I> deserializeAs, BiFunction<VaultResponseSupport<?>, I, T> mappingFunction) {
		ParameterizedTypeReference<VaultResponseSupport<JsonNode>> ref = VaultResponses
				.getTypeReference(JsonNode.class);
		VaultResponseSupport<JsonNode> response = doRead(createDataPath(path), ref);
		if (response != null) {
			JsonNode jsonNode = getJsonNode(response);
			JsonNode jsonMeta = response.getRequiredData().at("/metadata");
			response.setMetadata(this.mapper.convertValue(jsonMeta, new TypeReference<>() {
			}));
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
	@Nullable
	<T> T doRead(String path, ParameterizedTypeReference<T> typeReference) {

		return doRead((restOperations) -> {
			return restOperations.exchange(path, HttpMethod.GET, null, typeReference);
		});
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
		} catch (IOException e) {
			throw new VaultException("Cannot deserialize response", e);
		}
	}

	/**
	 * Perform a read action within a callback that gets access to a session-bound
	 * {@link RestOperations} object. {@link HttpStatusCodeException} with
	 * {@link HttpStatus#NOT_FOUND} are translated to a {@literal null} response.
	 * @param callback must not be {@literal null}.
	 * @return can be {@literal null}.
	 */
	@Nullable
	<T> T doRead(Function<RestOperations, ResponseEntity<T>> callback) {
		return this.vaultOperations.doWithSession((restOperations) -> {
			try {
				return callback.apply(restOperations).getBody();
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
	VaultResponse doWrite(String path, Object body) {
		Assert.hasText(path, "Path must not be empty");
		try {

			return this.vaultOperations.doWithSession((restOperations) -> {
				return restOperations.exchange(path, HttpMethod.POST, new HttpEntity<>(body), VaultResponse.class)
						.getBody();
			});
		} catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e, path);
		}
	}

	/**
	 * Return the {@link JsonNode} that contains the actual response body.
	 * @param response the response to extract the appropriate node from.
	 * @return the extracted {@link JsonNode}.
	 */
	abstract JsonNode getJsonNode(VaultResponseSupport<JsonNode> response);

	/**
	 * @param path must not be {@literal null} or empty.
	 * @return secrets engine path representing the data path.
	 */
	abstract String createDataPath(String path);

	private static ObjectMapper extractObjectMapper(VaultOperations vaultOperations) {

		Optional<ObjectMapper> mapper = vaultOperations.doWithSession(operations -> {

			if (operations instanceof RestTemplate template) {

				Optional<AbstractJackson2HttpMessageConverter> jackson2Converter = template.getMessageConverters()
						.stream()
						.filter(AbstractJackson2HttpMessageConverter.class::isInstance) //
						.map(AbstractJackson2HttpMessageConverter.class::cast) //
						.findFirst();

				return jackson2Converter.map(AbstractJackson2HttpMessageConverter::getObjectMapper);
			}

			return Optional.empty();
		});

		return mapper.orElseGet(ObjectMapper::new);
	}

}
