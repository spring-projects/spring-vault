/*
 * Copyright 2020-2025 the original author or authors.
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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.support.VaultMetadataRequest;
import org.springframework.vault.util.Version;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.vault.core.VaultKeyValueOperationsSupport.*;

/**
 * Integration tests for {@link VaultKeyValueMetadataOperations}.
 *
 * @author Timothy R. Weiand
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
class ReactiveVaultKeyValueMetadataTemplateIntegrationTests
		extends AbstractReactiveVaultKeyValueTemplateIntegrationTests {

	private static final String SECRET_NAME = "regular-test";

	private static final String CAS_SECRET_NAME = "cas-test";

	private ReactiveVaultKeyValueMetadataOperations vaultKeyValueMetadataOperations;

	ReactiveVaultKeyValueMetadataTemplateIntegrationTests() {
		super("versioned", KeyValueBackend.versioned());
	}

	@BeforeEach
	void setup() {

		vaultKeyValueMetadataOperations = vaultOperations.opsForVersionedKeyValue("versioned").opsForKeyValueMetadata();

		for (var key : List.of(SECRET_NAME, CAS_SECRET_NAME)) {
			vaultKeyValueMetadataOperations.delete(key)
				.onErrorResume(e -> Mono.empty())
				.as(StepVerifier::create)
				.verifyComplete();
		}

		var secret = new HashMap<>();
		secret.put("key", "value");

		kvOperations.put(SECRET_NAME, secret).as(StepVerifier::create).verifyComplete();
	}

	@Test
	void shouldReadMetadataForANewKVEntry() {

		vaultKeyValueMetadataOperations.get(SECRET_NAME).as(StepVerifier::create).assertNext(metadataResponse -> {
			assertThat(metadataResponse.getMaxVersions()).isEqualTo(0);
			assertThat(metadataResponse.getCurrentVersion()).isEqualTo(1);
			assertThat(metadataResponse.getVersions()).hasSize(1);
			assertThat(metadataResponse.isCasRequired()).isFalse();
			assertThat(metadataResponse.getCreatedTime().isBefore(Instant.now())).isTrue();
			assertThat(metadataResponse.getUpdatedTime().isBefore(Instant.now())).isTrue();

			var version1 = metadataResponse.getVersions().get(0);

			if (prepare().getVersion().isGreaterThanOrEqualTo(Version.parse("1.2.0"))) {

				assertThat(metadataResponse.getDeleteVersionAfter()).isEqualTo(Duration.ZERO);

				assertThat(version1.getDeletedAt()).isNull();
				assertThat(version1.getCreatedAt()).isBefore(Instant.now());
			}

			assertThat(version1.getVersion().getVersion()).isEqualTo(1);

		}).verifyComplete();
	}

	@Test
	void shouldUpdateMetadataVersions() {

		Map<String, Object> secret = Map.of("newkey", "newvalue");
		kvOperations.put(SECRET_NAME, secret).as(StepVerifier::create).verifyComplete();

		vaultKeyValueMetadataOperations.get(SECRET_NAME).as(StepVerifier::create).assertNext(metadataResponse -> {
			assertThat(metadataResponse.getCurrentVersion()).isEqualTo(2);
			assertThat(metadataResponse.getVersions()).hasSize(2);
		}).verifyComplete();

	}

	@Test
	void shouldUpdateKVMetadata() {

		var secret = Map.of("key", "value");

		kvOperations.put(CAS_SECRET_NAME, secret).as(StepVerifier::create).verifyComplete();

		Duration duration = Duration.ofMinutes(30).plusHours(6).plusSeconds(30);
		VaultMetadataRequest request = VaultMetadataRequest.builder()
			.casRequired(true)
			.deleteVersionAfter(duration)
			.maxVersions(20)
			.build();

		vaultKeyValueMetadataOperations.put(CAS_SECRET_NAME, request).as(StepVerifier::create).verifyComplete();

		final var version = prepare().getVersion();

		vaultKeyValueMetadataOperations.get(CAS_SECRET_NAME)
			.as(StepVerifier::create)
			.assertNext(metadataResponseAfterUpdate -> {
				assertThat(metadataResponseAfterUpdate.isCasRequired()).isEqualTo(request.isCasRequired());
				assertThat(metadataResponseAfterUpdate.getMaxVersions()).isEqualTo(request.getMaxVersions());

				if (version.isGreaterThanOrEqualTo(Version.parse("1.2.0"))) {
					assertThat(metadataResponseAfterUpdate.getDeleteVersionAfter()).isEqualTo(duration);
				}
			})
			.verifyComplete();
	}

	@Test
	void shouldDeleteMetadata() {

		kvOperations.delete(SECRET_NAME).as(StepVerifier::create).verifyComplete();

		vaultKeyValueMetadataOperations.get(SECRET_NAME).as(StepVerifier::create).assertNext(metadataResponse -> {
			var version1 = metadataResponse.getVersions().get(0);
			assertThat(version1.getDeletedAt()).isBefore(Instant.now());
		}).verifyComplete();

		vaultKeyValueMetadataOperations.delete(SECRET_NAME).as(StepVerifier::create).verifyComplete();

		kvOperations.get(SECRET_NAME).map(r -> r).as(StepVerifier::create).verifyComplete();
	}

}
