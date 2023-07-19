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

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.support.Versioned.Metadata;
import org.springframework.vault.support.Versioned.Metadata.MetadataBuilder;
import org.springframework.vault.support.Versioned.Version;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link ReactiveVaultVersionedKeyValueOperations}.
 *
 * @author Timothy R. Weiand
 * @since 3.999.999
 */
public class ReactiveVaultVersionedKeyValueTemplate extends ReactiveVaultKeyValue2Accessor
		implements ReactiveVaultVersionedKeyValueOperations {

	/**
	 * Create a new {@link ReactiveVaultVersionedKeyValueTemplate} given
	 * {@link ReactiveVaultOperations} and the mount {@code path}.
	 * @param reactiveVaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	public ReactiveVaultVersionedKeyValueTemplate(ReactiveVaultOperations reactiveVaultOperations, String path) {

		super(reactiveVaultOperations, path);
	}

	private static Metadata getMetadata(Map<String, Object> responseMetadata) {

		MetadataBuilder builder = Metadata.builder();
		TemporalAccessor created_time = getDate(responseMetadata, "created_time");
		TemporalAccessor deletion_time = getDate(responseMetadata, "deletion_time");

		builder.createdAt(Instant.from(created_time));

		if (deletion_time != null) {
			builder.deletedAt(Instant.from(deletion_time));
		}

		if (Boolean.TRUE.equals(responseMetadata.get("destroyed"))) {
			builder.destroyed();
		}

		Integer version = (Integer) responseMetadata.get("version");
		builder.version(Version.from(version));

		return builder.build();
	}

	@Nullable
	private static TemporalAccessor getDate(Map<String, Object> responseMetadata, String key) {

		String date = (String) responseMetadata.getOrDefault(key, "");
		if (StringUtils.hasText(date)) {
			return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(date);
		}
		return null;
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
			.map(m -> Versioned.create((Map<String, Object>) m.getData(), m.getMetadata()));
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
				? String.format("%s?version=%d", createDataPath(path), version.getVersion()) : createDataPath(path);

		var ref = VaultResponses.getTypeReference(VaultResponses.getTypeReference(responseType));

		return doReadRaw(secretPath, ref).onErrorResume(WebClientResponseException.NotFound.class, e -> {
			if (e.getResponseBodyAsString().contains("deletion_time")) {
				return Mono.justOrEmpty(e.getResponseBodyAs(ref));
			}
			return Mono.error(VaultResponses.buildException(e.getStatusCode(), path, "Unexpected error during read"));
		}).flatMap(ReactiveKeyValueHelper::getRequiredData).flatMap(responseSupport -> {
			var metadataMap = responseSupport.getMetadata();
			if (null == metadataMap) {
				return Mono.just(Versioned.create(responseSupport.getData(), version));
			}
			var metadata = getMetadata(responseSupport.getMetadata());
			return Mono.just(Versioned.create(responseSupport.getData(), metadata));
		});
	}

	@Override
	public Mono<Metadata> put(String path, Object body) {

		Assert.hasText(path, "Path must not be empty");

		var data = new LinkedHashMap<>();
		var requestOptions = new LinkedHashMap<>();

		if (body instanceof Versioned<?> versioned) {

			data.put("data", versioned.getData());
			data.put("options", requestOptions);

			requestOptions.put("cas", versioned.getVersion().getVersion());
		}
		else {
			data.put("data", body);
		}

		return doWrite(createDataPath(path), data).flatMap(ReactiveKeyValueHelper::getRequiredData)
			.map(ReactiveVaultVersionedKeyValueTemplate::getMetadata)
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

		var versions = toVersionList(versionsToDelete);

		return doWrite(createBackendPath("delete", path), Collections.singletonMap("versions", versions)).then().log();
	}

	@Override
	public Mono<Void> undelete(String path, Version... versionsToDelete) {

		Assert.hasText(path, "Path must not be empty");
		Assert.noNullElements(versionsToDelete, "Versions must not be null");

		var versions = toVersionList(versionsToDelete);

		return doWrite(createBackendPath("undelete", path), Collections.singletonMap("versions", versions)).then();
	}

	@Override
	public Mono<Void> destroy(String path, Version... versionsToDelete) {

		Assert.hasText(path, "Path must not be empty");
		Assert.noNullElements(versionsToDelete, "Versions must not be null");

		var versions = toVersionList(versionsToDelete);

		return doWrite(createBackendPath("destroy", path), Collections.singletonMap("versions", versions)).then();
	}

	@Override
	public ReactiveVaultKeyValueMetadataOperations opsForKeyValueMetadata() {
		return new ReactiveVaultKeyValueMetadataTemplate(reactiveVaultOperations, path);
	}

	private static class VersionedResponse<T> extends VaultResponseSupport<VaultResponseSupport<T>> {

	}

}
