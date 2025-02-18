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

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpMethod;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * Support class to build accessor methods for the Vault key-value backend version 2.
 *
 * @author Mark Paluch
 * @since 2.1
 * @see KeyValueBackend#KV_2
 */
abstract class VaultKeyValue2Accessor extends VaultKeyValueAccessor {

	private final String path;

	/**
	 * Create a new {@link VaultKeyValue2Accessor} given {@link VaultOperations} and the
	 * mount {@code path}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	VaultKeyValue2Accessor(VaultOperations vaultOperations, String path) {

		super(vaultOperations, path);

		this.path = path;
	}

	@Nullable
	@Override
	@SuppressWarnings("unchecked")
	public List<String> list(String path) {

		VaultListResponse read = doRead(restOperations -> {
			return restOperations.exchange(
					"%s?list=true".formatted(createBackendPath("metadata", KeyValueUtilities.normalizeListPath(path))),
					HttpMethod.GET, null, VaultListResponse.class);
		});

		if (read == null) {
			return Collections.emptyList();
		}

		return (List<String>) read.getRequiredData().get("keys");
	}

	@Override
	public KeyValueBackend getApiVersion() {
		return KeyValueBackend.KV_2;
	}

	@Override
	JsonNode getJsonNode(VaultResponseSupport<JsonNode> response) {
		return response.getRequiredData().at("/data");
	}

	@Override
	String createDataPath(String path) {
		return createBackendPath("data", path);
	}

	String createBackendPath(String segment, String path) {
		return "%s/%s/%s".formatted(this.path, segment, path);
	}

}
