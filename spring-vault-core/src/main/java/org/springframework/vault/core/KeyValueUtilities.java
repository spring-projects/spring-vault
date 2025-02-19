package org.springframework.vault.core;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.support.DurationParser;
import org.springframework.vault.support.VaultMetadataResponse;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.support.Versioned.Metadata;
import org.springframework.vault.support.Versioned.Metadata.MetadataBuilder;
import org.springframework.vault.support.Versioned.Version;

/**
 * Common utility methods to map raw vault data structures to Spring Vault objects
 *
 * @author Timothy R. Weiand
 * @author Mark Paluch
 * @since 3.1
 */
class KeyValueUtilities {

	@SuppressWarnings("unchecked")
	static Metadata getMetadata(Map<String, Object> responseMetadata) {

		MetadataBuilder builder = Metadata.builder();
		TemporalAccessor created_time = getRequiredDate(responseMetadata, "created_time");
		TemporalAccessor deletion_time = getDate(responseMetadata, "deletion_time");

		builder.createdAt(Instant.from(created_time));

		if (deletion_time != null) {
			builder.deletedAt(Instant.from(deletion_time));
		}

		if (Boolean.TRUE.equals(responseMetadata.get("destroyed"))) {
			builder.destroyed();
		}

		builder.customMetadata((Map) responseMetadata.get("custom_metadata"));

		Integer version = (Integer) responseMetadata.get("version");
		builder.version(version != null ? Version.from(version) : Version.unversioned());

		return builder.build();
	}

	private static @Nullable TemporalAccessor getDate(Map<String, Object> responseMetadata, String key) {

		String date = (String) responseMetadata.getOrDefault(key, "");
		if (StringUtils.hasText(date)) {
			return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(date);
		}
		return null;
	}

	private static TemporalAccessor getRequiredDate(Map<String, Object> responseMetadata, String key) {

		TemporalAccessor date = getDate(responseMetadata, key);

		if (date == null) {
			throw new IllegalArgumentException("Date for key '" + key + "' is null");
		}

		return date;
	}

	@SuppressWarnings({ "ConstantConditions", "unchecked", "rawtypes" })
	static VaultMetadataResponse fromMap(Map<String, Object> metadataResponse) {

		Duration duration = DurationParser.parseDuration((String) metadataResponse.get("delete_version_after"));

		return VaultMetadataResponse.builder()
			.casRequired(Boolean.parseBoolean(String.valueOf(metadataResponse.get("cas_required"))))
			.createdTime(toRequiredInstant((String) metadataResponse.get("created_time")))
			.currentVersion(Integer.parseInt(String.valueOf(metadataResponse.get("current_version"))))
			.deleteVersionAfter(duration)
			.maxVersions(Integer.parseInt(String.valueOf(metadataResponse.get("max_versions"))))
			.oldestVersion(Integer.parseInt(String.valueOf(metadataResponse.get("oldest_version"))))
			.updatedTime(toRequiredInstant((String) metadataResponse.get("updated_time")))
			.versions(buildVersions((Map) metadataResponse.get("versions")))
			.build();
	}

	@Nullable
	static Instant toInstant(@Nullable String date) {
		return StringUtils.hasText(date) ? Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(date)) : null;
	}

	static Instant toRequiredInstant(@Nullable String date) {

		Instant instant = toInstant(date);

		if (instant == null) {
			throw new IllegalArgumentException("Date is null");
		}

		return instant;
	}

	private static List<Metadata> buildVersions(@Nullable Map<String, Map<String, Object>> versions) {

		if (versions == null) {
			return Collections.emptyList();
		}

		return versions.entrySet()
			.stream()
			.map(entry -> buildVersion(entry.getKey(), entry.getValue()))
			.collect(Collectors.toList());
	}

	private static Versioned.Metadata buildVersion(String version, Map<String, Object> versionData) {

		Instant createdTime = toRequiredInstant((String) versionData.get("created_time"));
		Instant deletionTime = toInstant((String) versionData.get("deletion_time"));
		Boolean destroyed = (Boolean) versionData.get("destroyed");
		Versioned.Version kvVersion = Versioned.Version.from(Integer.parseInt(version));

		MetadataBuilder builder = Metadata.builder().createdAt(createdTime).deletedAt(deletionTime).version(kvVersion);

		if (destroyed != null) {
			builder.destroyed(destroyed);
		}

		return builder.build();
	}

	static Map<String, Object> createPatchRequest(Map<String, ?> patch, Map<String, Object> previous,
			Map<String, Object> metadata) {

		Map<String, Object> result = new LinkedHashMap<>(previous);
		result.putAll(patch);

		Map<String, Object> body = new HashMap<>();
		body.put("data", result);
		body.put("options", Collections.singletonMap("cas", metadata.get("version")));

		return body;
	}

	static String normalizeListPath(String path) {

		Assert.notNull(path, "Path must not be null");

		return path.equals("/") ? "" : path.endsWith("/") ? path : path + "/";
	}

}
