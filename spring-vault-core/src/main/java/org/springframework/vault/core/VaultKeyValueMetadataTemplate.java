package org.springframework.vault.core;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.util.Assert;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultMetadataRequest;
import org.springframework.vault.support.VaultMetadataResponse;
import org.springframework.vault.support.Versioned;
import org.springframework.web.client.HttpStatusCodeException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class VaultKeyValueMetadataTemplate implements VaultKeyValueMetadataOperations {

  private final VaultOperations vaultOperations;

  private final String basePath;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public VaultKeyValueMetadataTemplate(VaultOperations vaultOperations, String basePath) {
    Assert.notNull(vaultOperations, "VaultOperations must not be null");
    this.vaultOperations = vaultOperations;
    this.basePath = basePath;
  }

  @Override
  public void delete(String path) {
    Assert.hasText(path, "Path must not be empty");
    vaultOperations.delete("/"+this.basePath+"/metadata/" + path);
  }

  @Override
  public VaultMetadataResponse get(String path) {
    Assert.hasText(path, "Path must not be empty");
    Map<String, Object> metadataResponse =
        vaultOperations.read("/" + this.basePath + "/metadata/" + path, Map.class).getData();

    return fromMap(metadataResponse);
  }

  @Override
  public void put(String path, VaultMetadataRequest body) {
    Assert.hasText(path, "Path must not be empty");
    Assert.notNull(body, "Body must not be null");
    vaultOperations.doWithSession(restOperations -> {
      try {
        restOperations.put("/"+this.basePath+"/metadata/" + path, body);
        return null;
      }
      catch (HttpStatusCodeException e) {
        throw VaultResponses.buildException(e, path);
      }
    });
  }

  private VaultMetadataResponse fromMap(Map<String, Object> metadataResponse) {
    return VaultMetadataResponse.builder()
        .casRequired(Boolean.parseBoolean(String.valueOf(metadataResponse.get("cas_required"))))
        .createdTime(toInstant(metadataResponse.get("created_time")))
        .currentVersion(Integer.parseInt(String.valueOf(metadataResponse.get("current_version"))))
        .deleteVersionAfter(String.valueOf(metadataResponse.get("delete_version_after")))
        .maxVersions(Integer.parseInt(String.valueOf(metadataResponse.get("max_versions"))))
        .oldestVersion(Integer.parseInt(String.valueOf(metadataResponse.get("oldest_version"))))
        .updatedTime(toInstant(metadataResponse.get("updated_time")))
        .versions(buildVersions(metadataResponse.get("versions")))
        .build();
  }

  private static List<Versioned.Metadata> buildVersions(Object versions) {
    try {
      JsonNode kvVersions = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(versions));

      return StreamSupport.stream(Spliterators.spliteratorUnknownSize(kvVersions.fieldNames(), Spliterator.DISTINCT), false)
          .map(version -> fromJsonNode(kvVersions.get(version), version))
          .collect(Collectors.toList());
    }
    catch (Exception e) {
      e.printStackTrace();
      return new ArrayList<>();
    }
  }

  private static Versioned.Metadata fromJsonNode(JsonNode versionData, String version) {
    Instant createdTime = toInstant(versionData.get("created_time").asText());
    Instant deletionTime = Objects.equals(versionData.get("deletion_time").asText(), "") ? null : toInstant(versionData.get("deletion_time").asText());
    boolean destroyed = versionData.get("destroyed").asBoolean();
    Versioned.Version kvVersion = Versioned.Version.from(Integer.parseInt(version));

    return Versioned.Metadata.builder().createdAt(createdTime).deletedAt(deletionTime).destroyed(destroyed).version(kvVersion).build();
  }

  private static Instant toInstant(Object date) {
    return Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(String.valueOf(date)));
  }
}
