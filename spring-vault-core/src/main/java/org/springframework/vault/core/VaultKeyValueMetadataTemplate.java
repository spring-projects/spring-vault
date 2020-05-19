package org.springframework.vault.core;

import java.io.IOException;

import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultMetadataRequest;
import org.springframework.vault.support.VaultMetadataResponse;
import org.springframework.vault.support.VaultMetadataResponseWrapper;
import org.springframework.web.client.HttpStatusCodeException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class VaultKeyValueMetadataTemplate implements VaultKeyValueMetadataOperations {

  private final VaultOperations vaultOperations;

  private static final ObjectMapper OBJECT_MAPPER;

  static {
    ObjectMapper mapper = new ObjectMapper();
    JavaTimeModule module = new JavaTimeModule();
    mapper.registerModule(module);
    mapper.configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, true);
    OBJECT_MAPPER = mapper;
  }


  public VaultKeyValueMetadataTemplate(VaultOperations vaultOperations) {
    Assert.notNull(vaultOperations, "VaultOperations must not be null");
    this.vaultOperations = vaultOperations;
  }

  @Override
  public void delete(String path) {
    Assert.hasText(path, "Path must not be empty");
    vaultOperations.doWithSession(restOperations -> {

      try {
        restOperations.delete("/secret/metadata/" + path);
        return null;
      }
      catch (HttpStatusCodeException e) {
        throw VaultResponses.buildException(e, path);
      }
    });
  }

  @Override
  public VaultMetadataResponse get(String path) {
    Assert.hasText(path, "Path must not be empty");

    return vaultOperations.doWithSession(restOperations -> {
      try {
       String vaultMetadataResponseJson = restOperations.getForObject("/secret/metadata/" + path, String.class);
        return OBJECT_MAPPER.readValue(vaultMetadataResponseJson, VaultMetadataResponseWrapper.class).getData();
      } catch (HttpStatusCodeException e) {
        throw VaultResponses.buildException(e, path);
      } catch (IOException e) {
        throw new VaultException("Cannot deserialize response", e);
      }
    });
  }

  @Override
  public void put(String path, VaultMetadataRequest body) {
    Assert.hasText(path, "Path must not be empty");
    Assert.notNull(body, "Body must not be null");
    vaultOperations.doWithSession(restOperations -> {
      try {
        restOperations.put("/secret/metadata/" + path, body);
        return null;
      }
      catch (HttpStatusCodeException e) {
        throw VaultResponses.buildException(e, path);
      }
    });
  }
}
