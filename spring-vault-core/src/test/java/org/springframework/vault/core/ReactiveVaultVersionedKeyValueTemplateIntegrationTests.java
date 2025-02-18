/*
 * Copyright 2018-2025 the original author or authors.
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

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.VaultException;
import org.springframework.vault.domain.Person;
import org.springframework.vault.support.VaultMetadataRequest;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.support.Versioned.Version;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.RequiresVaultVersion;
import org.springframework.vault.util.VaultInitializer;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ReactiveVaultVersionedKeyValueTemplate}.
 *
 * @author Timothy Weiand
 */
@ExtendWith(SpringExtension.class)
@RequiresVaultVersion(VaultInitializer.VERSIONING_INTRODUCED_WITH_VALUE)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
class ReactiveVaultVersionedKeyValueTemplateIntegrationTests extends IntegrationTestSupport {

	ReactiveVaultVersionedKeyValueOperations reactiveVersionedOperations;

	@Autowired
	ReactiveVaultVersionedKeyValueTemplateIntegrationTests(ReactiveVaultOperations reactiveVaultOperations) {
		reactiveVersionedOperations = reactiveVaultOperations.opsForVersionedKeyValue("versioned");
	}

	@Test
	void shouldCreateVersionedSecret() {

		var secret = Collections.singletonMap("key", "value");
		var key = UUID.randomUUID().toString();

		reactiveVersionedOperations.put(key, Versioned.create(secret)).as(StepVerifier::create).assertNext(metadata -> {
			assertThat(metadata.isDestroyed()).isFalse();
			assertThat(metadata.getCreatedAt()).isBetween(Instant.now().minusSeconds(60),
					Instant.now().plusSeconds(60));
			assertThat(metadata.getDeletedAt()).isNull();
		}).verifyComplete();

	}

	@Test
	void shouldCreateComplexVersionedSecret() {

		var person = new Person();
		person.setFirstname("Walter");
		person.setLastname("White");
		var key = UUID.randomUUID().toString();

		reactiveVersionedOperations.put(key, Versioned.create(person))
			.as(StepVerifier::create)
			.assertNext(m -> assertThat(m.getVersion().getVersion()).isEqualTo(1))
			.verifyComplete();

		reactiveVersionedOperations.get(key, Person.class)
			.as(StepVerifier::create)
			.assertNext(versioned -> assertThat(versioned.getRequiredData()).isEqualTo(person));
	}

	@Test
	void shouldCreateVersionedWithCAS() {

		var secret = Collections.singletonMap("key", "value");
		var key = UUID.randomUUID().toString();

		reactiveVersionedOperations.put(key, Versioned.create(secret, Version.unversioned()))
			.as(StepVerifier::create)
			.assertNext(m -> assertThat(m.getVersion().getVersion()).isEqualTo(1))
			.verifyComplete();

		// this should fail
		reactiveVersionedOperations.put(key, Versioned.create(secret, Version.unversioned()))
			.as(StepVerifier::create)
			.verifyErrorSatisfies(throwable -> assertThat(throwable).isExactlyInstanceOf(VaultException.class)
				.hasMessageContaining("check-and-set parameter did not match the current version"));
	}

	@Test
	void shouldWriteSecretWithCustomMetadata() {

		Person person = new Person();
		person.setFirstname("Walter");
		person.setLastname("White");

		String key = UUID.randomUUID().toString();

		Map<String, String> customMetadata = new HashMap<>();
		customMetadata.put("foo", "bar");
		customMetadata.put("uid", "werwer");

		reactiveVersionedOperations.put(key, Versioned.create(person)).then().as(StepVerifier::create).verifyComplete();

		VaultMetadataRequest request = VaultMetadataRequest.builder().customMetadata(customMetadata).build();

		reactiveVersionedOperations.opsForKeyValueMetadata()
			.put(key, request)
			.as(StepVerifier::create)
			.verifyComplete();

		reactiveVersionedOperations.get(key, Person.class).as(StepVerifier::create).assertNext(versioned -> {
			assertThat(versioned.getRequiredMetadata().getCustomMetadata()).containsEntry("foo", "bar");
		}).verifyComplete();
	}

	@Test
	void shouldReadAndWriteVersionedSecret() {

		var secret = Collections.singletonMap("key", "value");
		var key = UUID.randomUUID().toString();

		reactiveVersionedOperations.put(key, Versioned.create(secret))
			.as(StepVerifier::create)
			.assertNext(m -> assertThat(m.getVersion().getVersion()).isEqualTo(1))
			.verifyComplete();

		reactiveVersionedOperations.get(key).as(StepVerifier::create).assertNext(loaded -> {
			assertThat(loaded.getRequiredData()).isEqualTo(secret);
			assertThat(loaded.getRequiredMetadata()).isNotNull();
			assertThat(loaded.getVersion()).isEqualTo(Version.from(1));
		}).verifyComplete();
	}

	@Test
	void shouldListExistingSecrets() {

		var secret = Collections.singletonMap("key", "value");
		var key = UUID.randomUUID().toString();

		reactiveVersionedOperations.put(key, secret)
			.as(StepVerifier::create)
			.assertNext(m -> assertThat(m.getVersion().getVersion()).isEqualTo(1))
			.verifyComplete();

		reactiveVersionedOperations.list("")
			.collectList()
			.as(StepVerifier::create)
			.assertNext(list -> assertThat(list).contains(key));
	}

