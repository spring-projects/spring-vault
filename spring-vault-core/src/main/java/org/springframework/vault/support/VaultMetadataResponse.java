package org.springframework.vault.support;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Value object to bind Vault HTTP kv read metadata API responses.
 *
 * @author Zakaria Amine
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VaultMetadataResponse {

  @JsonProperty("cas_required")
  private boolean casRequired;


  @JsonProperty("created_time")
  private Instant createdTime;

  @JsonProperty("current_version")
  private int currentVersion;

  @JsonProperty("delete_version_after")
  private String deleteVersionAfter;

  @JsonProperty("max_versions")
  private int maxVersions;


  @JsonProperty("oldest_version")
  private int oldestVersion;

  @JsonProperty("updated_time")
  private Instant updatedTime;

  private JsonNode versions;

  /**
   *
   * @return
   */
  public boolean isCasRequired() {
    return casRequired;
  }

  /**
   *
   * @param casRequired
   */
  public void setCasRequired(boolean casRequired) {
    this.casRequired = casRequired;
  }

  /**
   *
   * @return the metadata creation time
   */
  public Instant getCreatedTime() {
    return createdTime;
  }

  /**
   *
   * @param createdTime
   */
  public void setCreatedTime(Instant createdTime) {
    this.createdTime = createdTime;
  }

  /**
   *
   * @return the active secret version
   */
  public int getCurrentVersion() {
    return currentVersion;
  }

  /**
   *
   * @param currentVersion
   */
  public void setCurrentVersion(int currentVersion) {
    this.currentVersion = currentVersion;
  }

  /**
   *
   * @return the duration after which a secret is to be deleted. 0 for unlimited duration. follows <a href="https://golang.org/pkg/time/#ParseDuration">Go duration format string</a>.
   */
  public String getDeleteVersionAfter() {
    return deleteVersionAfter;
  }

  /**
   *
   * @param deleteVersionAfter
   */
  public void setDeleteVersionAfter(String deleteVersionAfter) {
    this.deleteVersionAfter = deleteVersionAfter;
  }

  /**
   *
   * @return max secret versions accepted by this key
   */
  public int getMaxVersions() {
    return maxVersions;
  }

  /**
   *
   * @param maxVersions
   */
  public void setMaxVersions(int maxVersions) {
    this.maxVersions = maxVersions;
  }

  /**
   *
   * @return oldest key version
   */
  public int getOldestVersion() {
    return oldestVersion;
  }

  /**
   *
   * @param oldestVersion
   */
  public void setOldestVersion(int oldestVersion) {
    this.oldestVersion = oldestVersion;
  }

  /**
   *
   * @return the metadata update time
   */
  public Instant getUpdatedTime() {
    return updatedTime;
  }

  /**
   *
   * @param updatedTime
   */
  public void setUpdatedTime(Instant updatedTime) {
    this.updatedTime = updatedTime;
  }

  /**
   *
   * Follows the following format.
   *
   * "versions": {
   *       "1": {
   *         "created_time": "2020-05-18T12:23:09.895587932Z",
   *         "deletion_time": "2020-05-18T12:31:00.66257744Z",
   *         "destroyed": false
   *       },
   *       "2": {
   *         "created_time": "2020-05-18T12:23:10.122081788Z",
   *         "deletion_time": "",
   *         "destroyed": false
   *       }
   *   }
   *
   * @return the key versions and their details
   */
  public JsonNode getVersions() {
    return versions;
  }

  /**
   *
   * @param versions
   */
  public void setVersions(JsonNode versions) {
    this.versions = versions;
  }
}
