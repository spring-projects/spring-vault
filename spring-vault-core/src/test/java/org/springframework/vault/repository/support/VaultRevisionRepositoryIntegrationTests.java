/*
 * Copyright 2022-present the original author or authors.
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

package org.springframework.vault.repository.support;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.history.Revision;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultIntegrationTestConfiguration;
import org.springframework.vault.core.VaultSysOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.repository.configuration.EnableVaultRepositories;
import org.springframework.vault.repository.core.MappingVaultEntityInformation;
import org.springframework.vault.repository.core.VaultKeyValueTemplate;
import org.springframework.vault.repository.mapping.Secret;
import org.springframework.vault.repository.mapping.VaultPersistentEntity;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.util.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultRevisionRepositoryIntegrationTests.VaultRepositoryTestConfiguration.class)
class VaultRevisionRepositoryIntegrationTests extends IntegrationTestSupport {

	@Configuration
	@EnableVaultRepositories()
	static class VaultRepositoryTestConfiguration extends VaultIntegrationTestConfiguration {

	}

	@Autowired
	VaultTemplate vaultTemplate;

	@Autowired
	VaultKeyValueTemplate keyValueTemplate;

	@BeforeEach
	void before() {

		VaultSysOperations vaultSysOperations = this.vaultTemplate.opsForSys();

		try {
			vaultSysOperations.unmount("versioned");
		} catch (VaultException e) {
		}

		vaultSysOperations.mount("versioned",
				VaultMount.builder().type("kv").options(Collections.singletonMap("version", "2")).build());
	}

	@Test
	void shouldReportNoRevisions() {

		VaultRevisionRepository<VersionedPerson> repository = getRepository();

		assertThat(repository.findRevision("foo", 1)).isEmpty();
		assertThat(repository.findRevisions("foo")).isEmpty();
		assertThat(repository.findRevisions("foo", Pageable.ofSize(2).withPage(1))).isEmpty();
	}

	@Test
	void shouldFindRevisionMetadata() {

		VaultRevisionRepository<VersionedPerson> repository = getRepository();

		prepareVersions();

		assertThat(repository.findRevision("foo", 1)).get().satisfies(rev -> {
			assertThat(rev.getEntity().getPassword()).isEqualTo("password-v1");
			assertThat(rev.getRequiredRevisionInstant()).isNotNull();
			assertThat(rev.getRequiredRevisionNumber()).isEqualTo(1);
		});

		assertThat(repository.findRevision("foo", 5)).isEmpty();
	}

	@Test
	void shouldFindLatestRevision() {

		VaultRevisionRepository<VersionedPerson> repository = getRepository();

		prepareVersions();

		assertThat(repository.findLastChangeRevision("foo")).get().satisfies(rev -> {
			assertThat(rev.getEntity().getPassword()).isEqualTo("password-v4");
			assertThat(rev.getRequiredRevisionInstant()).isNotNull();
			assertThat(rev.getRequiredRevisionNumber()).isEqualTo(4);
		});

		assertThat(repository.findRevision("foo", 5)).isEmpty();
	}

	@Test
	void shouldFindRevisionMetadatas() {

		VaultRevisionRepository<VersionedPerson> repository = getRepository();

		prepareVersions();

		assertThat(repository.findRevisions("foo")).hasSize(4);
	}

	@Test
	void shouldFindPagedMetadatas() {

		VaultRevisionRepository<VersionedPerson> repository = getRepository();

		prepareVersions();

		Page<Revision<Integer, VersionedPerson>> page1 = repository.findRevisions("foo",
				Pageable.ofSize(3).withPage(0));

		Page<Revision<Integer, VersionedPerson>> page2 = repository.findRevisions("foo", page1.nextPageable());

		Page<Revision<Integer, VersionedPerson>> page3 = repository.findRevisions("foo", page2.nextPageable());

		assertThat(page1).hasSize(3);
		assertThat(page1.getTotalElements()).isEqualTo(4);

		assertThat(page2).hasSize(1);
		assertThat(page2.getTotalElements()).isEqualTo(4);

		assertThat(page3).isEmpty();
	}

	@Test
	void shouldFindOutOfBoundsPagedMetadatas() {

		VaultRevisionRepository<VersionedPerson> repository = getRepository();

		prepareVersions();

		assertThat(repository.findRevisions("foo", Pageable.ofSize(10).withPage(10))).isEmpty();

		assertThat(repository.findRevisions("foo", Pageable.ofSize(4).withPage(1))).isEmpty();
	}

	@Test
	void shouldFindDeletedRevisionMetadata() {

		VaultRevisionRepository<VersionedPerson> repository = getRepository();

		VersionedPerson v1 = new VersionedPerson("foo", 0, "password-v1");
		VersionedPerson v2 = new VersionedPerson("foo", 1, "password-v2");
		VersionedPerson v3 = new VersionedPerson("foo", 2, "password-v3");
		VersionedPerson v4 = new VersionedPerson("foo", 3, "password-v4");

		keyValueTemplate.insert(v1);
		keyValueTemplate.update(v2);
		keyValueTemplate.update(v3);
		keyValueTemplate.update(v4);

		keyValueTemplate.delete(v2);

		assertThat(repository.findRevision("foo", 1)).get().satisfies(rev -> {
			assertThat(rev.getEntity()).isNull();
			assertThat(rev.getRequiredRevisionInstant()).isNotNull();
			assertThat(rev.getRequiredRevisionNumber()).isEqualTo(1);
		});
	}

	@SuppressWarnings("rawtypes")
	private VaultRevisionRepository<VersionedPerson> getRepository() {

		VaultPersistentEntity<?> entity = keyValueTemplate.getConverter()
				.getMappingContext()
				.getRequiredPersistentEntity(VersionedPerson.class);

		return new VaultRevisionRepository<>(new MappingVaultEntityInformation(entity), "versioned/versionedPerson",
				keyValueTemplate);
	}

	private void prepareVersions() {

		VersionedPerson v1 = new VersionedPerson("foo", 0, "password-v1");
		VersionedPerson v2 = new VersionedPerson("foo", 1, "password-v2");
		VersionedPerson v3 = new VersionedPerson("foo", 2, "password-v3");
		VersionedPerson v4 = new VersionedPerson("foo", 3, "password-v4");

		keyValueTemplate.insert(v1);
		keyValueTemplate.update(v2);
		keyValueTemplate.update(v3);
		keyValueTemplate.update(v4);
	}

	@Secret(backend = "versioned")
	static class VersionedPerson {

		@Id
		String id;

		@Version
		int version;

		String password;

		public VersionedPerson() {
		}

		public VersionedPerson(String id, int version, String password) {
			this.id = id;
			this.version = version;
			this.password = password;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public int getVersion() {
			return version;
		}

		public void setVersion(int version) {
			this.version = version;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(String password) {
			this.password = password;
		}

	}

}
