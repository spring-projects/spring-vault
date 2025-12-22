/*
 * Copyright 2020-2025 the original author or authors.
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

import java.util.Map;

import org.springframework.util.Assert;
import org.springframework.vault.support.VaultMetadataRequest;
import org.springframework.vault.support.VaultMetadataResponse;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * Default implementation of {@link VaultKeyValueMetadataOperations}.
 *
 * @author Zakaria Amine
 * @author Mark Paluch
 * @author Jeroen Willemsen
 * @since 2.3
 */
class VaultKeyValueMetadataTemplate implements VaultKeyValueMetadataOperations {

	private final VaultOperations vaultOperations;

	private final String basePath;


	VaultKeyValueMetadataTemplate(VaultOperations vaultOperations, String basePath) {
		Assert.notNull(vaultOperations, "VaultOperations must not be null");
		this.vaultOperations = vaultOperations;
		this.basePath = basePath;
	}


	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public VaultMetadataResponse get(String path) {
		VaultResponseSupport<Map> response = this.vaultOperations.read(getPath(path), Map.class);
		return response != null && response.hasData() ? KeyValueUtilities.fromMap(response.getRequiredData()) : null;
	}

	@Override
	public void put(String path, VaultMetadataRequest body) {
		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(body, "Body must not be null");
		this.vaultOperations.write(getPath(path), body);
	}

	@Override
	public void delete(String path) {

		Assert.hasText(path, "Path must not be empty");

		this.vaultOperations.delete(getPath(path));
	}

	private String getPath(String path) {

		Assert.hasText(path, "Path must not be empty");
		return this.basePath + "/metadata/" + path;
	}

}
