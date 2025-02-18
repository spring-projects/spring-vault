/*
 * Copyright 2017-2025 the original author or authors.
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.VaultException;
import org.springframework.vault.domain.Person;
import org.springframework.vault.support.ObjectMapperSupplier;
import org.springframework.vault.util.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ReactiveVaultTemplate} using the {@code generic} backend.
 *
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
class ReactiveVaultTemplateGenericIntegrationTests extends IntegrationTestSupport {

	ObjectMapper OBJECT_MAPPER = ObjectMapperSupplier.get();

	@Autowired
	ReactiveVaultOperations vaultOperations;

	@Test
	void readShouldReturnAbsentKey() {
		this.vaultOperations.read("secret/absent").as(StepVerifier::create).verifyComplete();
	}

	@Test
	void readShouldReturnExistingKey() {

		this.vaultOperations.write("secret/mykey", Collections.singletonMap("hello", "world"))
			.as(StepVerifier::create) //
			.verifyComplete();

		this.vaultOperations.read("secret/mykey")
			.as(StepVerifier::create)
			.consumeNextWith(actual -> assertThat(actual.getRequiredData()).containsEntry("hello", "world")) //
			.verifyComplete();
	}

	@Test
	void readShouldReturnNestedPropertiesKey() throws IOException {

		Map map = this.OBJECT_MAPPER
			.readValue("{ \"hello.array[0]\":\"array-value0\", \"hello.array[1]\":\"array-value1\" }", Map.class);

		this.vaultOperations.write("secret/mykey", map).as(StepVerifier::create).verifyComplete();

		this.vaultOperations.read("secret/mykey").as(StepVerifier::create).consumeNextWith(actual -> {
			assertThat(actual.getRequiredData()).containsEntry("hello.array[0]", "array-value0");
			assertThat(actual.getRequiredData()).containsEntry("hello.array[1]", "array-value1");
		}).verifyComplete();

	}

	@Test
	void readShouldReturnNestedObjects() throws IOException {

		Map map = this.OBJECT_MAPPER.readValue("{ \"array\": [ {\"hello\": \"world\"}, {\"hello1\": \"world1\"} ] }",
				Map.class);
		this.vaultOperations.write("secret/mykey", map).as(StepVerifier::create).verifyComplete();

		List<Map<String, String>> expected = Arrays.asList(Collections.singletonMap("hello", "world"),
				Collections.singletonMap("hello1", "world1"));

		this.vaultOperations.read("secret/mykey").as(StepVerifier::create).consumeNextWith(actual -> {
			assertThat(actual.getRequiredData()).containsEntry("array", expected);
		}).verifyComplete();
	}

	@Test
	void readObjectShouldReadDomainClass() {

		Map<String, String> data = new HashMap<>();
		data.put("firstname", "Walter");
		data.put("password", "Secret");

		this.vaultOperations.write("secret/mykey", data) //
			.as(StepVerifier::create) //
			.verifyComplete();

		this.vaultOperations.read("secret/mykey", Person.class) //
			.as(StepVerifier::create) //
			.consumeNextWith(actual -> {

				Person person = actual.getRequiredData();
				assertThat(person.getFirstname()).isEqualTo("Walter");
				assertThat(person.getPassword()).isEqualTo("Secret");

			})
			.verifyComplete();
	}

	@Test
	void listShouldNotReturnAbsentKey() {

		this.vaultOperations.list("foo")
			.collectList()
			.as(StepVerifier::create)
			.consumeNextWith(actual -> assertThat(actual).isEmpty())
			.verifyComplete();
	}

	@Test
	void listShouldReturnExistingKey() {

		this.vaultOperations.write("secret/mykey", Collections.singletonMap("hello", "world"))
			.as(StepVerifier::create) //
			.verifyComplete();

		this.vaultOperations.list("secret")
			.collectList()
			.as(StepVerifier::create)
			.consumeNextWith(actual -> assertThat(actual).contains("mykey"))
			.verifyComplete();
	}

	@Test
	void deleteShouldRemoveKey() {

		this.vaultOperations.write("secret/mykey", Collections.singletonMap("hello", "world"))
			.as(StepVerifier::create) //
			.verifyComplete();

		this.vaultOperations.delete("secret/mykey") //
			.as(StepVerifier::create) //
			.verifyComplete();

		this.vaultOperations.read("secret/mykey") //
			.as(StepVerifier::create) //
			.verifyComplete();
	}

	@Test
	void writeShouldReturnResponse() {

		this.vaultOperations.write("auth/token/create").as(StepVerifier::create).assertNext(response -> {

			assertThat(response.getAuth()).isNotNull();

		}).verifyComplete();
	}

	@Test
	void deleteUnknownPathShouldFail() {

		this.vaultOperations.delete("foobar").as(StepVerifier::create).verifyError(VaultException.class);
	}

	@Test
	void writeUnknownPathShouldFail() {

		this.vaultOperations.write("foobar").as(StepVerifier::create).verifyError(VaultException.class);
	}

}
