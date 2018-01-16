/*
 * Copyright 2016-2018 the original author or authors.
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
 * Value object to bind Vault HTTP Transit Key Config API requests.
 *
 * @author Mark Paluch
 */
public class VaultTransitKeyConfiguration {

	@JsonProperty("deletion_allowed")
	private final Boolean deletionAllowed;

	@JsonProperty("latest_version")
	private final Integer latestVersion;

	@JsonProperty("min_decryption_version")
	private final Integer minDecryptionVersion;

	@JsonProperty("min_encryption_version")
	private final Integer minEncryptionVersion;

	private VaultTransitKeyConfiguration(Boolean deletionAllowed, Integer latestVersion,
			Integer minDecryptionVersion, Integer minEncryptionVersion) {

		this.deletionAllowed = deletionAllowed;
		this.latestVersion = latestVersion;
		this.minDecryptionVersion = minDecryptionVersion;
		this.minEncryptionVersion = minEncryptionVersion;
	}

	/**
	 * @return a new {@link VaultTransitKeyConfigurationBuilder}.
	 */
	public static VaultTransitKeyConfigurationBuilder builder() {
		return new VaultTransitKeyConfigurationBuilder();
	}

	/**
	 * @return whether key deletion is configured
	 */
	public Boolean getDeletionAllowed() {
		return deletionAllowed;
	}

	/**
	 * @return latest key version
	 * @deprecated since 1.1, property does not exist.
	 */
	@Deprecated
	public Integer getLatestVersion() {
		return latestVersion;
	}

	/**
	 * @return the minimum version of ciphertext allowed to be decrypted.
	 * @since 1.1
	 */
	public Integer getMinDecryptionVersion() {
		return minDecryptionVersion;
	}

	/**
	 * @return the minimum version of the key that can be used to encrypt plaintext, sign
	 * payloads, or generate HMACs.
	 * @since 1.1
	 */
	public Integer getMinEncryptionVersion() {
		return minEncryptionVersion;
	}

	/**
	 * Builder for {@link VaultTransitKeyConfiguration}.
	 */
	public static class VaultTransitKeyConfigurationBuilder {

		private Boolean deletionAllowed;

		private Integer latestVersion;

		private Integer minDecryptionVersion;

		private Integer minEncryptionVersion;

		VaultTransitKeyConfigurationBuilder() {
		}

		/**
		 * Set whether key deletion is allowed.
		 *
		 * @param deletionAllowed {@literal true} if key deletion should be allowed.
		 * @return {@code this} {@link VaultTransitKeyConfigurationBuilder}.
		 */
		public VaultTransitKeyConfigurationBuilder deletionAllowed(boolean deletionAllowed) {
			this.deletionAllowed = deletionAllowed;
			return this;
		}

		/**
		 * Set the latest key version.
		 *
		 * @param latestVersion key version.
		 * @return {@code this} {@link VaultTransitKeyConfigurationBuilder}.
		 * @deprecated since 1.1, property does not exist.
		 */
		@Deprecated
		public VaultTransitKeyConfigurationBuilder latestVersion(int latestVersion) {
			this.latestVersion = latestVersion;
			return this;
		}

		/**
		 * Specifies the minimum version of ciphertext allowed to be decrypted. Adjusting
		 * this as part of a key rotation policy can prevent old copies of ciphertext from
		 * being decrypted, should they fall into the wrong hands. For signatures, this
		 * value controls the minimum version of signature that can be verified against.
		 * For HMACs, this controls the minimum version of a key allowed to be used as the
		 * key for verification.
		 *
		 * @param minDecryptionVersion key version.
		 * @return {@code this} {@link VaultTransitKeyConfigurationBuilder}.
		 * @since 1.1
		 */
		public VaultTransitKeyConfigurationBuilder minDecryptionVersion(
				int minDecryptionVersion) {
			this.minDecryptionVersion = minDecryptionVersion;
			return this;
		}

		/**
		 * Specifies the minimum version of the key that can be used to encrypt plaintext,
		 * sign payloads, or generate HMACs. Must be 0 (which will use the latest version)
		 * or a value greater or equal to {@link #minDecryptionVersion(int)}.
		 *
		 * @param minEncryptionVersion key version.
		 * @return {@code this} {@link VaultTransitKeyConfigurationBuilder}.
		 * @since 1.1
		 */
		public VaultTransitKeyConfigurationBuilder minEncryptionVersion(
				int minEncryptionVersion) {
			this.minEncryptionVersion = minEncryptionVersion;
			return this;
		}

		/**
		 * Build a new {@link VaultTransitKeyConfiguration} instance.
		 *
		 * @return a new {@link VaultTransitKeyConfiguration}.
		 */
		public VaultTransitKeyConfiguration build() {
			return new VaultTransitKeyConfiguration(deletionAllowed, latestVersion,
					minDecryptionVersion, minEncryptionVersion);
		}
	}
}
