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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Value object to bind Vault HTTP Transit Key API responses.
 *
 * @author Mark Paluch
 */
public class VaultTransitKey {

	@JsonProperty("cipher_mode") private String cipherMode;

	@JsonProperty("deletion_allowed") private boolean deletionAllowed;

	private boolean derived;

	private Map<String, Long> keys;

	@JsonProperty("latest_version") private boolean latestVersion;

	@JsonProperty("min_decryption_version") private int minDecryptionVersion;

	private String name;

	public String getCipherMode() {
		return cipherMode;
	}

	public void setCipherMode(String cipherMode) {
		this.cipherMode = cipherMode;
	}

	public boolean isDeletionAllowed() {
		return deletionAllowed;
	}

	public void setDeletionAllowed(boolean deletionAllowed) {
		this.deletionAllowed = deletionAllowed;
	}

	public boolean isDerived() {
		return derived;
	}

	public void setDerived(boolean derived) {
		this.derived = derived;
	}

	public Map<String, Long> getKeys() {
		return keys;
	}

	public void setKeys(Map<String, Long> keys) {
		this.keys = keys;
	}

	public boolean isLatestVersion() {
		return latestVersion;
	}

	public void setLatestVersion(boolean latestVersion) {
		this.latestVersion = latestVersion;
	}

	public int getMinDecryptionVersion() {
		return minDecryptionVersion;
	}

	public void setMinDecryptionVersion(int minDecryptionVersion) {
		this.minDecryptionVersion = minDecryptionVersion;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
