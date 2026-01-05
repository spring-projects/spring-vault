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

import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.vault.support.JacksonCompat;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * Support class to build accessor methods for the Vault key-value secrets
 * engine version 2.
 *
 * @author Mark Paluch
 * @since 2.1
 * @see KeyValueBackend#KV_2
 */
abstract class VaultKeyValue2Accessor extends VaultKeyValueAccessor {

	private final String path;


	/**
	 * Create a new {@link VaultKeyValue2Accessor} given {@link VaultOperations} and
	 * the mount {@code path}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	VaultKeyValue2Accessor(VaultOperations vaultOperations, String path) {
		super(vaultOperations, path);
		this.path = path;
	}

	@Override
	@SuppressWarnings("unchecked")
	public @Nullable List<String> list(String path) {
		VaultListResponse read = doRead(client -> {
			return client.get()
					.path("%s?list=true"
							.formatted(createEnginePath("metadata", path)))
					.retrieve()
					.onStatus(HttpStatusUtil::isNotFound, HttpStatusUtil.proceed())
					.toEntity(VaultListResponse.class);
		});
		if (read == null || !read.hasData()) {
			return Collections.emptyList();
		}
		return (List<String>) read.getRequiredData().get("keys");
	}

	@Override
	public KeyValueBackend getApiVersion() {
		return KeyValueBackend.KV_2;
	}

	@Override
	Object getJsonNode(VaultResponseSupport<Object> response) {
		return JacksonCompat.instance().getAt(response.getRequiredData(), "/data");
	}

	@Override
	String createDataPath(String path) {
		return createEnginePath("data", path);
	}

	String createEnginePath(String segment, String path) {
		return "%s/%s/%s".formatted(PathUtil.stripSlashes(this.path), PathUtil.stripSlashes(segment),
				PathUtil.stripSlashes(path));
	}

}
