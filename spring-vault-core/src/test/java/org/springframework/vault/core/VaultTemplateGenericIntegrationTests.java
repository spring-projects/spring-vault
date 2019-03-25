/*
 * Copyright 2016 the original author or authors.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.util.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link VaultTemplate} using the {@code generic} backend.
 * 
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
public class VaultTemplateGenericIntegrationTests extends IntegrationTestSupport {

	@Autowired
	private VaultOperations vaultOperations;

	@Test
	public void readShouldReturnAbsentKey() throws Exception {

		VaultResponse read = vaultOperations.read("secret/absent");

		assertThat(read).isNull();
	}

	@Test
	public void readShouldReturnExistingKey() throws Exception {

		vaultOperations.write("secret/mykey", Collections.singletonMap("hello", "world"));

		VaultResponse read = vaultOperations.read("secret/mykey");
		assertThat(read).isNotNull();
		assertThat(read.getData()).containsEntry("hello", "world");
	}

	@Test
	public void readShouldReturnNestedPropertiesKey() throws Exception {

		Map map = new ObjectMapper()
				.readValue(
						"{ \"hello.array[0]\":\"array-value0\", \"hello.array[1]\":\"array-value1\" }",
						Map.class);
		vaultOperations.write("secret/mykey", map);

		VaultResponse read = vaultOperations.read("secret/mykey");
		assertThat(read).isNotNull();
		assertThat(read.getData()).containsEntry("hello.array[0]", "array-value0");
		assertThat(read.getData()).containsEntry("hello.array[1]", "array-value1");
	}

	@Test
	public void readShouldReturnNestedObjects() throws Exception {

		Map map = new ObjectMapper().readValue(
				"{ \"array\": [ {\"hello\": \"world\"}, {\"hello1\": \"world1\"} ] }",
				Map.class);
		vaultOperations.write("secret/mykey", map);

		VaultResponse read = vaultOperations.read("secret/mykey");
		assertThat(read).isNotNull();
		assertThat(read.getData()).containsEntry(
				"array",
				Arrays.asList(Collections.singletonMap("hello", "world"),
						Collections.singletonMap("hello1", "world1")));
	}

	@Test
	public void readObjectShouldReadDomainClass() throws Exception {

		Map<String, String> data = new HashMap<String, String>();
		data.put("firstname", "Walter");
		data.put("password", "Secret");

		vaultOperations.write("secret/mykey", data);

		VaultResponseSupport<Person> read = vaultOperations.read("secret/mykey",
				Person.class);
		assertThat(read).isNotNull();

		Person person = read.getData();
		assertThat(person.getFirstname()).isEqualTo("Walter");
		assertThat(person.getPassword()).isEqualTo("Secret");
	}

	@Test
	public void listShouldReturnExistingKey() throws Exception {

		vaultOperations.write("secret/mykey", Collections.singletonMap("hello", "world"));

		List<String> keys = vaultOperations.list("secret");
		assertThat(keys).contains("mykey");
	}

	@Test
	public void deleteShouldRemoveKey() throws Exception {

		vaultOperations.write("secret/mykey", Collections.singletonMap("hello", "world"));

		vaultOperations.delete("secret/mykey");

		VaultResponse read = vaultOperations.read("secret/mykey");
		assertThat(read).isNull();
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
