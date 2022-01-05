/*
 * Copyright 2016-2022 the original author or authors.
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.domain.Person;
import org.springframework.vault.support.ObjectMapperSupplier;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.util.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link VaultTemplate} using the {@code generic} backend.
 *
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
class VaultTemplateGenericIntegrationTests extends IntegrationTestSupport {

	ObjectMapper OBJECT_MAPPER = ObjectMapperSupplier.get();

	@Autowired
	VaultOperations vaultOperations;

	@Test
	void readShouldReturnAbsentKey() {

		VaultResponse read = this.vaultOperations.read("secret/absent");

		assertThat(read).isNull();
	}

	@Test
	void readShouldReturnExistingKey() {

		this.vaultOperations.write("secret/mykey", Collections.singletonMap("hello", "world"));

		VaultResponse read = this.vaultOperations.read("secret/mykey");
		assertThat(read).isNotNull();
		assertThat(read.getRequiredData()).containsEntry("hello", "world");
	}

	@Test
	void readShouldReturnNestedPropertiesKey() throws Exception {

		Map map = this.OBJECT_MAPPER
				.readValue("{ \"hello.array[0]\":\"array-value0\", \"hello.array[1]\":\"array-value1\" }", Map.class);
		this.vaultOperations.write("secret/mykey", map);

		VaultResponse read = this.vaultOperations.read("secret/mykey");
		assertThat(read).isNotNull();
		assertThat(read.getRequiredData()).containsEntry("hello.array[0]", "array-value0");
		assertThat(read.getRequiredData()).containsEntry("hello.array[1]", "array-value1");
	}

	@Test
	void readShouldReturnNestedObjects() throws Exception {

		Map map = this.OBJECT_MAPPER.readValue("{ \"array\": [ {\"hello\": \"world\"}, {\"hello1\": \"world1\"} ] }",
				Map.class);
		this.vaultOperations.write("secret/mykey", map);

		VaultResponse read = this.vaultOperations.read("secret/mykey");
		assertThat(read).isNotNull();
		assertThat(read.getRequiredData()).containsEntry("array", Arrays
				.asList(Collections.singletonMap("hello", "world"), Collections.singletonMap("hello1", "world1")));
	}

	@Test
	void readObjectShouldReadDomainClass() {

		Map<String, String> data = new HashMap<String, String>();
		data.put("firstname", "Walter");
		data.put("password", "Secret");

		this.vaultOperations.write("secret/mykey", data);

		VaultResponseSupport<Person> read = this.vaultOperations.read("secret/mykey", Person.class);
		assertThat(read).isNotNull();

		Person person = read.getRequiredData();
		assertThat(person.getFirstname()).isEqualTo("Walter");
		assertThat(person.getPassword()).isEqualTo("Secret");
	}

	@Test
	void listShouldReturnExistingKey() {

		this.vaultOperations.write("secret/mykey", Collections.singletonMap("hello", "world"));

		List<String> keys = this.vaultOperations.list("secret");
		assertThat(keys).contains("mykey");
	}

	@Test
	void listShouldNotReturnAbsentKey() {

		List<String> keys = this.vaultOperations.list("foo");
		assertThat(keys).isEmpty();
	}

	@Test
	void deleteShouldRemoveKey() {

		this.vaultOperations.write("secret/mykey", Collections.singletonMap("hello", "world"));

		this.vaultOperations.delete("secret/mykey");

		VaultResponse read = this.vaultOperations.read("secret/mykey");
		assertThat(read).isNull();
	}

}
