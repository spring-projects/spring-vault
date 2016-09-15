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
 * Transit backend key creation request options.
 * 
 * @author Mark Paluch
 */
public class VaultTransitKeyCreationRequest {

	private Boolean derived;

	@JsonProperty("convergent_encryption") private Boolean convergentEncryption;

	/**
	 * Creates a new {@link VaultTransitKeyCreationRequest}.
	 */
	public VaultTransitKeyCreationRequest() {}

	/**
	 * Creates a new {@link VaultTransitKeyCreationRequest} and sets {@code derived} and {@code convergentEncryption}
	 * properties.
	 * 
	 * @param derived
	 * @param convergentEncryption
	 */
	public VaultTransitKeyCreationRequest(boolean derived, boolean convergentEncryption) {
		this.derived = derived;
		this.convergentEncryption = convergentEncryption;
	}

	public Boolean getDerived() {
		return derived;
	}

	public void setDerived(Boolean derived) {
		this.derived = derived;
	}

	public Boolean getConvergentEncryption() {
		return convergentEncryption;
	}

	public void setConvergentEncryption(Boolean convergentEncryption) {
		this.convergentEncryption = convergentEncryption;
	}
}
