/*
 * Copyright 2017-2018 the original author or authors.
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.util.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReactiveVaultTemplate} using the {@code generic} backend.
 *
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
public class ReactiveVaultTemplateGenericIntegrationTests extends IntegrationTestSupport {

	@Autowired
	private ReactiveVaultOperations vaultOperations;

	@Test
	public void readShouldReturnAbsentKey() {
		StepVerifier.create(vaultOperations.read("secret/absent")).verifyComplete();
	}

	@Test
	public void readShouldReturnExistingKey() {

		StepVerifier.create(
				vaultOperations.write("secret/mykey",
						Collections.singletonMap("hello", "world"))).verifyComplete();

		StepVerifier
				.create(vaultOperations.read("secret/mykey"))
				.consumeNextWith(
						actual -> assertThat(actual.getData()).containsEntry("hello",
								"world")).verifyComplete();

	}

	@Test
	public void readShouldReturnNestedPropertiesKey() throws IOException {

		Map map = new ObjectMapper()
				.readValue(
						"{ \"hello.array[0]\":\"array-value0\", \"hello.array[1]\":\"array-value1\" }",
						Map.class);

		StepVerifier.create(vaultOperations.write("secret/mykey", map)).verifyComplete();

		StepVerifier
				.create(vaultOperations.read("secret/mykey"))
				.consumeNextWith(
						actual -> {
							assertThat(actual.getData()).containsEntry("hello.array[0]",
									"array-value0");
							assertThat(actual.getData()).containsEntry("hello.array[1]",
									"array-value1");
						}).verifyComplete();

	}

	@Test
	public void readShouldReturnNestedObjects() throws IOException {

		Map map = new ObjectMapper().readValue(
				"{ \"array\": [ {\"hello\": \"world\"}, {\"hello1\": \"world1\"} ] }",
				Map.class);
		StepVerifier.create(vaultOperations.write("secret/mykey", map)).verifyComplete();

		List<Map<String, String>> expected = Arrays.asList(
				Collections.singletonMap("hello", "world"),
				Collections.singletonMap("hello1", "world1"));

		StepVerifier.create(vaultOperations.read("secret/mykey"))
				.consumeNextWith(actual -> {
					assertThat(actual.getData()).containsEntry("array", expected);
				}).verifyComplete();

	}

	@Test
	public void readObjectShouldReadDomainClass() {

		Map<String, String> data = new HashMap<>();
		data.put("firstname", "Walter");
		data.put("password", "Secret");

		StepVerifier.create(vaultOperations.write("secret/mykey", data)).verifyComplete();

		StepVerifier.create(vaultOperations.read("secret/mykey", Person.class))
				.consumeNextWith(actual -> {

					Person person = actual.getData();
					assertThat(person.getFirstname()).isEqualTo("Walter");
					assertThat(person.getPassword()).isEqualTo("Secret");

				}).verifyComplete();
	}

	@Test
	public void listShouldReturnExistingKey() {

		StepVerifier.create(
				vaultOperations.write("secret/mykey",
						Collections.singletonMap("hello", "world"))).verifyComplete();

		StepVerifier.create(vaultOperations.list("secret").collectList())
				.consumeNextWith(actual -> assertThat(actual).contains("mykey"))
				.verifyComplete();
	}

	@Test
	public void deleteShouldRemoveKey() {

		StepVerifier.create(
				vaultOperations.write("secret/mykey",
						Collections.singletonMap("hello", "world"))).verifyComplete();

		StepVerifier.create(vaultOperations.delete("secret/mykey")).verifyComplete();

		StepVerifier.create(vaultOperations.read("secret/mykey")).verifyComplete();
	}

	@Test
	public void writeShouldReturnResponse() {

		StepVerifier.create(vaultOperations.write("auth/token/create"))
				.assertNext(response -> {

					assertThat(response.getAuth()).isNotNull();

				}).verifyComplete();
	}

	static class Person {

		String firstname;
		String password;

		public String getFirstname() {
			return firstname;
		}

		public String getPassword() {
			return password;
		}
	}
}
