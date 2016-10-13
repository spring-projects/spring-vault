/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.vault.support;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Value object to bind Vault HTTP Initialization API requests.
 * 
 * @author Mark Paluch
 */
public class VaultInitializationRequest {

	@JsonProperty("secret_shares")
	private final int secretShares;

	@JsonProperty("secret_threshold")
	private final int secretThreshold;

	private VaultInitializationRequest(int secretShares, int secretThreshold) {
		this.secretShares = secretShares;
		this.secretThreshold = secretThreshold;
	}

	/**
	 * Create a new {@literal VaultInitializationRequest} given {@code secretShares} and
	 * {@code secretThreshold}.
	 * 
	 * @param secretShares
	 * @param secretThreshold
	 * @return a new {@link VaultInitializationRequest}.
	 */
	public static VaultInitializationRequest create(int secretShares, int secretThreshold) {
		return new VaultInitializationRequest(secretShares, secretThreshold);
	}

	/**
	 * @return number of secret shares.
	 */
	public int getSecretShares() {
		return secretShares;
	}

	/**
	 * @return required number of secret shares to unseal Vault.
	 */
	public int getSecretThreshold() {
		return secretThreshold;
	}

}
