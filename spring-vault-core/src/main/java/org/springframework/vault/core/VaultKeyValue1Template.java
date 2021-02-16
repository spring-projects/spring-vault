/*
 * Copyright 2018-2021 the original author or authors.
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

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * Default implementation of {@link VaultKeyValueOperations} for the Key/Value backend
 * version 1.
 *
 * @author Mark Paluch
 * @since 2.1
 * @see KeyValueBackend#KV_1
 */
class VaultKeyValue1Template extends VaultKeyValueAccessor implements VaultKeyValueOperations {

	private final VaultOperations vaultOperations;

	private final String path;

	/**
	 * Create a new {@link VaultKeyValue1Template} given {@link VaultOperations} and the
	 * mount {@code path}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	public VaultKeyValue1Template(VaultOperations vaultOperations, String path) {

		super(vaultOperations, path);

		this.vaultOperations = vaultOperations;
		this.path = path;
	}

	@Nullable
	@Override
	public List<String> list(String path) {
		return this.vaultOperations.list(createDataPath(path));
	}

	@Nullable
	@Override
	public VaultResponse get(String path) {

		Assert.hasText(path, "Path must not be empty");

		return doRead(path, Map.class, (response, data) -> {

			VaultResponse vaultResponse = new VaultResponse();
			vaultResponse.setRenewable(response.isRenewable());
			vaultResponse.setAuth(response.getAuth());
			vaultResponse.setLeaseDuration(response.getLeaseDuration());
			vaultResponse.setLeaseId(response.getLeaseId());
			vaultResponse.setMetadata(response.getMetadata());
			vaultResponse.setRequestId(response.getRequestId());
			vaultResponse.setWarnings(response.getWarnings());
			vaultResponse.setWrapInfo(response.getWrapInfo());
			vaultResponse.setData(data);

			return vaultResponse;
		});
	}

	@Nullable
	@Override
	@SuppressWarnings("unchecked")
	public <T> VaultResponseSupport<T> get(String path, Class<T> responseType) {

		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(responseType, "Response type must not be null");

		return doRead(path, responseType, (response, data) -> {

			VaultResponseSupport result = response;
			result.setData(data);
			return result;
		});
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
	JsonNode getJsonNode(VaultResponseSupport<JsonNode> response) {
		return response.getRequiredData();
	}

	@Override
	String createDataPath(String path) {
		return String.format("%s/%s", this.path, path);
	}

}
