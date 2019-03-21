/*
 * Copyright 2016 the original author or authors.
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

	private VaultTransitKeyConfiguration(Boolean deletionAllowed, Integer latestVersion) {
		this.deletionAllowed = deletionAllowed;
		this.latestVersion = latestVersion;
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
	 */
	public Integer getLatestVersion() {
		return latestVersion;
	}

	/**
	 * Builder for {@link VaultTransitKeyConfiguration}.
	 */
	public static class VaultTransitKeyConfigurationBuilder {

		private Boolean deletionAllowed;

		private Integer latestVersion;

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
		 */
		public VaultTransitKeyConfigurationBuilder latestVersion(int latestVersion) {
			this.latestVersion = latestVersion;
			return this;
		}

		/**
		 * Build a new {@link VaultTransitKeyConfiguration} instance.
		 *
		 * @return a new {@link VaultTransitKeyConfiguration}.
		 */
		public VaultTransitKeyConfiguration build() {
			return new VaultTransitKeyConfiguration(deletionAllowed, latestVersion);
		}
	}
}
