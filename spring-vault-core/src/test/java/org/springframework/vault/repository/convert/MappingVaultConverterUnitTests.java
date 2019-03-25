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
package org.springframework.vault.repository.convert;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.junit.Before;
import org.junit.Test;

import org.springframework.core.convert.converter.Converter;
import org.springframework.vault.repository.mapping.VaultMappingContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MappingVaultConverter}.
 *
 * @author Mark Paluch
 */
public class MappingVaultConverterUnitTests {

	VaultMappingContext context = new VaultMappingContext();

	MappingVaultConverter converter = new MappingVaultConverter(context);

	@Before
	public void before() {

		VaultCustomConversions conversions = new VaultCustomConversions(Arrays.asList(
				DocumentToPersonConverter.INSTANCE, PersonToDocumentConverter.INSTANCE));
		converter.setCustomConversions(conversions);
		converter.afterPropertiesSet();
	}

	@Test
	public void shouldReadSimpleEntity() {

		SecretDocument document = new SecretDocument("heisenberg");
		document.put("username", "walter");
		document.put("password", "hb");

		SimpleEntity entity = converter.read(SimpleEntity.class, document);

		assertThat(entity.getId()).isEqualTo("heisenberg");
		assertThat(entity.getUsername()).isEqualTo("walter");
		assertThat(entity.getPassword()).isEqualTo("hb");
	}

	@Test
	public void shouldReadConvertedEntity() {

		SecretDocument document = new SecretDocument("heisenberg");
		document.put("the_name", "walter");

		Person entity = converter.read(Person.class, document);

		assertThat(entity.getName()).isEqualTo("walter");
	}

	@Test
	public void shouldReadSubtype() {

		SecretDocument document = new SecretDocument("heisenberg");
		document.put("_class", ExtendedEntity.class.getName());
		document.put("username", "walter");
		document.put("password", "hb");
		document.put("location", "Albuquerque");

		SimpleEntity entity = converter.read(SimpleEntity.class, document);

		assertThat(entity).isInstanceOf(ExtendedEntity.class);
		assertThat(entity.getId()).isEqualTo("heisenberg");
		assertThat(entity.getUsername()).isEqualTo("walter");
		assertThat(entity.getPassword()).isEqualTo("hb");
		assertThat(((ExtendedEntity) entity).getLocation()).isEqualTo("Albuquerque");
	}

	@Test
	public void shouldReadEntityWithEnum() {

		SecretDocument document = new SecretDocument();
		document.put("condition", "BAD");

		EntityWithEnum entity = converter.read(EntityWithEnum.class, document);

		assertThat(entity.getCondition()).isEqualTo(Condition.BAD);
	}

	@Test
	public void shouldReadSimpleEntityWithConstructorCreation() {

		SecretDocument document = new SecretDocument("heisenberg");
		document.put("username", "walter");
		document.put("password", "hb");

		ConstructorCreation entity = converter.read(ConstructorCreation.class, document);

		assertThat(entity.getId()).isEqualTo("heisenberg");
		assertThat(entity.getUsername()).isEqualTo("walter");
		assertThat(entity.getPassword()).isEqualTo("hb");
	}

	@Test
	public void shouldReadEntityWithList() {

		SecretDocument document = new SecretDocument(Collections.singletonMap(
				"usernames", Arrays.asList("walter", "heisenberg")));

		EntityWithListOfStrings entity = converter.read(EntityWithListOfStrings.class,
				document);

		assertThat(entity.getUsernames()).containsSequence("walter", "heisenberg");
	}

	@Test
	public void shouldReadEntityWithMap() {

		Map<String, Integer> keyVersions = new LinkedHashMap<>();
		keyVersions.put("foo", 1);
		keyVersions.put("bar", 2);

		SecretDocument document = new SecretDocument(Collections.singletonMap(
				"keyVersions", keyVersions));

		EntityWithMap entity = converter.read(EntityWithMap.class, document);

		assertThat(entity.getKeyVersions()).containsAllEntriesOf(keyVersions);
	}

	@Test
	public void shouldReadEntityWithNesting() {

		Map<String, String> walter = new LinkedHashMap<>();
		walter.put("username", "heisenberg");
		walter.put("password", "hb");

		SecretDocument document = new SecretDocument();
		document.put("nested", walter);

		EntityWithNestedType entity = converter
				.read(EntityWithNestedType.class, document);

		assertThat(entity.getNested()).isEqualTo(new NestedType("heisenberg", "hb"));
	}

