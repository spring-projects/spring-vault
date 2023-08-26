package org.springframework.vault.core;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.vault.support.DurationParser;
import org.springframework.vault.support.VaultMetadataResponse;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.support.Versioned.Metadata;
import org.springframework.vault.support.Versioned.Metadata.MetadataBuilder;
import org.springframework.vault.support.Versioned.Version;

class VaultKeyValueUtilities {

	static Metadata getMetadata(Map<String, Object> responseMetadata) {

		MetadataBuilder builder = Metadata.builder();
		TemporalAccessor created_time = getDate(responseMetadata, "created_time");
		TemporalAccessor deletion_time = getDate(responseMetadata, "deletion_time");

		builder.createdAt(Instant.from(created_time));

		if (deletion_time != null) {
			builder.deletedAt(Instant.from(deletion_time));
		}

		if (Boolean.TRUE.equals(responseMetadata.get("destroyed"))) {
			builder.destroyed();
		}

		Integer version = (Integer) responseMetadata.get("version");
		builder.version(Version.from(version));

		return builder.build();
	}

	@Nullable
	private static TemporalAccessor getDate(Map<String, Object> responseMetadata, String key) {

		String date = (String) responseMetadata.getOrDefault(key, "");
		if (StringUtils.hasText(date)) {
			return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(date);
		}
		return null;
	}

	@SuppressWarnings({ "ConstantConditions", "unchecked", "rawtypes" })
	static VaultMetadataResponse fromMap(Map<String, Object> metadataResponse) {

		Duration duration = DurationParser.parseDuration((String) metadataResponse.get("delete_version_after"));

		return VaultMetadataResponse.builder()
			.casRequired(Boolean.parseBoolean(String.valueOf(metadataResponse.get("cas_required"))))
			.createdTime(toInstant((String) metadataResponse.get("created_time")))
			.currentVersion(Integer.parseInt(String.valueOf(metadataResponse.get("current_version"))))
			.deleteVersionAfter(duration)
			.maxVersions(Integer.parseInt(String.valueOf(metadataResponse.get("max_versions"))))
			.oldestVersion(Integer.parseInt(String.valueOf(metadataResponse.get("oldest_version"))))
			.updatedTime(toInstant((String) metadataResponse.get("updated_time")))
			.versions(buildVersions((Map) metadataResponse.get("versions")))
			.build();
	}

	@Nullable
	static Instant toInstant(String date) {
		return StringUtils.hasText(date) ? Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(date)) : null;
	}

	private static List<Metadata> buildVersions(Map<String, Map<String, Object>> versions) {

		return versions.entrySet()
			.stream()
			.map(entry -> buildVersion(entry.getKey(), entry.getValue()))
			.collect(Collectors.toList());
	}

	private static Versioned.Metadata buildVersion(String version, Map<String, Object> versionData) {

		Instant createdTime = toInstant((String) versionData.get("created_time"));
		Instant deletionTime = toInstant((String) versionData.get("deletion_time"));
		boolean destroyed = (Boolean) versionData.get("destroyed");
		Versioned.Version kvVersion = Versioned.Version.from(Integer.parseInt(version));

		return Versioned.Metadata.builder()
			.createdAt(createdTime)
			.deletedAt(deletionTime)
			.destroyed(destroyed)
			.version(kvVersion)
			.build();
	}

}
