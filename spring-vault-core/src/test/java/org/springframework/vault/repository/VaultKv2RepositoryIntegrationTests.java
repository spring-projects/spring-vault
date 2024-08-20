/*
 * Copyright 2017-2024 the original author or authors.
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
package org.springframework.vault.repository;

import static org.assertj.core.api.Assertions.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.history.Revisions;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.ObjectUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultIntegrationTestConfiguration;
import org.springframework.vault.core.VaultSysOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.repository.configuration.EnableVaultRepositories;
import org.springframework.vault.repository.mapping.Secret;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.util.IntegrationTestSupport;

/**
 * Integration tests for Vault repositories using KeyValue version 2.
 *
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultKv2RepositoryIntegrationTests.VaultRepositoryTestConfiguration.class)
class VaultKv2RepositoryIntegrationTests extends IntegrationTestSupport {

	@Configuration
	@EnableVaultRepositories(considerNestedRepositories = true,
			includeFilters = @ComponentScan.Filter(classes = { VersionedRepository.class, SimpleRepository.class },
					type = FilterType.ASSIGNABLE_TYPE))
	static class VaultRepositoryTestConfiguration extends VaultIntegrationTestConfiguration {

	}

	@Autowired
	VersionedRepository versionedRepository;

	@Autowired
	SimpleRepository simpleRepository;

	@Autowired
	VaultTemplate vaultTemplate;

	@BeforeEach
	void before() {

		VaultSysOperations vaultSysOperations = this.vaultTemplate.opsForSys();

		try {
			vaultSysOperations.unmount("versioned");
		}
		catch (VaultException e) {
		}

		vaultSysOperations.mount("versioned",
				VaultMount.builder().type("kv").options(Collections.singletonMap("version", "2")).build());
	}

	@Test
	void loadAndSaveVersioned() {

		VersionedPerson person = new VersionedPerson();
		person.setId("foo-key");
		person.setFirstname("bar");

		VersionedPerson saved = this.versionedRepository.save(person);

		assertThat(saved.getVersion()).isEqualTo(1);

		Iterable<VersionedPerson> all = this.versionedRepository.findAll();

		assertThat(all).contains(saved);
		assertThat(this.versionedRepository.findById("foo-key")).contains(saved);
	}

	@Test
	void shouldReportRevisions() {

		VersionedPerson person = new VersionedPerson();
		person.setId("foo-key");
		person.setFirstname("bar");

		VersionedPerson saved = this.versionedRepository.save(person);

		saved.setFirstname("baz");
		this.versionedRepository.save(saved);

		Revisions<Integer, VersionedPerson> revisions = this.versionedRepository.findRevisions(person.getId());

		assertThat(revisions).hasSize(2);
	}

	@Test
	void loadAndUpdateVersioned() {

		VersionedPerson person = new VersionedPerson();
		person.setId("foo-key");
		person.setFirstname("bar");

		this.versionedRepository.save(person);

		VersionedPerson versionedPerson = this.versionedRepository.findById("foo-key").get();
		versionedPerson.setFirstname("baz");

		VersionedPerson updated = this.versionedRepository.save(versionedPerson);

		assertThat(updated.getVersion()).isEqualTo(2);
	}

	@Test
	@Disabled("Requires newer Spring Data KeyValue version, https://github.com/spring-projects/spring-vault/issues/701")
	void deleteVersioned() {

		VersionedPerson person = new VersionedPerson();
		person.setId("foo-key");
		person.setFirstname("bar");

		this.versionedRepository.save(person);

		VersionedPerson versionedPerson = this.versionedRepository.findById("foo-key").get();
		versionedPerson.setFirstname("baz");

		this.versionedRepository.save(versionedPerson);

		VaultVersionedKeyValueOperations versioned = vaultTemplate.opsForVersionedKeyValue("versioned");

		Versioned<Object> objectVersioned = versioned.get("versionedPerson/foo-key", Versioned.Version.from(1));
		assertThat(objectVersioned.hasData()).isTrue();

		this.versionedRepository.delete(versionedPerson); // delete v1

		Versioned<Object> v1 = versioned.get("versionedPerson/foo-key", Versioned.Version.from(1));
		assertThat(v1.hasData()).isFalse();

		Versioned<Object> v2 = versioned.get("versionedPerson/foo-key", Versioned.Version.from(2));
		assertThat(v2.hasData()).isTrue();
	}

	@Test
	void optimisticLockingInsertShouldFail() {

		VersionedPerson person = new VersionedPerson();
		person.setId("foo-key");
		person.setFirstname("bar");
		person.setVersion(2);

		assertThatExceptionOfType(OptimisticLockingFailureException.class)
			.isThrownBy(() -> this.versionedRepository.save(person));
	}

	@Test
	void optimisticLockingUpdateShouldFail() {

		VersionedPerson person = new VersionedPerson();
		person.setId("foo-key");
		person.setFirstname("bar");

		VersionedPerson saved = this.versionedRepository.save(person);
		saved.setVersion(2);
		saved.setFirstname("baz");

		assertThatExceptionOfType(OptimisticLockingFailureException.class)
			.isThrownBy(() -> this.versionedRepository.save(saved));
	}

	@Test
	void shouldDeleteAll() {

		VersionedPerson person = new VersionedPerson();
		person.setId("foo-key");
		person.setFirstname("bar");

		this.versionedRepository.save(person);
		this.versionedRepository.deleteAll();

		Iterable<VersionedPerson> all = this.versionedRepository.findAll();

		assertThat(all).isEmpty();
	}

	@Test
	void shouldApplyQueryMethod() {

		VersionedPerson walter = new VersionedPerson();
		walter.setId("walter");
		walter.setFirstname("Walter");

		walter = this.versionedRepository.save(walter);

		VersionedPerson skyler = new VersionedPerson();
		skyler.setId("skyler");
		skyler.setFirstname("Skyler");

		skyler = this.versionedRepository.save(skyler);

		Iterable<VersionedPerson> all = this.versionedRepository.findByIdStartsWith("walt");

		assertThat(all).contains(walter).doesNotContain(skyler);
	}

	@Test
	void loadAndUpdate() {

		SimplePerson person = new SimplePerson();
		person.setId("foo-bar");
		person.setFirstname("bar");

		this.simpleRepository.save(person);

		SimplePerson versionedPerson = this.simpleRepository.findById("foo-bar").get();
		versionedPerson.setFirstname("baz");

		this.simpleRepository.save(versionedPerson);
	}

	interface VersionedRepository
			extends CrudRepository<VersionedPerson, String>, RevisionRepository<VersionedPerson, String, Integer> {

		List<VersionedPerson> findByIdStartsWith(String prefix);

	}

	interface SimpleRepository extends CrudRepository<SimplePerson, String> {

		List<SimplePerson> findByIdStartsWith(String prefix);

	}

	@Secret(backend = "versioned")
	static class VersionedPerson {

		@Id
		String id;

		@Version
		long version;

		String firstname;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public long getVersion() {
			return version;
		}

		public void setVersion(long version) {
			this.version = version;
		}

		public String getFirstname() {
			return firstname;
		}

		public void setFirstname(String firstname) {
			this.firstname = firstname;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof VersionedPerson that)) {
				return false;
			}
			if (version != that.version) {
				return false;
			}
			if (!ObjectUtils.nullSafeEquals(id, that.id)) {
				return false;
			}
			return ObjectUtils.nullSafeEquals(firstname, that.firstname);
		}

		@Override
		public int hashCode() {
			int result = ObjectUtils.nullSafeHashCode(id);
			result = 31 * result + (int) (version ^ (version >>> 32));
			result = 31 * result + ObjectUtils.nullSafeHashCode(firstname);
			return result;
		}

		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append(getClass().getSimpleName());
			sb.append(" [id='").append(id).append('\'');
			sb.append(", version=").append(version);
			sb.append(", firstname='").append(firstname).append('\'');
			sb.append(']');
			return sb.toString();
		}

	}

	@Secret(backend = "versioned")
	static class SimplePerson {

		@Id
		String id;

		String firstname;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getFirstname() {
			return firstname;
		}

		public void setFirstname(String firstname) {
			this.firstname = firstname;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof VersionedPerson that)) {
				return false;
			}
			if (!ObjectUtils.nullSafeEquals(id, that.id)) {
				return false;
			}
			return ObjectUtils.nullSafeEquals(firstname, that.firstname);
		}

		@Override
		public int hashCode() {
			int result = ObjectUtils.nullSafeHashCode(id);
			result = 31 * result + ObjectUtils.nullSafeHashCode(firstname);
			return result;
		}

		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append(getClass().getSimpleName());
			sb.append(" [id='").append(id).append('\'');
			sb.append(", firstname='").append(firstname).append('\'');
			sb.append(']');
			return sb.toString();
		}

	}

}
