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
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.JacksonCompat;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.support.Versioned.Metadata;
import org.springframework.vault.support.Versioned.Version;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Default implementation of {@link VaultVersionedKeyValueOperations}.
 *
 * @author Mark Paluch
 * @author Maciej Drozdzowski
 * @author Jeroen Willemsen
 * @since 2.1
 */
public class VaultVersionedKeyValueTemplate extends VaultKeyValue2Accessor implements VaultVersionedKeyValueOperations {

	private final VaultTemplate vaultOperations;

	private final String path;

	/**
	 * Create a new {@link VaultVersionedKeyValueTemplate} given {@link VaultOperations}
	 * and the mount {@code path}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	public VaultVersionedKeyValueTemplate(VaultOperations vaultOperations, String path) {

		super(vaultOperations, path);

		this.vaultOperations = VaultTemplate.from(vaultOperations);
		this.path = path;
	}

	@Nullable
	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Versioned<Map<String, Object>> get(String path, Version version) {

		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(version, "Version must not be null");

		return (Versioned) doRead(path, version, Map.class);
	}

	@Nullable
	@Override
	public <T> Versioned<T> get(String path, Version version, Class<T> responseType) {

		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(version, "Version must not be null");
		Assert.notNull(responseType, "Response type must not be null");

		return doRead(path, version, responseType);
	}

	@Nullable
	@SuppressWarnings({ "NullAway", "removal" })
	private <T> Versioned<T> doRead(String path, Version version, Class<T> responseType) {

		String secretPath = version.isVersioned()
				? "%s?version=%d".formatted(createDataPath(path), version.getVersion()) : createDataPath(path);

		Class<? extends VaultResponseSupport> responseTypeToUse;
		if (JacksonCompat.instance().isJackson3()) {
			responseTypeToUse = VersionedResponse.class;
		}
		else {
			responseTypeToUse = VersionedJackson2Response.class;
		}

		VaultResponseSupport<VaultResponseSupport<Object>> response = this.vaultOperations
			.doWithSessionClient((RestClientCallback<@Nullable VaultResponseSupport>) client -> {

				try {
					return client.get().uri(secretPath).retrieve().body(responseTypeToUse);
				}
				catch (HttpStatusCodeException e) {

					if (HttpStatusUtil.isNotFound(e.getStatusCode())) {
						if (e.getResponseBodyAsString().contains("deletion_time")) {
							return VaultResponses.unwrap(e.getResponseBodyAsString(), responseTypeToUse);
						}

						return null;
					}

					throw VaultResponses.buildException(e, path);
				}
			});

		if (response == null) {
			return null;
		}

		VaultResponseSupport<Object> data = response.getRequiredData();
		Metadata metadata = KeyValueUtilities.getMetadata(data.getMetadata());

		T body = deserialize(data.getRequiredData(), responseType);

		return Versioned.create(body, metadata);
	}

	@Override
	public Metadata put(String path, Object body) {

		Assert.hasText(path, "Path must not be empty");

		Map<Object, Object> data = new LinkedHashMap<>();
		Map<Object, Object> requestOptions = new LinkedHashMap<>();

		if (body instanceof Versioned<?> versioned) {

			data.put("data", versioned.getRequiredData());
			data.put("options", requestOptions);

			requestOptions.put("cas", versioned.getVersion().getVersion());
		}
		else {
			data.put("data", body);
		}

		VaultResponse response = doWrite(createDataPath(path), data);

		if (response == null) {
			throw new IllegalStateException(
					"VaultVersionedKeyValueOperations cannot be used with a Key-Value version 1 mount");
		}

		return KeyValueUtilities.getMetadata(response.getRequiredData());
	}

	@Override
	public void delete(String path, Version... versionsToDelete) {

		Assert.hasText(path, "Path must not be empty");
		Assert.noNullElements(versionsToDelete, "Versions must not be null");

		if (versionsToDelete.length == 0) {
			delete(path);
			return;
		}

		List<Integer> versions = toVersionList(versionsToDelete);

		doWrite(createBackendPath("delete", path), Collections.singletonMap("versions", versions));
	}

	private static List<Integer> toVersionList(Version[] versionsToDelete) {
		return Arrays.stream(versionsToDelete)
			.filter(Version::isVersioned)
			.map(Version::getVersion)
			.collect(Collectors.toList());
	}

	@Override
	public void undelete(String path, Version... versionsToDelete) {

		Assert.hasText(path, "Path must not be empty");
		Assert.noNullElements(versionsToDelete, "Versions must not be null");

		List<Integer> versions = toVersionList(versionsToDelete);

		doWrite(createBackendPath("undelete", path), Collections.singletonMap("versions", versions));
	}

	@Override
	public void destroy(String path, Version... versionsToDelete) {

		Assert.hasText(path, "Path must not be empty");
		Assert.noNullElements(versionsToDelete, "Versions must not be null");

		List<Integer> versions = toVersionList(versionsToDelete);

		doWrite(createBackendPath("destroy", path), Collections.singletonMap("versions", versions));
	}

	@Override
	public VaultKeyValueMetadataOperations opsForKeyValueMetadata() {
		return new VaultKeyValueMetadataTemplate(this.vaultOperations, this.path);
	}

}
