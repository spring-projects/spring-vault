/*
 * Copyright 2020-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.vault.core;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.support.VaultMetadataRequest;
import org.springframework.vault.support.VaultMetadataResponse;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.util.Version;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.support.VaultMetadataRequest;
import org.springframework.vault.support.VaultMetadataResponse;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.util.Version;

/**
 * Integration tests for {@link VaultKeyValueMetadataOperations}.
 *
 * @author Zakaria Amine
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
class VaultKeyValueMetadataTemplateIntegrationTests extends AbstractVaultKeyValueTemplateIntegrationTests {

	private static final String SECRET_NAME = "regular-test";

	private static final String CAS_SECRET_NAME = "cas-test";

	private VaultKeyValueMetadataOperations vaultKeyValueMetadataOperations;

	VaultKeyValueMetadataTemplateIntegrationTests() {
		super("versioned", VaultKeyValueOperationsSupport.KeyValueBackend.versioned());
	}

	@BeforeEach
	void setup() {

		this.vaultKeyValueMetadataOperations = this.vaultOperations.opsForVersionedKeyValue("versioned")
			.opsForKeyValueMetadata();

		try {
			this.vaultKeyValueMetadataOperations.delete(SECRET_NAME);
		}
		catch (Exception e) {
			// ignore
		}

		try {
			this.vaultKeyValueMetadataOperations.delete(CAS_SECRET_NAME);
		}
		catch (Exception e) {
			// ignore
		}

		Map<String, Object> secret = new HashMap<>();
		secret.put("key", "value");

		this.kvOperations.put(SECRET_NAME, secret);
	}

	@Test
	void shouldReadMetadataForANewKVEntry() {

		VaultMetadataResponse metadataResponse = this.vaultKeyValueMetadataOperations.get(SECRET_NAME);

		assertThat(metadataResponse.getMaxVersions()).isEqualTo(0);
		assertThat(metadataResponse.getCurrentVersion()).isEqualTo(1);
		assertThat(metadataResponse.getVersions()).hasSize(1);
		assertThat(metadataResponse.isCasRequired()).isFalse();
		assertThat(metadataResponse.getCreatedTime().isBefore(Instant.now())).isTrue();
		assertThat(metadataResponse.getUpdatedTime().isBefore(Instant.now())).isTrue();

		Versioned.Metadata version1 = metadataResponse.getVersions().get(0);

		if (prepare().getVersion().isGreaterThanOrEqualTo(Version.parse("1.2.0"))) {

			assertThat(metadataResponse.getDeleteVersionAfter()).isEqualTo(Duration.ZERO);

			assertThat(version1.getDeletedAt()).isNull();
			assertThat(version1.getCreatedAt()).isBefore(Instant.now());
		}

		assertThat(version1.getVersion().getVersion()).isEqualTo(1);
	}

	@Test
	void shouldUpdateMetadataVersions() {

		Map<String, Object> secret = new HashMap<>();
		secret.put("newkey", "newvalue");
		this.kvOperations.put(SECRET_NAME, secret);

		VaultMetadataResponse metadataResponse = this.vaultKeyValueMetadataOperations.get(SECRET_NAME);

		assertThat(metadataResponse.getCurrentVersion()).isEqualTo(2);
		assertThat(metadataResponse.getVersions()).hasSize(2);
	}

	@Test
	void shouldUpdateKVMetadata() {

		Map<String, Object> secret = new HashMap<>();
		secret.put("key", "value");

		this.kvOperations.put(CAS_SECRET_NAME, secret);

		Duration duration = Duration.ofMinutes(30).plusHours(6).plusSeconds(30);
		VaultMetadataRequest request = VaultMetadataRequest.builder()
			.casRequired(true)
			.deleteVersionAfter(duration)
			.maxVersions(20)
			.build();

		this.vaultKeyValueMetadataOperations.put(CAS_SECRET_NAME, request);

		VaultMetadataResponse metadataResponseAfterUpdate = this.vaultKeyValueMetadataOperations.get(CAS_SECRET_NAME);

		assertThat(metadataResponseAfterUpdate.isCasRequired()).isEqualTo(request.isCasRequired());
		assertThat(metadataResponseAfterUpdate.getMaxVersions()).isEqualTo(request.getMaxVersions());

		if (prepare().getVersion().isGreaterThanOrEqualTo(Version.parse("1.2.0"))) {
			assertThat(metadataResponseAfterUpdate.getDeleteVersionAfter()).isEqualTo(duration);
		}
	}

	@Test
	void shouldDeleteMetadata() {

		this.kvOperations.delete(SECRET_NAME);
		VaultMetadataResponse metadataResponse = this.vaultKeyValueMetadataOperations.get(SECRET_NAME);
		Versioned.Metadata version1 = metadataResponse.getVersions().get(0);
		assertThat(version1.getDeletedAt()).isBefore(Instant.now());

		this.vaultKeyValueMetadataOperations.delete(SECRET_NAME);

		VaultResponse response = this.kvOperations.get(SECRET_NAME);
		assertThat(response).isNull();
	}

}