	@Test
	void shouldReadDifferentVersions() {

		var key = UUID.randomUUID().toString();

		reactiveVersionedOperations.put(key, Collections.singletonMap("key", "v1"))
			.as(StepVerifier::create)
			.assertNext(m -> assertThat(m.getVersion().getVersion()).isEqualTo(1))
			.verifyComplete();
		reactiveVersionedOperations.put(key, Collections.singletonMap("key", "v2"))
			.as(StepVerifier::create)
			.assertNext(m -> assertThat(m.getVersion().getVersion()).isEqualTo(2))
			.verifyComplete();

		reactiveVersionedOperations.get(key, Version.from(1))
			.as(StepVerifier::create)
			.assertNext(versioned -> assertThat(versioned.getData()).isEqualTo(Collections.singletonMap("key", "v1")));

		reactiveVersionedOperations.get(key, Version.from(2))
			.as(StepVerifier::create)
			.assertNext(versioned -> assertThat(versioned.getData()).isEqualTo(Collections.singletonMap("key", "v2")));
	}

	@Test
	void shouldDeleteMostRecentVersion() {
		var key = UUID.randomUUID().toString();

		reactiveVersionedOperations.put(key, Collections.singletonMap("key", "v1"))
			.as(StepVerifier::create)
			.assertNext(m -> assertThat(m.getVersion().getVersion()).isEqualTo(1))
			.verifyComplete();
		reactiveVersionedOperations.put(key, Collections.singletonMap("key", "v2"))
			.as(StepVerifier::create)
			.assertNext(m -> assertThat(m.getVersion().getVersion()).isEqualTo(2))
			.verifyComplete();

		reactiveVersionedOperations.delete(key).as(StepVerifier::create).verifyComplete();

		reactiveVersionedOperations.get(key).as(StepVerifier::create).assertNext(versioned -> {
			assertThat(versioned.getData()).isNull();
			assertThat(versioned.getVersion()).isEqualTo(Version.from(2));
			assertThat(versioned.getRequiredMetadata().isDestroyed()).isFalse();
			assertThat(versioned.getRequiredMetadata().getDeletedAt()).isBetween(Instant.now().minusSeconds(60),
					Instant.now().plusSeconds(60));
		});
	}

	@Test
	void shouldUndeleteVersion() {

		var key = UUID.randomUUID().toString();

		reactiveVersionedOperations.put(key, Collections.singletonMap("key", "v1"))
			.as(StepVerifier::create)
			.assertNext(m -> assertThat(m.getVersion().getVersion()).isEqualTo(1))
			.verifyComplete();
		reactiveVersionedOperations.put(key, Collections.singletonMap("key", "v2"))
			.as(StepVerifier::create)
			.assertNext(m -> assertThat(m.getVersion().getVersion()).isEqualTo(2))
			.verifyComplete();

		reactiveVersionedOperations.delete(key, Version.from(2)).as(StepVerifier::create).verifyComplete();
		reactiveVersionedOperations.undelete(key, Version.from(2)).as(StepVerifier::create).verifyComplete();

		reactiveVersionedOperations.get(key).as(StepVerifier::create).assertNext(versioned -> {
			assertThat(versioned.getRequiredData()).isEqualTo(Collections.singletonMap("key", "v2"));
			assertThat(versioned.getVersion()).isEqualTo(Version.from(2));
			assertThat(versioned.getRequiredMetadata().isDestroyed()).isFalse();
			assertThat(versioned.getRequiredMetadata().getDeletedAt()).isNull();
		});
	}

	@Test
	void shouldDeleteIntermediateRecentVersion() {

		var key = UUID.randomUUID().toString();

		reactiveVersionedOperations.put(key, Collections.singletonMap("key", "v1"))
			.as(StepVerifier::create)
			.assertNext(m -> assertThat(m.getVersion().getVersion()).isEqualTo(1))
			.verifyComplete();
		reactiveVersionedOperations.put(key, Collections.singletonMap("key", "v2"))
			.as(StepVerifier::create)
			.assertNext(m -> assertThat(m.getVersion().getVersion()).isEqualTo(2))
			.verifyComplete();

		reactiveVersionedOperations.delete(key, Version.from(1)).as(StepVerifier::create).verifyComplete();

		reactiveVersionedOperations.get(key, Version.from(1)).as(StepVerifier::create).assertNext(versioned -> {
			assertThat(versioned.getData()).isNull();
			assertThat(versioned.getVersion()).isEqualTo(Version.from(1));
			assertThat(versioned.getRequiredMetadata().isDestroyed()).isFalse();
			assertThat(versioned.getRequiredMetadata().getDeletedAt()).isBetween(Instant.now().minusSeconds(60),
					Instant.now().plusSeconds(60));
		}).verifyComplete();
	}

	@Test
	void shouldDestroyVersion() {

		var key = UUID.randomUUID().toString();

		reactiveVersionedOperations.put(key, Collections.singletonMap("key", "v1"))
			.as(StepVerifier::create)
			.assertNext(m -> assertThat(m.getVersion().getVersion()).isEqualTo(1))
			.verifyComplete();

		reactiveVersionedOperations.put(key, Collections.singletonMap("key", "v2"))
			.as(StepVerifier::create)
			.assertNext(m -> assertThat(m.getVersion().getVersion()).isEqualTo(2))
			.verifyComplete();

		reactiveVersionedOperations.destroy(key, Version.from(2)).as(StepVerifier::create).verifyComplete();

		reactiveVersionedOperations.get(key).as(StepVerifier::create).assertNext(versioned -> {
			assertThat(versioned.getData()).isNull();
			assertThat(versioned.getVersion()).isEqualTo(Version.from(2));
			assertThat(versioned.getRequiredMetadata().isDestroyed()).isTrue();
			assertThat(versioned.getRequiredMetadata().getDeletedAt()).isNull();
		}).verifyComplete();
	}

}
