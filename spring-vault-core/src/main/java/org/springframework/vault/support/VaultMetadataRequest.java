/*
 * Copyright 2020 the original author or authors.
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

import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.lang.Nullable;

/**
 * Value object to bind Vault HTTP kv metadata update API requests.
 *
 * @author Zakaria Amine
 * @see <a href=
 * "https://www.vaultproject.io/api-docs/secret/kv/kv-v2#update-metadata">Update
 * Metadata</a>
 * @since 2.3
 */
public class VaultMetadataRequest {

	@JsonProperty("max_versions")
	private final int maxVersions;

	@JsonProperty("cas_required")
	private final boolean casRequired;

	@JsonProperty("delete_version_after")
	private final String deleteVersionAfter;

	private VaultMetadataRequest(int maxVersions, boolean casRequired, @Nullable Duration deleteVersionAfter) {
		this.maxVersions = maxVersions;
		this.casRequired = casRequired;
		this.deleteVersionAfter = DurationParser
				.formatDuration(deleteVersionAfter != null ? deleteVersionAfter : Duration.ZERO);
	}

	public static VaultMetadataRequestBuilder builder() {
		return new VaultMetadataRequestBuilder();
	}

	/**
	 * @return The number of versions to keep per key.
	 */
	public int getMaxVersions() {
		return this.maxVersions;
	}

	/**
	 * @return If true all keys will require the cas parameter to be set on all write
	 * requests.
	 */
	public boolean isCasRequired() {
		return this.casRequired;
	}

	/**
	 * @return the deletion_time for all new versions written to this key. Accepts
	 * <a href="https://golang.org/pkg/time/#ParseDuration">Go duration format string</a>.
	 */
	public String getDeleteVersionAfter() {
		return this.deleteVersionAfter;
	}

	public static class VaultMetadataRequestBuilder {

		private int maxVersions;

		private boolean casRequired;

		@Nullable
		private Duration deleteVersionAfter;

		/**
		 * Set the number of versions to keep per key.
		 * @param maxVersions
		 * @return {@link VaultMetadataRequest}
		 */
		public VaultMetadataRequestBuilder maxVersions(int maxVersions) {
			this.maxVersions = maxVersions;
			return this;
		}

		/**
		 * Set the cas_required parameter. If true all keys will require the cas parameter
		 * to be set on all write requests.
		 * @param casRequired
		 * @return {@link VaultMetadataRequest}
		 */
		public VaultMetadataRequestBuilder casRequired(boolean casRequired) {
			this.casRequired = casRequired;
			return this;
		}

		/**
		 * Sets the deletion time for all new versions written to this key.
		 * @param deleteVersionAfter
		 * @return {@link VaultMetadataRequest}
		 */
		public VaultMetadataRequestBuilder deleteVersionAfter(Duration deleteVersionAfter) {
			this.deleteVersionAfter = deleteVersionAfter;
			return this;
		}

		/**
		 * @return a new {@link VaultMetadataRequest}
		 */
		public VaultMetadataRequest build() {
			return new VaultMetadataRequest(this.maxVersions, this.casRequired, this.deleteVersionAfter);
		}

	}

}
