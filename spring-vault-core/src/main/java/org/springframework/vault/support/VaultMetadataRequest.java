package org.springframework.vault.support;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Value object to bind Vault HTTP kv metadata update API requests.
 *
 * @author Zakaria Amine
 * @see <a href="https://www.vaultproject.io/api-docs/secret/kv/kv-v2#update-metadata">Update Metadata</a>
 */
public class VaultMetadataRequest {

  @JsonProperty("max_versions")
  private int maxVersions;

  @JsonProperty("cas_required")
  private boolean casRequired;

  @JsonProperty("delete_version_after")
  private String deleteVersionAfter;

  VaultMetadataRequest(int maxVersions, boolean casRequired, String deleteVersionAfter) {
    this.maxVersions = maxVersions;
    this.casRequired = casRequired;
    this.deleteVersionAfter = deleteVersionAfter;
  }

  public static VaultMetadataRequestBuilder builder() {
    return new VaultMetadataRequestBuilder();
  }

  /**
   * @return The number of versions to keep per key.
   */
  public int getMaxVersions() {
    return maxVersions;
  }

  /**
   * @return If true all keys will require the cas parameter to be set on all write requests.
   */
  public boolean isCasRequired() {
    return casRequired;
  }

  /**
   * @return the deletion_time for all new versions written to this key. Accepts <a href="https://golang.org/pkg/time/#ParseDuration">Go duration format string</a>.
   */
  public String getDeleteVersionAfter() {
    return deleteVersionAfter;
  }

  public static class VaultMetadataRequestBuilder {

    private int maxVersions;
    private boolean casRequired;
    private String deleteVersionAfter;

    /**
     *
     * sets the number of versions to keep per key.
     *
     * @param maxVersions
     * @return {@link VaultMetadataRequest}
     */
    public VaultMetadataRequestBuilder maxVersions(int maxVersions) {
      this.maxVersions = maxVersions;
      return this;
    }

    /**
     *
     * sets the cas_required parameter. If true all keys will require the cas parameter to be set on all write requests.
     *
     * @param casRequired
     * @return {@link VaultMetadataRequest}
     */
    public VaultMetadataRequestBuilder casRequired(boolean casRequired) {
      this.casRequired = casRequired;
      return this;
    }

    /**
     * sets the deletion_time for all new versions written to this key. Accepts <a href="https://golang.org/pkg/time/#ParseDuration">Go duration format string</a>.
     *
     * @param deleteVersionAfter
     * @return {@link VaultMetadataRequest}
     */
    public VaultMetadataRequestBuilder deleteVersionAfter(String deleteVersionAfter) {
      this.deleteVersionAfter = deleteVersionAfter;
      return this;
    }

    /**
     * @return a new {@link VaultMetadataRequest}
     */
    public VaultMetadataRequest build() {
      return new VaultMetadataRequest(maxVersions, casRequired, deleteVersionAfter);
    }
  }
}
