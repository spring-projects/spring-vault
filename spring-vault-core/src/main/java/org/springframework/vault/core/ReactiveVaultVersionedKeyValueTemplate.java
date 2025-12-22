/*
 * Copyright 2018-2025 the original author or authors.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;

import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.support.Versioned.Metadata;
import org.springframework.vault.support.Versioned.Version;
import org.springframework.web.reactive.function.client.ClientResponse;

/**
 * Default implementation of {@link ReactiveVaultVersionedKeyValueOperations}.
 *
 * @author Timothy R. Weiand
 * @since 3.1
 */
public class ReactiveVaultVersionedKeyValueTemplate extends ReactiveVaultKeyValue2Accessor
		implements ReactiveVaultVersionedKeyValueOperations {

	private final String path;


	/**
	 * Create a new {@link ReactiveVaultVersionedKeyValueTemplate} given
	 * {@link ReactiveVaultOperations} and the mount {@code path}.
	 * @param reactiveVaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	public ReactiveVaultVersionedKeyValueTemplate(ReactiveVaultOperations reactiveVaultOperations, String path) {
		super(reactiveVaultOperations, path);
		this.path = path;
	}


	private static List<Integer> toVersionList(Version[] versionsToDelete) {
		return Arrays.stream(versionsToDelete)
				.filter(Version::isVersioned)
				.map(Version::getVersion)
				.collect(Collectors.toList());
	}

	@Override
	@SuppressWarnings("unchecked")
	public Mono<Versioned<Map<String, Object>>> get(String path, Version version) {
		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(version, "Version must not be null");
		return doRead(path, version, Map.class)
				.map(m -> Versioned.create((Map<String, Object>) m.getData(), m.getRequiredMetadata()));
	}

	@Override
	public <T> Mono<Versioned<T>> get(String path, Version version, Class<T> responseType) {
		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(version, "Version must not be null");
		Assert.notNull(responseType, "Response type must not be null");
		return doRead(path, version, responseType);
	}

	private <T> Mono<Versioned<T>> doRead(String path, Version version, Class<T> responseType) {
		String secretPath = version.isVersioned()
				? "%s?version=%d".formatted(createDataPath(path), version.getVersion())
				: createDataPath(path);

		Mono<VersionedResponse> versionedResponseMono = doReadVersioned(secretPath);
		return versionedResponseMono.map(response -> {
			VaultResponseSupport<JsonNode> data = response.getRequiredData();
			Metadata metadata = KeyValueUtilities.getMetadata(data.getMetadata());
			T body = deserialize(data.getRequiredData(), responseType);
			return Versioned.create(body, metadata);
		});
	}

	@Override
	public Mono<Metadata> put(String path, Object body) {
		Assert.hasText(path, "Path must not be empty");
		LinkedHashMap<Object, Object> data = new LinkedHashMap<>();
		LinkedHashMap<Object, Object> requestOptions = new LinkedHashMap<>();
		if (body instanceof Versioned<?> versioned) {
			data.put("data", versioned.getData());
			data.put("options", requestOptions);
			requestOptions.put("cas", versioned.getVersion().getVersion());
		} else {
			data.put("data", body);
		}
		return doWrite(createDataPath(path), data).map(VaultResponseSupport::getRequiredData)
				.map(KeyValueUtilities::getMetadata)
				.switchIfEmpty(Mono.error(new IllegalStateException(
						"VaultVersionedKeyValueOperations cannot be used with a Key-Value version 1 mount")));
	}

	@Override
	public Mono<Void> delete(String path, Version... versionsToDelete) {
		Assert.hasText(path, "Path must not be empty");
		Assert.noNullElements(versionsToDelete, "Versions must not be null");
		if (versionsToDelete.length == 0) {
			return delete(path);
		}
		List<Integer> versions = toVersionList(versionsToDelete);
		return doWrite(createEnginePath("delete", path), Collections.singletonMap("versions", versions)).then().log();
	}

	@Override
	public Mono<Void> undelete(String path, Version... versionsToDelete) {
		Assert.hasText(path, "Path must not be empty");
		Assert.noNullElements(versionsToDelete, "Versions must not be null");
		List<Integer> versions = toVersionList(versionsToDelete);
		return doWrite(createEnginePath("undelete", path), Collections.singletonMap("versions", versions)).then();
	}

	@Override
	public Mono<Void> destroy(String path, Version... versionsToDelete) {
		Assert.hasText(path, "Path must not be empty");
		Assert.noNullElements(versionsToDelete, "Versions must not be null");
		List<Integer> versions = toVersionList(versionsToDelete);
		return doWrite(createEnginePath("destroy", path), Collections.singletonMap("versions", versions)).then();
	}

	@Override
	public ReactiveVaultKeyValueMetadataOperations opsForKeyValueMetadata() {
		return new ReactiveVaultKeyValueMetadataTemplate(reactiveVaultOperations, path);
	}

	/**
	 * Read a secret at {@code path} and read it into {@link VersionedResponse}.
	 * @param path must not be {@literal null} or empty.
	 * @return mapped value.
	 */
	<T> Mono<VersionedResponse> doReadVersioned(String path) {
		Function<ClientResponse, Mono<ResponseEntity<VersionedResponse>>> toEntity = cr -> cr
				.toEntity(VersionedResponse.class);
		ResponseFunction<VersionedResponse> defaults = new ResponseFunction<>(toEntity);
		Function<ClientResponse, Mono<VersionedResponse>> responseFunction = clientResponse -> {
			if (HttpStatusUtil.isNotFound(clientResponse.statusCode())) {
				return clientResponse.bodyToMono(String.class).flatMap(it -> {
					if (it.contains("deletion_time")) {
						return Mono.justOrEmpty(VaultResponses.unwrap(it, VersionedResponse.class));
					}
					return Mono.empty();
				});
			}
			return defaults.apply(clientResponse);
		};
		return doRead((webClient) -> webClient.get().uri(path), responseFunction);
	}

}
