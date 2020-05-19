package org.springframework.vault.core;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.support.VaultMetadataRequest;
import org.springframework.vault.support.VaultMetadataResponse;
import org.springframework.vault.support.VaultResponse;

import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
public class VaultKeyValueMetadataTemplateIntegrationTests extends AbstractVaultKeyValueTemplateIntegrationTests {

  private static final String SECRET_NAME = "test";
  private VaultKeyValueMetadataOperations vaultKeyValueMetadataOperations;

  VaultKeyValueMetadataTemplateIntegrationTests() {
    super("secret", VaultKeyValueOperationsSupport.KeyValueBackend.versioned());
  }

  @BeforeEach
  void setup() {
    Map<String, Object> secret = new HashMap<>();
    secret.put("key", "value");

    kvOperations.put(SECRET_NAME, secret);

    vaultKeyValueMetadataOperations = vaultOperations.opsForKeyValueMetadata();
  }

  @Test
  public void shouldReadMetadataForANewKVEntry() {

    VaultMetadataResponse metadataResponse = vaultKeyValueMetadataOperations.get(SECRET_NAME);

    assertThat(metadataResponse.getMaxVersions()).isEqualTo(0);
    assertThat(metadataResponse.getCurrentVersion()).isEqualTo(1);
    assertThat(StreamSupport.stream(Spliterators.spliteratorUnknownSize(metadataResponse.getVersions().fields(),
        Spliterator.DISTINCT), false).count()).isEqualTo(1);
    assertThat(metadataResponse.isCasRequired()).isFalse();
    assertThat(metadataResponse.getDeleteVersionAfter()).isEqualTo("0s");
    assertThat(metadataResponse.getCreatedTime().isBefore(Instant.now())).isTrue();
    assertThat(metadataResponse.getUpdatedTime().isBefore(Instant.now())).isTrue();

    JsonNode version1 = metadataResponse.getVersions().get("1");

    assertThat(version1.get("deletion_time").asText()).isEmpty();
    assertThat(LocalDateTime.parse(version1.get("created_time").asText(), DateTimeFormatter.ISO_ZONED_DATE_TIME).isBefore(LocalDateTime.now())).isTrue();
  }

  @Test
  public void shouldUpdateMetadataVersions() {
    Map<String, Object> secret = new HashMap<>();
    secret.put("newkey", "newvalue");
    kvOperations.put(SECRET_NAME, secret);

    VaultMetadataResponse metadataResponse = vaultKeyValueMetadataOperations.get(SECRET_NAME);

    assertThat(metadataResponse.getCurrentVersion()).isEqualTo(2);
    assertThat(StreamSupport.stream(Spliterators.spliteratorUnknownSize(metadataResponse.getVersions().fields(),
        Spliterator.DISTINCT), false).count()).isEqualTo(2);
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
    JsonNode version1 = metadataResponse.getVersions().get("1");
    assertThat(LocalDateTime.parse(version1.get("deletion_time").asText(), DateTimeFormatter.ISO_ZONED_DATE_TIME).isBefore(LocalDateTime.now())).isTrue();

    vaultKeyValueMetadataOperations.delete(SECRET_NAME);

    VaultResponse response = kvOperations.get(SECRET_NAME);

    assertThat(response).isNull();
  }

  @AfterEach
  void cleanup() {
   vaultOperations.opsForKeyValueMetadata().delete(SECRET_NAME);
  }
}
