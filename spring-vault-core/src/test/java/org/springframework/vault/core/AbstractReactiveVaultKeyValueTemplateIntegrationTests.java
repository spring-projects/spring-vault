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

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.domain.Person;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.RequiresVaultVersion;
import org.springframework.vault.util.VaultInitializer;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ReactiveVaultKeyValueOperations}.
 *
 * @author Timothy R. Weiand
 */
@RequiresVaultVersion(VaultInitializer.VERSIONING_INTRODUCED_WITH_VALUE)
abstract class AbstractReactiveVaultKeyValueTemplateIntegrationTests extends IntegrationTestSupport {

	private final String path;

	private final KeyValueBackend apiVersion;

	@Autowired
	ReactiveVaultOperations vaultOperations;

	ReactiveVaultKeyValueOperations kvOperations;

	AbstractReactiveVaultKeyValueTemplateIntegrationTests(String path, KeyValueBackend apiVersion) {
		this.path = path;
		this.apiVersion = apiVersion;
	}

	@BeforeEach
	void before() {
		kvOperations = vaultOperations.opsForKeyValue(path, apiVersion);
	}

	@Test
	void shouldReportExpectedApiVersion() {
		assertThat(kvOperations.getApiVersion()).isEqualTo(apiVersion);
	}

	@Test
	void shouldCreateSecret() {

		Map<String, String> secret = Collections.singletonMap("key", "value");

		String key = UUID.randomUUID().toString();

		kvOperations.put(key, secret).as(StepVerifier::create).verifyComplete();

		kvOperations.list("/")
			.collectList()
			.as(StepVerifier::create)
			.assertNext(elements -> assertThat(elements).contains(key))
			.verifyComplete();
	}

	@Test
	void shouldReadSecret() {

		Map<String, String> secret = Collections.singletonMap("key", "value");

		String key = UUID.randomUUID().toString();

		kvOperations.put(key, secret).as(StepVerifier::create).verifyComplete();

		kvOperations.get(key)
			.map(VaultResponseSupport::getRequiredData)
			.as(StepVerifier::create)
			.assertNext(n -> assertThat(n).containsEntry("key", "value"))
			.verifyComplete();
	}

	@Test
	void shouldReadAbsentSecret() {

		kvOperations.get("absent").as(StepVerifier::create).verifyComplete();

		kvOperations.get("absent", Person.class).as(StepVerifier::create).verifyComplete();
	}

	@Test
	void shouldReadComplexSecret() {

		var person = new Person();
		person.setFirstname("Walter");
		person.setLastname("Heisenberg");
		person.setPassword("some-password");

		kvOperations.put("my-secret", person).as(StepVerifier::create).verifyComplete();

		kvOperations.get("my-secret")
			.map(VaultResponseSupport::getRequiredData)
			.as(StepVerifier::create)
			.assertNext(m -> {
				assertThat(m).containsAllEntriesOf(
						Map.of("firstname", "Walter", "lastname", "Heisenberg", "password", "some-password"));
				assertThat(m).containsEntry("id", null);
			})
			.verifyComplete();

		kvOperations.get("my-secret", Person.class)
			.map(VaultResponseSupport::getRequiredData)
			.as(StepVerifier::create)
			.assertNext(p -> assertThat(p).isEqualTo(person))
			.verifyComplete();
	}

	@Test
	void shouldDeleteSecret() {

		Map<String, String> secret = Collections.singletonMap("key", "value");

		String key = UUID.randomUUID().toString();

		kvOperations.put(key, secret).as(StepVerifier::create).verifyComplete();

		kvOperations.delete(key).as(StepVerifier::create).verifyComplete();

		kvOperations.get(key).as(StepVerifier::create).verifyComplete();
	}

}
