/*
 * Copyright 2020-2024 the original author or authors.
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
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.lang.Nullable;

/**
 * Value object to bind Vault HTTP kv metadata update API requests.
 *
 * @author Zakaria Amine
 * @author Jeroen Willemsen
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

	@JsonProperty("custom_metadata")
	private final @Nullable Map<String, String> customMetadata;

	private VaultMetadataRequest(boolean casRequired, @Nullable Map<String, String> customMetadata,
			@Nullable Duration deleteVersionAfter, int maxVersions) {
		this.casRequired = casRequired;
		this.customMetadata = customMetadata;
		this.deleteVersionAfter = DurationParser
			.formatDuration(deleteVersionAfter != null ? deleteVersionAfter : Duration.ZERO);
		this.maxVersions = maxVersions;
	}

	public static VaultMetadataRequestBuilder builder() {
		return new VaultMetadataRequestBuilder();
	}

	/**
	 * @return If true all keys will require the cas parameter to be set on all write
	 * requests.
	 */
	public boolean isCasRequired() {
		return this.casRequired;
	}

	@Nullable
	public Map<String, String> getCustomMetadata() {
		return this.customMetadata;
	}

	/**
	 * @return the deletion_time for all new versions written to this key. Accepts
	 * <a href="https://golang.org/pkg/time/#ParseDuration">Go duration format string</a>.
	 */
	public String getDeleteVersionAfter() {
		return this.deleteVersionAfter;
	}

	/**
	 * @return The number of versions to keep per key.
	 */
	public int getMaxVersions() {
		return this.maxVersions;
	}

	public static class VaultMetadataRequestBuilder {

		private boolean casRequired;

		@Nullable
		private Map<String, String> customMetadata;

		@Nullable
		private Duration deleteVersionAfter;

		private int maxVersions;

		/**
		 * Set the cas_required parameter to {@code true} to require the cas parameter to
		 * be set on all write requests.
		 * @return {@link VaultMetadataRequest}
		 * @since 3.1
		 */
		public VaultMetadataRequestBuilder casRequired() {
			return casRequired(true);
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
		 * Sets the custom Metadata for the metadata request.
		 * @param customMetadata
		 * @return {@link VaultMetadataRequest}
		 * @since 3.1
		 */
		public VaultMetadataRequestBuilder customMetadata(Map<String, String> customMetadata) {
			this.customMetadata = customMetadata;
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
		 * Set the number of versions to keep per key.
		 * @param maxVersions
		 * @return {@link VaultMetadataRequest}
		 */
		public VaultMetadataRequestBuilder maxVersions(int maxVersions) {
			this.maxVersions = maxVersions;
			return this;
		}

		/**
		 * @return a new {@link VaultMetadataRequest}
		 */
		public VaultMetadataRequest build() {
			return new VaultMetadataRequest(this.casRequired, this.customMetadata, this.deleteVersionAfter,
					this.maxVersions);
		}

	}

}
