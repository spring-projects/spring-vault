/*
 * Copyright 2018-2020 the original author or authors.
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
import java.util.HashMap;
import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * Default implementation of {@link VaultKeyValueOperations} for the key-value backend
 * version 2.
 *
 * @author Mark Paluch
 * @since 2.1
 */
class VaultKeyValue2Template extends VaultKeyValue2Accessor implements VaultKeyValueOperations {

	/**
	 * Create a new {@link VaultKeyValue2Template} given {@link VaultOperations} and the
	 * mount {@code path}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	public VaultKeyValue2Template(VaultOperations vaultOperations, String path) {
		super(vaultOperations, path);
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

		doWrite(createDataPath(path), Collections.singletonMap("data", body));
	}

	/**
	 * Performs a KV Patch operation.
	 * @param path must not be {@literal null} or empty.
	 * @param kv New key value map to be updated
	 * @since 2.3
	 */
	@Override
	public boolean patch(String path, Map<String, ?> kv) {
		Assert.hasText(path, "Path must not be empty");

		// To do patch operation, we need to do a read operation first
		VaultResponse readResponse = get(path);
		if (null == readResponse) {
			throw new VaultException("VaultResponse must not be null");
		} else if (null == readResponse.getData()) {
			throw new SecretNotFoundException("No data found at %s; patch only works on existing data");
		} else if (null == readResponse.getMetadata()) {
			throw new VaultException("Metadata must not be null");
		}
		Map<String, Object> data = readResponse.getData();
		Map<String, Object> metadata = readResponse.getMetadata();
		kv.forEach(data::put);
		Map<String, Object> body = new HashMap<>();
		body.put("data", data);
		body.put("options", Collections.singletonMap("cas", metadata.get("version")));

		VaultResponse writeResponse = doWrite(createDataPath(path), body);
		if (null == writeResponse) {
			return false;
		}
		Map<String, Object> writeResponseData = writeResponse.getData();
		return null != writeResponseData;
	}

}
