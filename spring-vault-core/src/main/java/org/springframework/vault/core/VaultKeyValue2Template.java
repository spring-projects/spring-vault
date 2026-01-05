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
import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * Default implementation of {@link VaultKeyValueOperations} for the key-value
 * secrets engine version 2.
 *
 * @author Mark Paluch
 * @author Younghwan Jang
 * @since 2.1
 */
class VaultKeyValue2Template extends VaultKeyValue2Accessor implements VaultKeyValueOperations {

	private final String path;


	/**
	 * Create a new {@link VaultKeyValue2Template} given {@link VaultOperations} and
	 * the mount {@code path}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	public VaultKeyValue2Template(VaultOperations vaultOperations, String path) {
		super(vaultOperations, path);
		this.path = path;
	}

	@Nullable
	@Override
	public VaultResponse get(String path) {
		Assert.hasText(path, "Path must not be empty");
		return doRead(path, Map.class, (response, data) -> {
			VaultResponse vaultResponse = new VaultResponse();
			vaultResponse.applyMetadata(response);
			vaultResponse.setData(data);
			return vaultResponse;
		});
	}

	@Nullable
	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
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
	public boolean patch(String path, Map<String, ?> patch) {
		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(patch, "Patch body must not be null");
		// To do patch operation, we need to do a read operation first
		VaultResponse readResponse = get(path);
		if (readResponse == null || readResponse.getData() == null) {
			throw new SecretNotFoundException(
					"No data found at %s; patch only works on existing data".formatted(createDataPath(path)),
					"%s/%s".formatted(this.path, path));
		}
		if (readResponse.getMetadata() == null) {
			throw new VaultException("Metadata must not be null");
		}
		Map<String, Object> body = KeyValueUtilities.createPatchRequest(patch, readResponse.getRequiredData(),
				readResponse.getMetadata());
		try {
			doWrite(createDataPath(path), body);
			return true;
		} catch (VaultException e) {
			if (e.getMessage() != null && (e.getMessage().contains("check-and-set")
					|| e.getMessage().contains("did not match the current version"))) {
				return false;
			}
			throw e;
		}
	}

	@Override
	public void put(String path, Object body) {
		Assert.hasText(path, "Path must not be empty");
		doWrite(createDataPath(path), Collections.singletonMap("data", body));
	}

}
