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

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * Default implementation of {@link VaultKeyValueOperations} for the Key/Value
 * backend version 1.
 *
 * @author Mark Paluch
 * @author Younghwan Jang
 * @see KeyValueBackend#KV_1
 * @since 2.1
 */
class VaultKeyValue1Template extends VaultKeyValueAccessor implements VaultKeyValueOperations {

	private final VaultOperations vaultOperations;

	private final String path;


	/**
	 * Create a new {@link VaultKeyValue1Template} given {@link VaultOperations} and
	 * the mount {@code path}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	public VaultKeyValue1Template(VaultOperations vaultOperations, String path) {
		super(vaultOperations, path);
		this.vaultOperations = vaultOperations;
		this.path = path;
	}


	@Override
	public @Nullable List<String> list(String path) {
		return this.vaultOperations.list(createDataPath(path));
	}

	@Override
	public @Nullable VaultResponse get(String path) {
		Assert.hasText(path, "Path must not be empty");
		return doRead(path, Map.class, (response, data) -> {
			VaultResponse vaultResponse = new VaultResponse();
			vaultResponse.applyMetadata(response);
			vaultResponse.setData(data);
			return vaultResponse;
		});
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> @Nullable VaultResponseSupport<T> get(String path, Class<T> responseType) {
		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(responseType, "Response type must not be null");
		return doRead(path, responseType, (response, data) -> {
			VaultResponseSupport result = response;
			result.setData(data);
			return result;
		});
	}

	@Override
	public boolean patch(String path, Map<String, ?> patch) {
		throw new IllegalStateException("K/V engine mount must be version 2 for patch support");
	}

	@Override
	public void put(String path, Object body) {
		Assert.hasText(path, "Path must not be empty");
		doWrite(createDataPath(path), body);
	}

	@Override
	public KeyValueBackend getApiVersion() {
		return KeyValueBackend.KV_1;
	}

	@Override
	Object getJsonNode(VaultResponseSupport<Object> response) {
		return response.getRequiredData();
	}

	@Override
	String createDataPath(String path) {

		if (this.path.endsWith("/")) {
			return this.path + path;
		}

		return "%s/%s".formatted(this.path, path);
	}

}
