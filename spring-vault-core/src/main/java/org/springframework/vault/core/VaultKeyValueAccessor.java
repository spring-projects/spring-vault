/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.vault.core;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
 * Base class for {@link VaultVersionedKeyValueTemplate} and other Vault KV-accessing
 * helpers, defining common
 * <p/>
 * Not intended to be used directly. See {@link VaultVersionedKeyValueTemplate}.
 *
 * @author Mark Paluch
 * @since 2.1
 */
public abstract class VaultKeyValueAccessor implements VaultKeyValueOperationsSupport {

	private final VaultOperations vaultOperations;

	private final String path;

	private final ObjectMapper mapper;

	/**
	 * Create a new {@link VaultKeyValueAccessor} given {@link VaultOperations} and the
	 * mount {@code path}.
	 *
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	public VaultKeyValueAccessor(VaultOperations vaultOperations, String path) {

		Assert.notNull(vaultOperations, "VaultOperations must not be null");
		Assert.hasText(path, "Path must not be empty");

		this.vaultOperations = vaultOperations;
		this.path = path;
		this.mapper = extractObjectMapper(vaultOperations);
	}

	@Nullable
	@Override
	@SuppressWarnings("unchecked")
	public List<String> list(String path) {

		String pathToUse = path.equals("/") ? "" : path.endsWith("/") ? path
				: (path + "/");

		VaultListResponse read = doRead((restOperations, httpEntity) -> {
			return restOperations.exchange(String.format("%s?list=true",
					createBackendPath("metadata", pathToUse)), HttpMethod.GET,
					httpEntity, VaultListResponse.class);
		});

		if (read == null) {
			return Collections.emptyList();
		}

		return (List<String>) read.getRequiredData().get("keys");
	}

	@Override
	public void delete(String path) {

		Assert.hasText(path, "Path must not be empty");

		doWithSession(((restOperations, httpHeaders) -> {
			restOperations.exchange(createDataPath(path), HttpMethod.DELETE,
					new HttpEntity<>(httpHeaders), Void.class);

			return null;
		}));
	}

	/**
	 * Read a secret at {@code path} and deserialize the nested {@literal data} element to
	 * the given {@link Class type}.
	 *
	 * @param path must not be {@literal null}.
	 * @param deserializeAs must not be {@literal null}.
	 * @param mappingFunction Mapping function to convert from the intermediate to the
	 * target data type. Must not be {@literal null}.
	 * @param <I> intermediate data type for {@literal data} deserialization.
	 * @param <T> return type. Value is created by the {@code mappingFunction}.
	 * @return mapped value.
	 */
	@Nullable
	<I, T> T doRead(String path, Class<I> deserializeAs,
			BiFunction<VaultResponseSupport<?>, I, T> mappingFunction) {

		ParameterizedTypeReference<VaultResponseSupport<JsonNode>> ref = VaultResponses
				.getTypeReference(JsonNode.class);

		VaultResponseSupport<JsonNode> response = doRead(createDataPath(path), ref);

		if (response != null) {

			JsonNode jsonNode = response.getRequiredData().at("/data");

			return mappingFunction.apply(response, deserialize(jsonNode, deserializeAs));
		}

		return null;
	}

	<T> T deserialize(JsonNode jsonNode, Class<T> type) {

		try {
			return mapper.reader().readValue(jsonNode.traverse(), type);
		}
		catch (IOException e) {
			throw new VaultException("Cannot deserialize response", e);
		}
	}

	@Nullable
	<T> T doRead(String path, ParameterizedTypeReference<T> typeReference) {

		return doRead((restOperations, httpEntity) -> {
			return restOperations.exchange(path, HttpMethod.GET, httpEntity,
					typeReference);
		});
	}

	@Nullable
	private <T> T doRead(
			BiFunction<RestOperations, HttpEntity<?>, ResponseEntity<T>> callback) {

		return doWithSession((restOperations, headers) -> {

			try {
				return callback.apply(restOperations, new HttpEntity<>(headers))
						.getBody();
			}
			catch (HttpStatusCodeException e) {

				if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
					return null;
				}

				throw VaultResponses.buildException(e, path);
			}
		});
	}

	@Nullable
	VaultResponse doWrite(String path, Object body) {

		Assert.hasText(path, "Path must not be empty");

		try {

			return doWithSession((restOperations, httpHeaders) -> {
				return restOperations.exchange(path, HttpMethod.POST,
						new HttpEntity<>(body, httpHeaders), VaultResponse.class)
						.getBody();
			});
		}
		catch (HttpStatusCodeException e) {
			throw VaultResponses.buildException(e, path);
		}
	}

	@Nullable
	<T> T doWithSession(BiFunction<RestOperations, HttpHeaders, T> callback) {

		return vaultOperations.doWithSession(restOperations -> {

			HttpHeaders headers = new HttpHeaders();
			headers.set("X-Vault-Kv-Client", "v2");

			return callback.apply(restOperations, headers);
		});
	}

	String createDataPath(String path) {
		return createBackendPath("data", path);
	}

	String createBackendPath(String segment, String path) {
		return String.format("%s/%s/%s", this.path, segment, path);
	}

	private static ObjectMapper extractObjectMapper(VaultOperations vaultOperations) {

		Optional<ObjectMapper> mapper = vaultOperations
				.doWithSession(operations -> {

					if (operations instanceof RestTemplate) {

						RestTemplate template = (RestTemplate) operations;

						Optional<AbstractJackson2HttpMessageConverter> jackson2Converter = template
								.getMessageConverters()
								.stream()
								//
								.filter(AbstractJackson2HttpMessageConverter.class::isInstance) //
								.map(AbstractJackson2HttpMessageConverter.class::cast) //
								.findFirst();

						return jackson2Converter
								.map(AbstractJackson2HttpMessageConverter::getObjectMapper);
					}

					return Optional.empty();
				});

		return mapper.orElseGet(ObjectMapper::new);
	}
}
