/*
 * Copyright 2020-2022 the original author or authors.
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

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.support.DurationParser;
import org.springframework.vault.support.VaultMetadataRequest;
import org.springframework.vault.support.VaultMetadataResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.Versioned;

/**
 * Default implementation of {@link VaultKeyValueMetadataOperations}.
 *
 * @author Zakaria Amine
 * @author Mark Paluch
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
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public VaultMetadataResponse get(String path) {

		VaultResponseSupport<Map> response = this.vaultOperations.read(getPath(path), Map.class);

		return response != null ? fromMap(response.getRequiredData()) : null;
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

	@SuppressWarnings({ "ConstantConditions", "unchecked", "rawtypes" })
	private static VaultMetadataResponse fromMap(Map<String, Object> metadataResponse) {

		Duration duration = DurationParser.parseDuration((String) metadataResponse.get("delete_version_after"));

		return VaultMetadataResponse.builder()
				.casRequired(Boolean.parseBoolean(String.valueOf(metadataResponse.get("cas_required"))))
				.createdTime(toInstant((String) metadataResponse.get("created_time")))
				.currentVersion(Integer.parseInt(String.valueOf(metadataResponse.get("current_version"))))
				.deleteVersionAfter(duration)
				.maxVersions(Integer.parseInt(String.valueOf(metadataResponse.get("max_versions"))))
				.oldestVersion(Integer.parseInt(String.valueOf(metadataResponse.get("oldest_version"))))
				.updatedTime(toInstant((String) metadataResponse.get("updated_time")))
				.versions(buildVersions((Map) metadataResponse.get("versions"))).build();
	}

	private static List<Versioned.Metadata> buildVersions(Map<String, Map<String, Object>> versions) {

		return versions.entrySet().stream().map(entry -> buildVersion(entry.getKey(), entry.getValue()))
				.collect(Collectors.toList());
	}

	private static Versioned.Metadata buildVersion(String version, Map<String, Object> versionData) {

		Instant createdTime = toInstant((String) versionData.get("created_time"));
		Instant deletionTime = toInstant((String) versionData.get("deletion_time"));
		boolean destroyed = (Boolean) versionData.get("destroyed");
		Versioned.Version kvVersion = Versioned.Version.from(Integer.parseInt(version));

		return Versioned.Metadata.builder().createdAt(createdTime).deletedAt(deletionTime).destroyed(destroyed)
				.version(kvVersion).build();
	}

	@Nullable
	private static Instant toInstant(String date) {
		return StringUtils.hasText(date) ? Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(date)) : null;
	}

}
