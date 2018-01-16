/*
 * Copyright 2017-2018 the original author or authors.
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
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.core.VaultIntegrationTestConfiguration;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.repository.VaultRepositoryIntegrationTests.VaultRepositoryTestConfiguration;
import org.springframework.vault.repository.configuration.EnableVaultRepositories;
import org.springframework.vault.util.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.domain.Sort.Order.asc;

/**
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = VaultRepositoryTestConfiguration.class)
public class VaultRepositoryIntegrationTests extends IntegrationTestSupport {

	@Configuration
	@EnableVaultRepositories(considerNestedRepositories = true)
	static class VaultRepositoryTestConfiguration extends
			VaultIntegrationTestConfiguration {
	}

	@Autowired
	VaultRepository vaultRepository;

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
		assertThat(vaultRepository.findById("foo-key")).contains(person);
	}

	@Test
	public void shouldApplyQueryMethod() {

		Person walter = new Person();
		walter.setId("walter");
		walter.setName("Walter");

		vaultRepository.save(walter);

		Person skyler = new Person();
		skyler.setId("skyler");
		skyler.setName("Skyler");

		vaultRepository.save(skyler);

		Iterable<Person> all = vaultRepository.findByIdStartsWith("walt");

		assertThat(all).contains(walter).doesNotContain(skyler);
	}

	@Test
	public void shouldApplyQueryMethodWithSorting() {

		Person walter = new Person();
		walter.setId("walter");
		walter.setName("Walter");

		vaultRepository.save(walter);

		Person skyler = new Person();
		skyler.setId("skyler");
		skyler.setName("Skyler");

		vaultRepository.save(skyler);

		assertThat(vaultRepository.findAllByOrderByNameAsc()).containsSequence(skyler,
				walter);
		assertThat(vaultRepository.findAllByOrderByNameDesc()).containsSequence(walter,
				skyler);
	}

	@Test
	public void shouldApplyLimiting() {

		Person walter = new Person();
		walter.setId("walter");
		walter.setName("Walter");

		vaultRepository.save(walter);

		Person skyler = new Person();
		skyler.setId("skyler");
		skyler.setName("Skyler");

		vaultRepository.save(skyler);

		assertThat(vaultRepository.findTop1By(Sort.by(asc("name")))).containsOnly(skyler);
	}

	@Test(expected = InvalidDataAccessApiUsageException.class)
	public void shouldFailForNonIdCriteria() {
		vaultRepository.findInvalidByName("foo");
	}

	interface VaultRepository extends CrudRepository<Person, String> {

		List<Person> findByIdStartsWith(String prefix);

		List<Person> findAllByOrderByNameAsc();

		List<Person> findAllByOrderByNameDesc();

		List<Person> findTop1By(Sort sort);

		List<Person> findInvalidByName(String name);
	}

	@Data
	static class Person {

		String id, name;
	}
}
