/*
 * Copyright 2017-2019 the original author or authors.
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
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.data.map.repository.config.EnableMapRepositories;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.core.VaultIntegrationTestConfiguration;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.domain.Person;
import org.springframework.vault.repository.MultipleSpringDataModulesIntegrationTests.MultipleModulesActiveTestConfiguration;
import org.springframework.vault.repository.configuration.EnableVaultRepositories;
import org.springframework.vault.util.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Vault repositories with multiple Spring Data modules.
 *
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MultipleModulesActiveTestConfiguration.class)
class MultipleSpringDataModulesIntegrationTests extends IntegrationTestSupport {

	@Configuration
	@EnableMapRepositories(considerNestedRepositories = true, //
	excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, value = VaultRepository.class))
	@EnableVaultRepositories(considerNestedRepositories = true, //
	excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, value = MapRepository.class))
	static class MultipleModulesActiveTestConfiguration extends
			VaultIntegrationTestConfiguration {
	}

	@Autowired
	VaultRepository vaultRepository;

	@Autowired
	MapRepository mapRepository;

	@Autowired
	VaultTemplate vaultTemplate;

	@BeforeEach
	void before() {
		vaultRepository.deleteAll();
	}

	@Test
	void loadAndSave() {

		Person person = new Person();
		person.setId("foo-key");
		person.setFirstname("bar");

		vaultRepository.save(person);

		Iterable<Person> all = vaultRepository.findAll();

		assertThat(all).contains(person);
		assertThat(vaultRepository.findByIdStartsWith("foo-key")).contains(person);
	}

	@Test
	void loadAndSaveMapRepository() {

		vaultRepository.findAll().forEach(vaultRepository::delete);

		Person person = new Person();
		person.setId("foo-key");
		person.setFirstname("bar");

		mapRepository.save(person);

		Iterable<Person> all = mapRepository.findAll();

		assertThat(all).contains(person);
		assertThat(mapRepository.findByFirstnameStartsWith("bar")).contains(person);
		assertThat(vaultRepository.findById("foo-key")).isEmpty();
	}

	interface VaultRepository extends CrudRepository<Person, String> {

		List<Person> findByIdStartsWith(String prefix);
	}

	interface MapRepository extends CrudRepository<Person, String> {

		List<Person> findByFirstnameStartsWith(String prefix);
	}
}
