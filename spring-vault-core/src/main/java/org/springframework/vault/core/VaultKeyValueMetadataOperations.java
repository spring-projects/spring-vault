package org.springframework.vault.core;

import org.springframework.vault.support.VaultMetadataRequest;
import org.springframework.vault.support.VaultMetadataResponse;

/**
 *  Interface that specifies kv metadata related operations
 *
 * @author Zakaria Amine
 * @see <a href="https://www.vaultproject.io/api-docs/secret/kv/kv-v2#update-metadata">kv backend metadata api docs</a>
 */
public interface VaultKeyValueMetadataOperations {

  /**
   * permanently deletes the key metadata and all version data for the specified key. All version history will be removed.
   * @param path the secret path, must not be null or empty
   */
  void delete(String path);

  /**
   * retrieves the metadata and versions for the secret at the specified path.
   * @param path the secret path, must not be null or empty
   * @return {@link VaultMetadataResponse}
   */
  VaultMetadataResponse get(String path);

  /**
   * Updates the secret metadata, or creates new metadata if not present.
   *
   * @param path the secret path, must not be null or empty
   * @param body {@link VaultMetadataRequest}
   */
  void put(String path, VaultMetadataRequest body);
}
