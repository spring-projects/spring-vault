package org.springframework.vault.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VaultMetadataResponseWrapper {

  private VaultMetadataResponse data;

  public VaultMetadataResponse getData() {
    return data;
  }

  public void setData(VaultMetadataResponse data) {
    this.data = data;
  }
}
