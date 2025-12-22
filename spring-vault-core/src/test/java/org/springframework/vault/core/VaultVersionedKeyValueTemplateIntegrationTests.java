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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.VaultException;
import org.springframework.vault.domain.Person;
import org.springframework.vault.support.VaultMetadataRequest;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.support.Versioned.Metadata;
import org.springframework.vault.support.Versioned.Version;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.RequiresVaultVersion;
import org.springframework.vault.util.VaultInitializer;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link VaultVersionedKeyValueTemplate}.
 *
 * @author Mark Paluch
 * @author Jeroen Willemsen
 */
@ExtendWith(SpringExtension.class)
@RequiresVaultVersion(VaultInitializer.VERSIONING_INTRODUCED_WITH_VALUE)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
class VaultVersionedKeyValueTemplateIntegrationTests extends IntegrationTestSupport {

	@Autowired
	VaultOperations vaultOperations;

	VaultVersionedKeyValueOperations versionedOperations;

	@BeforeEach
	void before() {
		this.versionedOperations = this.vaultOperations.opsForVersionedKeyValue("versioned");
	}

	@Test
	void shouldCreateVersionedSecret() {

		Map<String, String> secret = Collections.singletonMap("key", "value");

		String key = UUID.randomUUID().toString();

		Metadata metadata = this.versionedOperations.put(key, Versioned.create(secret));

		assertThat(metadata.isDestroyed()).isFalse();
		assertThat(metadata.getCreatedAt()).isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(60));
		assertThat(metadata.getDeletedAt()).isNull();
	}

	@Test
	void shouldCreateComplexVersionedSecret() {

		Person person = new Person();
		person.setFirstname("Walter");
		person.setLastname("White");

		String key = UUID.randomUUID().toString();
		this.versionedOperations.put(key, Versioned.create(person));

		Versioned<Person> versioned = this.versionedOperations.get(key, Person.class);

		assertThat(versioned.getRequiredData()).isEqualTo(person);
		assertThat(versioned.getRequiredMetadata().getCustomMetadata()).isEmpty();
	}

	@Test
	void shouldCreateVersionedWithCAS() {

		Map<String, String> secret = Collections.singletonMap("key", "value");

		String key = UUID.randomUUID().toString();

		this.versionedOperations.put(key, Versioned.create(secret, Version.unversioned()));

		// this should fail
		assertThatThrownBy(() -> this.versionedOperations.put(key, Versioned.create(secret, Version.unversioned())))
				.isExactlyInstanceOf(VaultException.class)
				.hasMessageContaining("check-and-set parameter did not match the current version");
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

		this.versionedOperations.put(key, Versioned.create(person));

		VaultMetadataRequest request = VaultMetadataRequest.builder().customMetadata(customMetadata).build();

		this.versionedOperations.opsForKeyValueMetadata().put(key, request);

		Versioned<Person> versioned = this.versionedOperations.get(key, Person.class);
		assertThat(versioned.getRequiredMetadata().getCustomMetadata()).containsEntry("foo", "bar");
	}

	@Test
	void shouldReadAndWriteVersionedSecret() {

		Map<String, String> secret = Collections.singletonMap("key", "value");

		String key = UUID.randomUUID().toString();

		this.versionedOperations.put(key, Versioned.create(secret));

		Versioned<Map<String, Object>> loaded = this.versionedOperations.get(key);

		assertThat(loaded.getRequiredData()).isEqualTo(secret);
		assertThat(loaded.getRequiredMetadata()).isNotNull();
		assertThat(loaded.getVersion()).isEqualTo(Version.from(1));
	}

	@Test
	void shouldListExistingSecrets() {

		Map<String, String> secret = Collections.singletonMap("key", "value");
		String key = UUID.randomUUID().toString();

		this.versionedOperations.put(key, secret);

		assertThat(this.versionedOperations.list("")).contains(key);
	}

	@Test
	void shouldReadDifferentVersions() {

		String key = UUID.randomUUID().toString();

		this.versionedOperations.put(key, Collections.singletonMap("key", "v1"));
		this.versionedOperations.put(key, Collections.singletonMap("key", "v2"));

		assertThat(this.versionedOperations.get(key, Version.from(1)).getRequiredData())
				.isEqualTo(Collections.singletonMap("key", "v1"));
		assertThat(this.versionedOperations.get(key, Version.from(2)).getRequiredData())
				.isEqualTo(Collections.singletonMap("key", "v2"));
	}

	@Test
	void shouldDeleteMostRecentVersion() {

		String key = UUID.randomUUID().toString();

		this.versionedOperations.put(key, Collections.singletonMap("key", "v1"));
		this.versionedOperations.put(key, Collections.singletonMap("key", "v2"));

		this.versionedOperations.delete(key);

		Versioned<Map<String, Object>> versioned = this.versionedOperations.get(key);

		assertThat(versioned.getData()).isNull();
		assertThat(versioned.getVersion()).isEqualTo(Version.from(2));
		assertThat(versioned.getRequiredMetadata().isDestroyed()).isFalse();
		assertThat(versioned.getRequiredMetadata().getDeletedAt()).isBetween(Instant.now().minusSeconds(60),
				Instant.now().plusSeconds(60));
	}

	@Test
	void shouldUndeleteVersion() {

		String key = UUID.randomUUID().toString();

		this.versionedOperations.put(key, Collections.singletonMap("key", "v1"));
		this.versionedOperations.put(key, Collections.singletonMap("key", "v2"));

		this.versionedOperations.delete(key, Version.from(2));
		this.versionedOperations.undelete(key, Version.from(2));

		Versioned<Map<String, Object>> versioned = this.versionedOperations.get(key);

		assertThat(versioned.getRequiredData()).isEqualTo(Collections.singletonMap("key", "v2"));
		assertThat(versioned.getVersion()).isEqualTo(Version.from(2));
		assertThat(versioned.getRequiredMetadata().isDestroyed()).isFalse();
		assertThat(versioned.getRequiredMetadata().getDeletedAt()).isNull();
	}

	@Test
	void shouldDeleteIntermediateRecentVersion() {

		String key = UUID.randomUUID().toString();

		this.versionedOperations.put(key, Collections.singletonMap("key", "v1"));
		this.versionedOperations.put(key, Collections.singletonMap("key", "v2"));

		this.versionedOperations.delete(key, Version.from(1));

		Versioned<Map<String, Object>> versioned = this.versionedOperations.get(key, Version.from(1));

		assertThat(versioned.getData()).isNull();
		assertThat(versioned.getVersion()).isEqualTo(Version.from(1));
		assertThat(versioned.getRequiredMetadata().isDestroyed()).isFalse();
		assertThat(versioned.getRequiredMetadata().getDeletedAt()).isBetween(Instant.now().minusSeconds(60),
				Instant.now().plusSeconds(60));
	}

	@Test
	void shouldDestroyVersion() {

		String key = UUID.randomUUID().toString();

		this.versionedOperations.put(key, Collections.singletonMap("key", "v1"));
		this.versionedOperations.put(key, Collections.singletonMap("key", "v2"));

		this.versionedOperations.destroy(key, Version.from(2));

		Versioned<Map<String, Object>> versioned = this.versionedOperations.get(key);

		assertThat(versioned.getData()).isNull();
		assertThat(versioned.getVersion()).isEqualTo(Version.from(2));
		assertThat(versioned.getRequiredMetadata().isDestroyed()).isTrue();
		assertThat(versioned.getRequiredMetadata().getDeletedAt()).isNull();
	}

}
