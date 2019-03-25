/*
 * Copyright 2018-2019 the original author or authors.
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

import lombok.Data;
import org.junit.Before;
import org.junit.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.VaultRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * Integration tests for {@link VaultKeyValue2Template}.
 *
 * @author Mark Paluch
 */
public abstract class AbstractVaultKeyValueTemplateIntegrationTests extends
		IntegrationTestSupport {

	private final String path;
	private final KeyValueBackend apiVersion;

	@Autowired
	VaultOperations vaultOperations;
	VaultKeyValueOperations kvOperations;

	AbstractVaultKeyValueTemplateIntegrationTests(String path, KeyValueBackend apiVersion) {
		this.path = path;
		this.apiVersion = apiVersion;
	}

	@Before
	public void before() {

		assumeTrue(prepare().getVersion().isGreaterThanOrEqualTo(
				VaultRule.VERSIONING_INTRODUCED_WITH));

		kvOperations = vaultOperations.opsForKeyValue(path, apiVersion);
	}

	@Test
	public void shouldReportExpectedApiVersion() {
		assertThat(kvOperations.getApiVersion()).isEqualTo(apiVersion);
	}

	@Test
	public void shouldCreateSecret() {

		Map<String, String> secret = Collections.singletonMap("key", "value");

		String key = UUID.randomUUID().toString();

		kvOperations.put(key, secret);

		assertThat(kvOperations.list("/")).contains(key);
	}

	@Test
	public void shouldReadSecret() {

		Map<String, String> secret = Collections.singletonMap("key", "value");

		String key = UUID.randomUUID().toString();

		kvOperations.put(key, secret);

		assertThat(kvOperations.get(key).getRequiredData()).containsEntry("key", "value");
	}

	@Test
	public void shouldReadAbsentSecret() {

		assertThat(kvOperations.get("absent")).isNull();
		assertThat(kvOperations.get("absent", Person.class)).isNull();
	}

	@Test
	public void shouldReadComplexSecret() {

		Person person = new Person();
		person.setFirstname("Walter");
		person.setLastname("Heisenberg");

		kvOperations.put("my-secret", person);

		assertThat(kvOperations.get("my-secret").getRequiredData()).containsEntry(
				"firstname", "Walter");
		assertThat(kvOperations.get("my-secret", Person.class).getRequiredData())
				.isEqualTo(person);
	}

	@Test
	public void shouldDeleteSecret() {

		Map<String, String> secret = Collections.singletonMap("key", "value");

		String key = UUID.randomUUID().toString();

		kvOperations.put(key, secret);
		kvOperations.delete(key);

		assertThat(kvOperations.get(key)).isNull();
	}

	@Data
	static class Person {

		String firstname;
		String lastname;
	}
}
