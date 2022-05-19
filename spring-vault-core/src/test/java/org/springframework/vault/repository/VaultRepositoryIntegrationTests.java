/*
 * Copyright 2017-2022 the original author or authors.
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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.core.VaultIntegrationTestConfiguration;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.domain.Person;
import org.springframework.vault.repository.VaultRepositoryIntegrationTests.VaultRepositoryTestConfiguration;
import org.springframework.vault.repository.configuration.EnableVaultRepositories;
import org.springframework.vault.util.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.data.domain.Sort.Order.*;

/**
 * Integration tests for Vault repositories.
 *
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultRepositoryTestConfiguration.class)
class VaultRepositoryIntegrationTests extends IntegrationTestSupport {

	@Configuration
	@EnableVaultRepositories(considerNestedRepositories = true,
			includeFilters = @ComponentScan.Filter(classes = VaultRepositoryIntegrationTests.VaultRepository.class,
					type = FilterType.ASSIGNABLE_TYPE))
	static class VaultRepositoryTestConfiguration extends VaultIntegrationTestConfiguration {

	}

	@Autowired
	VaultRepository vaultRepository;

	@Autowired
	VaultTemplate vaultTemplate;

	@BeforeEach
	void before() {
		this.vaultRepository.deleteAll();
	}

	@Test
	void loadAndSave() {

		Person person = new Person();
		person.setId("foo-key");
		person.setFirstname("bar");

		this.vaultRepository.save(person);

		Iterable<Person> all = this.vaultRepository.findAll();

		assertThat(all).contains(person);
		assertThat(this.vaultRepository.findById("foo-key")).contains(person);
	}

	@Test
	void shouldApplyQueryMethod() {

		Person walter = new Person();
		walter.setId("walter");
		walter.setFirstname("Walter");

		this.vaultRepository.save(walter);

		Person skyler = new Person();
		skyler.setId("skyler");
		skyler.setFirstname("Skyler");

		this.vaultRepository.save(skyler);

		Iterable<Person> all = this.vaultRepository.findByIdStartsWith("walt");

		assertThat(all).contains(walter).doesNotContain(skyler);
	}

	@Test
	void shouldApplyQueryMethodWithSorting() {

		Person walter = new Person();
		walter.setId("walter");
		walter.setFirstname("Walter");

		this.vaultRepository.save(walter);

		Person skyler = new Person();
		skyler.setId("skyler");
		skyler.setFirstname("Skyler");

		this.vaultRepository.save(skyler);

		assertThat(this.vaultRepository.findAllByOrderByFirstnameAsc()).containsSequence(skyler, walter);
		assertThat(this.vaultRepository.findAllByOrderByFirstnameDesc()).containsSequence(walter, skyler);
	}

	@Test
	void shouldApplyLimiting() {

		Person walter = new Person();
		walter.setId("walter");
		walter.setFirstname("Walter");

		this.vaultRepository.save(walter);

		Person skyler = new Person();
		skyler.setId("skyler");
		skyler.setFirstname("Skyler");

		this.vaultRepository.save(skyler);

		assertThat(this.vaultRepository.findTop1By(Sort.by(asc("firstname")))).containsOnly(skyler);
	}

	@Test
	void shouldFailForNonIdCriteria() {
		assertThatExceptionOfType(InvalidDataAccessApiUsageException.class)
				.isThrownBy(() -> this.vaultRepository.findInvalidByFirstname("foo"));
	}

	interface VaultRepository extends CrudRepository<Person, String> {

		List<Person> findByIdStartsWith(String prefix);

		List<Person> findAllByOrderByFirstnameAsc();

		List<Person> findAllByOrderByFirstnameDesc();

		List<Person> findTop1By(Sort sort);

		List<Person> findInvalidByFirstname(String name);

	}

}
