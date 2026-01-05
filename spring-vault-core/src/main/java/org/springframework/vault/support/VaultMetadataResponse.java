/*
 * Copyright 2020-present the original author or authors.
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
import java.time.Instant;
import java.time.Period;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.vault.support.Versioned.Metadata;

/**
 * Value object to bind Vault HTTP kv read metadata API responses.
 *
 * @author Zakaria Amine
 * @author Jeroen Willemsen
 * @since 2.3
 */
public class VaultMetadataResponse {

	private final boolean casRequired;

	private final Instant createdTime;

	private final int currentVersion;

	private final Map<String, String> customMetadata;

	private final Duration deleteVersionAfter;

	private final int maxVersions;

	private final int oldestVersion;

	private final Instant updatedTime;

	private final List<Versioned.Metadata> versions;


	private VaultMetadataResponse(boolean casRequired, Instant createdTime, int currentVersion,
			Map<String, String> customMetadata, Duration deleteVersionAfter, int maxVersions, int oldestVersion,
			Instant updatedTime, List<Metadata> versions) {
		this.casRequired = casRequired;
		this.createdTime = createdTime;
		this.customMetadata = customMetadata;
		this.currentVersion = currentVersion;
		this.deleteVersionAfter = deleteVersionAfter;
		this.maxVersions = maxVersions;
		this.oldestVersion = oldestVersion;
		this.updatedTime = updatedTime;
		this.versions = versions;
	}


	public static VaultMetadataResponseBuilder builder() {
		return new VaultMetadataResponseBuilder();
	}


	/**
	 * @return whether compare-and-swap is required (i.e. optimistic locking).
	 */
	public boolean isCasRequired() {
		return this.casRequired;
	}

	/**
	 * @return the metadata creation time
	 */
	public Instant getCreatedTime() {
		return this.createdTime;
	}

	/**
	 * @return the active secret version
	 */
	public int getCurrentVersion() {
		return this.currentVersion;
	}

	/**
	 * @return the duration after which a secret is to be deleted.
	 * {@link Period#ZERO} for unlimited duration. Versions prior to Vault 1.2 may
	 * return {@code null}.
	 */
	@Nullable
	public Duration getDeleteVersionAfter() {
		return this.deleteVersionAfter;
	}

	/**
	 * @return the custom metadata. Entries can be any arbitrary key-value pairs
	 * @since 3.1
	 */
	public Map<String, String> getCustomMetadata() {
		return this.customMetadata;
	}

	/**
	 * @return max secret versions accepted by this key
	 */
	public int getMaxVersions() {
		return this.maxVersions;
	}

	/**
	 * @return oldest key version
	 */
	public int getOldestVersion() {
		return this.oldestVersion;
	}

	/**
	 * @return the metadata update time
	 */
	public Instant getUpdatedTime() {
		return this.updatedTime;
	}

	/**
	 * Follows the following format. "versions": { "1": { "created_time":
	 * "2020-05-18T12:23:09.895587932Z", "deletion_time":
	 * "2020-05-18T12:31:00.66257744Z", "destroyed": false }, "2": { "created_time":
	 * "2020-05-18T12:23:10.122081788Z", "deletion_time": "", "destroyed": false } }
	 * @return the key versions and their details
	 */
	public List<Versioned.Metadata> getVersions() {
		return this.versions;
	}


	/**
	 * Builder for {@link VaultMetadataResponse}.
	 */
	public static class VaultMetadataResponseBuilder {

		private boolean casRequired;

		private Map<String, String> customMetadata;

		private Instant createdTime;

		private int currentVersion;

		private Duration deleteVersionAfter;

		private int maxVersions;

		private int oldestVersion;

		private Instant updatedTime;

		private List<Versioned.Metadata> versions;


		VaultMetadataResponseBuilder() {
		}


		public VaultMetadataResponseBuilder casRequired(boolean casRequired) {
			this.casRequired = casRequired;
			return this;
		}

		public VaultMetadataResponseBuilder createdTime(Instant createdTime) {
			this.createdTime = createdTime;
			return this;
		}

		public VaultMetadataResponseBuilder currentVersion(int currentVersion) {
			this.currentVersion = currentVersion;
			return this;
		}

		public VaultMetadataResponseBuilder customMetadata(@Nullable Map<String, String> customMetadata) {
			this.customMetadata = customMetadata != null ? customMetadata : Collections.emptyMap();
			return this;
		}

		public VaultMetadataResponseBuilder deleteVersionAfter(Duration deleteVersionAfter) {
			this.deleteVersionAfter = deleteVersionAfter;
			return this;
		}

		public VaultMetadataResponseBuilder maxVersions(int maxVersions) {
			this.maxVersions = maxVersions;
			return this;
		}

		public VaultMetadataResponseBuilder oldestVersion(int oldestVersion) {
			this.oldestVersion = oldestVersion;
			return this;
		}

		public VaultMetadataResponseBuilder updatedTime(Instant updatedTime) {
			this.updatedTime = updatedTime;
			return this;
		}

		public VaultMetadataResponseBuilder versions(List<Versioned.Metadata> versions) {
			this.versions = versions;
			return this;
		}

		public VaultMetadataResponse build() {
			return new VaultMetadataResponse(this.casRequired, this.createdTime, this.currentVersion,
					this.customMetadata, this.deleteVersionAfter, this.maxVersions, this.oldestVersion,
					this.updatedTime, this.versions);
		}

	}

}
