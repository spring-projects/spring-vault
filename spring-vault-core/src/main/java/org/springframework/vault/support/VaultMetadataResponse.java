package org.springframework.vault.support;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Value object to bind Vault HTTP kv read metadata API responses.
 *
 * @author Zakaria Amine
 */
public class VaultMetadataResponse {

  private boolean casRequired;

  private Instant createdTime;

  private int currentVersion;

  private String deleteVersionAfter;

  private int maxVersions;

  private int oldestVersion;

  private Instant updatedTime;

  private List<Versioned.Metadata> versions;

  VaultMetadataResponse(boolean casRequired, Instant createdTime, int currentVersion, String deleteVersionAfter,
      int maxVersions, int oldestVersion, Instant updatedTime, List<Versioned.Metadata> versions) {
    this.casRequired = casRequired;
    this.createdTime = createdTime;
    this.currentVersion = currentVersion;
    this.deleteVersionAfter = deleteVersionAfter;
    this.maxVersions = maxVersions;
    this.oldestVersion = oldestVersion;
    this.updatedTime = updatedTime;
    this.versions = versions;
  }

  public static VaultMetadataResponseBuilder builder() {return new VaultMetadataResponseBuilder();}

  /**
   *
   * @return
   */
  public boolean isCasRequired() {
    return casRequired;
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
   * @return the active secret version
   */
  public int getCurrentVersion() {
    return currentVersion;
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
   * @return max secret versions accepted by this key
   */
  public int getMaxVersions() {
    return maxVersions;
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
   * @return the metadata update time
   */
  public Instant getUpdatedTime() {
    return updatedTime;
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
  public List<Versioned.Metadata> getVersions() {
    return versions;
  }


  public static class VaultMetadataResponseBuilder {

    private boolean casRequired;
    private Instant createdTime;
    private int currentVersion;
    private String deleteVersionAfter;
    private int maxVersions;
    private int oldestVersion;
    private Instant updatedTime;
    private List<Versioned.Metadata> versions;

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

    public VaultMetadataResponseBuilder deleteVersionAfter(String deleteVersionAfter) {
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
      return new VaultMetadataResponse(casRequired, createdTime, currentVersion, deleteVersionAfter, maxVersions,
          oldestVersion, updatedTime, versions);
    }
  }
}
