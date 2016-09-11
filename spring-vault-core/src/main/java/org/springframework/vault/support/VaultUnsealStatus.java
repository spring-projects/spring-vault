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
 * Value object to bind Vault HTTP Unseal API responses.
 * 
 * @author Mark Paluch
 */
public class VaultUnsealStatus {

	private boolean sealed;

	@JsonProperty("t") private int secretThreshold;

	@JsonProperty("n") private int secretShares;

	private int progress;

	public VaultUnsealStatus() {}

	public int getSecretShares() {
		return secretShares;
	}

	public void setSecretShares(int secretShares) {
		this.secretShares = secretShares;
	}

	public int getSecretThreshold() {
		return secretThreshold;
	}

	public void setSecretThreshold(int secretThreshold) {
		this.secretThreshold = secretThreshold;
	}

	public boolean isSealed() {
		return sealed;
	}

	public void setSealed(boolean sealed) {
		this.sealed = sealed;
	}

	public int getProgress() {
		return progress;
	}

	public void setProgress(int progress) {
		this.progress = progress;
	}
}
