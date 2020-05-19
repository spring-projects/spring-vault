package org.springframework.vault.core;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.support.VaultMetadataRequest;
import org.springframework.vault.support.VaultMetadataResponse;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.Versioned;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
public class VaultKeyValueMetadataTemplateIntegrationTests extends AbstractVaultKeyValueTemplateIntegrationTests {

  private static final String SECRET_NAME = "test";
  private VaultKeyValueMetadataOperations vaultKeyValueMetadataOperations;

  VaultKeyValueMetadataTemplateIntegrationTests() {
    super("versioned", VaultKeyValueOperationsSupport.KeyValueBackend.versioned());
  }

  @BeforeEach
  void setup() {
    Map<String, Object> secret = new HashMap<>();
    secret.put("key", "value");

    kvOperations.put(SECRET_NAME, secret);
    vaultKeyValueMetadataOperations = vaultOperations.opsForVersionedKeyValue("versioned").opsForKeyValueMetadata();
  }

  @Test
  public void shouldReadMetadataForANewKVEntry() {

    VaultMetadataResponse metadataResponse = vaultKeyValueMetadataOperations.get(SECRET_NAME);

    assertThat(metadataResponse.getMaxVersions()).isEqualTo(0);
    assertThat(metadataResponse.getCurrentVersion()).isEqualTo(1);
    assertThat(metadataResponse.getVersions()).hasSize(1);
    assertThat(metadataResponse.isCasRequired()).isFalse();
    assertThat(metadataResponse.getDeleteVersionAfter()).isEqualTo("0s");
    assertThat(metadataResponse.getCreatedTime().isBefore(Instant.now())).isTrue();
    assertThat(metadataResponse.getUpdatedTime().isBefore(Instant.now())).isTrue();

    Versioned.Metadata version1 = metadataResponse.getVersions().get(0);

    assertThat(version1.getDeletedAt()).isNull();
    assertThat(version1.getCreatedAt()).isBefore(Instant.now());
    assertThat(version1.getVersion().getVersion()).isEqualTo(1);
  }

  @Test
  public void shouldUpdateMetadataVersions() {
    Map<String, Object> secret = new HashMap<>();
    secret.put("newkey", "newvalue");
    kvOperations.put(SECRET_NAME, secret);

    VaultMetadataResponse metadataResponse = vaultKeyValueMetadataOperations.get(SECRET_NAME);

    assertThat(metadataResponse.getCurrentVersion()).isEqualTo(2);
    assertThat(metadataResponse.getVersions()).hasSize(2);
  }

  @Test
  public void shouldUpdateKVMetadata() {
    VaultMetadataRequest request = VaultMetadataRequest.builder().casRequired(true).deleteVersionAfter("6h30m0s").maxVersions(20).build();

    vaultKeyValueMetadataOperations.put(SECRET_NAME, request);

    VaultMetadataResponse metadataResponseAfterUpdate = vaultKeyValueMetadataOperations.get(SECRET_NAME);

    assertThat(metadataResponseAfterUpdate.isCasRequired()).isEqualTo(request.isCasRequired());
    assertThat(metadataResponseAfterUpdate.getMaxVersions()).isEqualTo(request.getMaxVersions());
    assertThat(metadataResponseAfterUpdate.getDeleteVersionAfter()).isEqualTo(request.getDeleteVersionAfter());
  }

  @Test
  public void shouldDeleteMetadata() {
    kvOperations.delete(SECRET_NAME);
    VaultMetadataResponse metadataResponse = vaultKeyValueMetadataOperations.get(SECRET_NAME);
    Versioned.Metadata version1 = metadataResponse.getVersions().get(0);
    assertThat(version1.getDeletedAt()).isBefore(Instant.now());

    vaultKeyValueMetadataOperations.delete(SECRET_NAME);

    VaultResponse response = kvOperations.get(SECRET_NAME);

    assertThat(response).isNull();
  }

  @AfterEach
  void cleanup() {
    vaultKeyValueMetadataOperations.delete(SECRET_NAME);
  }
}