	@Test
	public void shouldReadEntityWithListOfEntities() {

		Map<String, String> walter = new LinkedHashMap<>();
		walter.put("username", "heisenberg");
		walter.put("password", "hb");

		Map<String, String> skyler = new LinkedHashMap<>();
		skyler.put("username", "skyler");
		skyler.put("password", "marie");

		SecretDocument document = new SecretDocument();
		document.put("nested", Arrays.asList(walter, skyler));

		EntityWithListOfEntities entity = converter.read(EntityWithListOfEntities.class,
				document);

		assertThat(entity.getNested()).contains(new NestedType("heisenberg", "hb"),
				new NestedType("skyler", "marie"));
	}

	@Test
	public void shouldWriteSimpleEntity() {

		SimpleEntity entity = new SimpleEntity();
		entity.setId("heisenberg");
		entity.setUsername("walter");
		entity.setPassword("hb");

		SecretDocument expected = new SecretDocument("heisenberg");
		expected.put("username", "walter");
		expected.put("password", "hb");
		expected.put("_class", entity.getClass().getName());

		SecretDocument sink = new SecretDocument();

		converter.write(entity, sink);

		assertThat(sink).isEqualTo(expected);
	}

	@Test
	public void shouldWriteConvertedEntity() {

		SecretDocument expected = new SecretDocument();
		expected.put("the_name", "walter");

		SecretDocument sink = new SecretDocument();

		converter.write(new Person("walter"), sink);

		assertThat(sink).isEqualTo(expected);
	}

	@Test
	public void shouldWriteEntityWithEnum() {

		EntityWithEnum entity = new EntityWithEnum();
		entity.setCondition(Condition.BAD);

		SecretDocument sink = new SecretDocument();

		converter.write(entity, sink);

		assertThat(sink.getBody()).containsEntry("condition", "BAD");
	}

	@Test
	public void shouldWriteEntityWithList() {

		EntityWithListOfStrings entity = new EntityWithListOfStrings();
		entity.setUsernames(Arrays.asList("walter", "heisenberg"));

		SecretDocument sink = new SecretDocument();

		converter.write(entity, sink);

		assertThat(sink.getBody()).containsEntry("usernames",
				Arrays.asList("walter", "heisenberg"));
	}

	@Test
	public void shouldWriteEntityWithMap() {

		Map<String, Integer> keyVersions = new LinkedHashMap<>();
		keyVersions.put("foo", 1);
		keyVersions.put("bar", 2);

		EntityWithMap entity = new EntityWithMap();
		entity.setKeyVersions(keyVersions);

		SecretDocument sink = new SecretDocument();

		converter.write(entity, sink);

		assertThat(sink.getBody()).containsEntry("keyVersions", keyVersions);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void shouldWriteEntityWithListOfEntities() {

		EntityWithListOfEntities entity = new EntityWithListOfEntities();
		entity.setNested(Arrays.asList(new NestedType("heisenberg", "hb"),
				new NestedType("skyler", "marie")));

		Map<String, Object> walter = new LinkedHashMap<>();
		walter.put("username", "heisenberg");
		walter.put("password", "hb");

		Map<String, Object> skyler = new LinkedHashMap<>();
		skyler.put("username", "skyler");
		skyler.put("password", "marie");

		SecretDocument sink = new SecretDocument();

		converter.write(entity, sink);

		assertThat((List<Map<String, Object>>) sink.get("nested")).contains(walter,
				skyler);
	}

	@Data
	static class SimpleEntity {

		String id;
		String username;
		String password;
	}

	@Data
	static class ExtendedEntity extends SimpleEntity {

		String location;
	}

	@Data
	static class EntityWithNestedType {

		NestedType nested;
	}

	@Data
	static class EntityWithEnum {

		Condition condition;
	}

	@Data
	@RequiredArgsConstructor
	static class ConstructorCreation {

		final String id;
		final String username;
		String password;
	}

	@Data
	static class EntityWithListOfStrings {

		List<String> usernames;
	}

	@Data
	static class EntityWithListOfEntities {

		List<NestedType> nested;
	}

	@Data
	static class EntityWithMap {

		Map<String, Integer> keyVersions;
	}

	@Data
	@AllArgsConstructor
	static class NestedType {

		String username;
		String password;
	}

	enum Condition {
		GOOD, BAD
	}

	@Data
	static class Person {
		final String name;
	}

	enum DocumentToPersonConverter implements Converter<SecretDocument, Person> {

		INSTANCE;

		@Override
		public Person convert(SecretDocument secretDocument) {
			return new Person((String) secretDocument.get("the_name"));
		}
	}

	enum PersonToDocumentConverter implements Converter<Person, SecretDocument> {

		INSTANCE;

		@Override
		public SecretDocument convert(Person person) {

			SecretDocument document = new SecretDocument();
			document.put("the_name", person.getName());
			return document;
		}
	}
}
