/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.Map;
import java.util.UUID;

import lombok.Data;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.support.Versioned.Metadata;
import org.springframework.vault.support.Versioned.Version;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.VaultRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assume.assumeTrue;

/**
 * Integration tests for {@link VaultVersionedKeyValueTemplate}.
 *
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
public class VaultVersionedKeyValueTemplateIntegrationTests extends
		IntegrationTestSupport {

	@Autowired
	private VaultOperations vaultOperations;
	private VaultVersionedKeyValueOperations versionedOperations;

	@Before
	public void before() {

		assumeTrue(prepare().getVersion().isGreaterThanOrEqualTo(
				VaultRule.VERSIONING_INTRODUCED_WITH));

		versionedOperations = vaultOperations.opsForVersionedKeyValue("versioned");
	}

	@Test
	public void shouldCreateVersionedSecret() {

		Map<String, String> secret = Collections.singletonMap("key", "value");

		String key = UUID.randomUUID().toString();

		Metadata metadata = versionedOperations.put(key, Versioned.create(secret));

		assertThat(metadata.isDestroyed()).isFalse();
		assertThat(metadata.getCreatedAt()).isBetween(Instant.now().minusSeconds(60),
				Instant.now().plusSeconds(60));
		assertThat(metadata.getDeletedAt()).isNull();
	}

	@Test
	public void shouldCreateComplexVersionedSecret() {

		Person person = new Person();
		person.setFirstname("Walter");
		person.setLastname("White");

		String key = UUID.randomUUID().toString();
		versionedOperations.put(key, Versioned.create(person));

		Versioned<Person> versioned = versionedOperations.get(key, Person.class);

		assertThat(versioned.getData()).isEqualTo(person);
	}

	@Test
	public void shouldCreateVersionedWithCAS() {

		Map<String, String> secret = Collections.singletonMap("key", "value");

		String key = UUID.randomUUID().toString();

		versionedOperations.put(key, Versioned.create(secret, Version.unversioned()));

		// this should fail
		assertThatThrownBy(
				() -> versionedOperations.put(key,
						Versioned.create(secret, Version.unversioned())))
				.isExactlyInstanceOf(VaultException.class).hasMessageContaining(
						"check-and-set parameter did not match the current version");
	}

	@Test
	public void shouldReadAndWriteVersionedSecret() {

		Map<String, String> secret = Collections.singletonMap("key", "value");

		String key = UUID.randomUUID().toString();

		versionedOperations.put(key, Versioned.create(secret));

		Versioned<Map<String, Object>> loaded = versionedOperations.get(key);

		assertThat(loaded.getData()).isEqualTo(secret);
		assertThat(loaded.getMetadata()).isNotNull();
		assertThat(loaded.getVersion()).isEqualTo(Version.from(1));
	}

	@Test
	public void shouldListExistingSecrets() {

		Map<String, String> secret = Collections.singletonMap("key", "value");
		String key = UUID.randomUUID().toString();

		versionedOperations.put(key, secret);

		assertThat(versionedOperations.list("")).contains(key);
	}

	@Test
	public void shouldReadDifferentVersions() {

		String key = UUID.randomUUID().toString();

		versionedOperations.put(key, Collections.singletonMap("key", "v1"));
		versionedOperations.put(key, Collections.singletonMap("key", "v2"));

		assertThat(versionedOperations.get(key, Version.from(1)).getData()).isEqualTo(
				Collections.singletonMap("key", "v1"));
		assertThat(versionedOperations.get(key, Version.from(2)).getData()).isEqualTo(
				Collections.singletonMap("key", "v2"));
	}

	@Test
	public void shouldDeleteMostRecentVersion() {

		String key = UUID.randomUUID().toString();

		versionedOperations.put(key, Collections.singletonMap("key", "v1"));
		versionedOperations.put(key, Collections.singletonMap("key", "v2"));

		versionedOperations.delete(key);

		Versioned<Map<String, Object>> versioned = versionedOperations.get(key);

		assertThat(versioned.getData()).isNull();
		assertThat(versioned.getVersion()).isEqualTo(Version.from(2));
		assertThat(versioned.getMetadata().isDestroyed()).isFalse();
		assertThat(versioned.getMetadata().getDeletedAt()).isBetween(
				Instant.now().minusSeconds(60), Instant.now().plusSeconds(60));
	}

	@Test
	public void shouldUndeleteVersion() {

		String key = UUID.randomUUID().toString();

		versionedOperations.put(key, Collections.singletonMap("key", "v1"));
		versionedOperations.put(key, Collections.singletonMap("key", "v2"));

		versionedOperations.delete(key, Version.from(2));
		versionedOperations.undelete(key, Version.from(2));

		Versioned<Map<String, Object>> versioned = versionedOperations.get(key);

		assertThat(versioned.getData()).isEqualTo(Collections.singletonMap("key", "v2"));
		assertThat(versioned.getVersion()).isEqualTo(Version.from(2));
		assertThat(versioned.getMetadata().isDestroyed()).isFalse();
		assertThat(versioned.getMetadata().getDeletedAt()).isNull();
	}

	@Test
	public void shouldDeleteIntermediateRecentVersion() {

		String key = UUID.randomUUID().toString();

		versionedOperations.put(key, Collections.singletonMap("key", "v1"));
		versionedOperations.put(key, Collections.singletonMap("key", "v2"));

		versionedOperations.delete(key, Version.from(1));

		Versioned<Map<String, Object>> versioned = versionedOperations.get(key,
				Version.from(1));

		assertThat(versioned.getData()).isNull();
		assertThat(versioned.getVersion()).isEqualTo(Version.from(1));
		assertThat(versioned.getMetadata().isDestroyed()).isFalse();
		assertThat(versioned.getMetadata().getDeletedAt()).isBetween(
				Instant.now().minusSeconds(60), Instant.now().plusSeconds(60));
	}

	@Test
	public void shouldDestroyVersion() {

		String key = UUID.randomUUID().toString();

		versionedOperations.put(key, Collections.singletonMap("key", "v1"));
		versionedOperations.put(key, Collections.singletonMap("key", "v2"));

		versionedOperations.destroy(key, Version.from(2));

		Versioned<Map<String, Object>> versioned = versionedOperations.get(key);

		assertThat(versioned.getData()).isNull();
		assertThat(versioned.getVersion()).isEqualTo(Version.from(2));
		assertThat(versioned.getMetadata().isDestroyed()).isTrue();
		assertThat(versioned.getMetadata().getDeletedAt()).isNull();
	}

	@Data
	static class Person {

		String firstname;
		String lastname;
	}
}
