/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.vault.repository;

import java.util.List;

import lombok.Data;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.data.annotation.Id;
import org.springframework.data.map.repository.config.EnableMapRepositories;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.core.VaultIntegrationTestConfiguration;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.repository.MultipleSpringDataModulesIntegrationTests.MultipleModulesActiveTestConfiguration;
import org.springframework.vault.repository.configuration.EnableVaultRepositories;
import org.springframework.vault.util.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = MultipleModulesActiveTestConfiguration.class)
public class MultipleSpringDataModulesIntegrationTests extends IntegrationTestSupport {

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

	@Before
	public void before() {
		vaultRepository.deleteAll();
	}

	@Test
	public void loadAndSave() {

		Person person = new Person();
		person.setId("foo-key");
		person.setName("bar");

		vaultRepository.save(person);

		Iterable<Person> all = vaultRepository.findAll();

		assertThat(all).contains(person);
		assertThat(vaultRepository.findByIdStartsWith("foo-key")).contains(person);
	}

	@Test
	public void loadAndSaveMapRepository() {

		vaultRepository.findAll().forEach(vaultRepository::delete);

		Person person = new Person();
		person.setId("foo-key");
		person.setName("bar");

		mapRepository.save(person);

		Iterable<Person> all = mapRepository.findAll();

		assertThat(all).contains(person);
		assertThat(mapRepository.findByNameStartsWith("bar")).contains(person);
		assertThat(vaultRepository.findById("foo-key")).isEmpty();
	}

	interface VaultRepository extends CrudRepository<Person, String> {

		List<Person> findByIdStartsWith(String prefix);
	}

	interface MapRepository extends CrudRepository<Person, String> {

		List<Person> findByNameStartsWith(String prefix);
	}

	@Data
	static class Person {

		@Id
		String id;
		String name;
	}
}
