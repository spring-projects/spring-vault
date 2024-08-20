/*
 * Copyright 2017-2024 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.vault.repository.mapping.VaultMappingContext;

/**
 * Unit tests for {@link MappingVaultConverter}.
 *
 * @author Mark Paluch
 */
class MappingVaultConverterUnitTests {

	VaultMappingContext context = new VaultMappingContext();

	MappingVaultConverter converter = new MappingVaultConverter(this.context);

	@BeforeEach
	void before() {

		VaultCustomConversions conversions = new VaultCustomConversions(
				Arrays.asList(DocumentToPersonConverter.INSTANCE, PersonToDocumentConverter.INSTANCE));
		this.converter.setCustomConversions(conversions);
		this.converter.afterPropertiesSet();
	}

	@Test
	void shouldReadSimpleEntity() {

		SecretDocument document = new SecretDocument("heisenberg");
		document.put("username", "walter");
		document.put("password", "hb");

		SimpleEntity entity = this.converter.read(SimpleEntity.class, document);

		assertThat(entity.getId()).isEqualTo("heisenberg");
		assertThat(entity.getUsername()).isEqualTo("walter");
		assertThat(entity.getPassword()).isEqualTo("hb");
	}

	@Test
	void shouldReadConvertedEntity() {

		SecretDocument document = new SecretDocument("heisenberg");
		document.put("the_name", "walter");

		Person entity = this.converter.read(Person.class, document);

		assertThat(entity.getName()).isEqualTo("walter");
	}

	@Test
	void shouldReadSubtype() {

		SecretDocument document = new SecretDocument("heisenberg");
		document.put("_class", ExtendedEntity.class.getName());
		document.put("username", "walter");
		document.put("password", "hb");
		document.put("location", "Albuquerque");

		SimpleEntity entity = this.converter.read(SimpleEntity.class, document);

		assertThat(entity).isInstanceOf(ExtendedEntity.class);
		assertThat(entity.getId()).isEqualTo("heisenberg");
		assertThat(entity.getUsername()).isEqualTo("walter");
		assertThat(entity.getPassword()).isEqualTo("hb");
		assertThat(((ExtendedEntity) entity).getLocation()).isEqualTo("Albuquerque");
	}

	@Test
	void shouldReadEntityWithEnum() {

		SecretDocument document = new SecretDocument();
		document.put("condition", "BAD");

		EntityWithEnum entity = this.converter.read(EntityWithEnum.class, document);

		assertThat(entity.getCondition()).isEqualTo(Condition.BAD);
	}

	@Test
	void shouldReadSimpleEntityWithConstructorCreation() {

		SecretDocument document = new SecretDocument("heisenberg");
		document.put("username", "walter");
		document.put("password", "hb");

		ConstructorCreation entity = this.converter.read(ConstructorCreation.class, document);

		assertThat(entity.getId()).isEqualTo("heisenberg");
		assertThat(entity.getUsername()).isEqualTo("walter");
		assertThat(entity.getPassword()).isEqualTo("hb");
	}

	@Test
	void shouldReadEntityWithList() {

		SecretDocument document = new SecretDocument(
				Collections.singletonMap("usernames", Arrays.asList("walter", "heisenberg")));

		EntityWithListOfStrings entity = this.converter.read(EntityWithListOfStrings.class, document);

		assertThat(entity.getUsernames()).containsSequence("walter", "heisenberg");
	}

	@Test
	void shouldReadEntityWithMap() {

		Map<String, Integer> keyVersions = new LinkedHashMap<>();
		keyVersions.put("foo", 1);
		keyVersions.put("bar", 2);

		SecretDocument document = new SecretDocument(Collections.singletonMap("keyVersions", keyVersions));

		EntityWithMap entity = this.converter.read(EntityWithMap.class, document);

		assertThat(entity.getKeyVersions()).containsAllEntriesOf(keyVersions);
	}

	@Test
	void shouldReadEntityWithNesting() {

		Map<String, String> walter = new LinkedHashMap<>();
		walter.put("username", "heisenberg");
		walter.put("password", "hb");

		SecretDocument document = new SecretDocument();
		document.put("nested", walter);

		EntityWithNestedType entity = this.converter.read(EntityWithNestedType.class, document);

		assertThat(entity.getNested()).isEqualTo(new NestedType("heisenberg", "hb"));
	}

	@Test
	void shouldReadEntityWithListOfEntities() {

		Map<String, String> walter = new LinkedHashMap<>();
		walter.put("username", "heisenberg");
		walter.put("password", "hb");

		Map<String, String> skyler = new LinkedHashMap<>();
		skyler.put("username", "skyler");
		skyler.put("password", "marie");

		SecretDocument document = new SecretDocument();
		document.put("nested", Arrays.asList(walter, skyler));

		EntityWithListOfEntities entity = this.converter.read(EntityWithListOfEntities.class, document);

		assertThat(entity.getNested()).contains(new NestedType("heisenberg", "hb"), new NestedType("skyler", "marie"));
	}

	@Test
	void shouldWriteSimpleEntity() {

		SimpleEntity entity = new SimpleEntity();
		entity.setId("heisenberg");
		entity.setUsername("walter");
		entity.setPassword("hb");

		SecretDocument expected = new SecretDocument("heisenberg");
		expected.put("username", "walter");
		expected.put("password", "hb");
		expected.put("_class", entity.getClass().getName());

		SecretDocument sink = new SecretDocument();

		this.converter.write(entity, sink);

		assertThat(sink).isEqualTo(expected);
	}

	@Test
	void shouldWriteVersionedEntity() {

		VersionedEntity entity = new VersionedEntity();
		entity.setId("heisenberg");
		entity.setUsername("walter");
		entity.setVersion(0);

		SecretDocument expected = new SecretDocument("heisenberg");
		expected.put("username", "walter");
		expected.setVersion(0);
		expected.put("_class", entity.getClass().getName());

		SecretDocument sink = new SecretDocument();

		this.converter.write(entity, sink);

		assertThat(sink).isEqualTo(expected);
	}

	@Test
	void shouldReadVersionedEntity() {

		SecretDocument document = new SecretDocument("heisenberg");
		document.put("username", "walter");
		document.setVersion(11);

		VersionedEntity read = this.converter.read(VersionedEntity.class, document);

		assertThat(read.getId()).isEqualTo("heisenberg");
		assertThat(read.getUsername()).isEqualTo("walter");
		assertThat(read.getVersion()).isEqualTo(11);
	}

	@Test
	void shouldWriteConvertedEntity() {

		SecretDocument expected = new SecretDocument();
		expected.put("the_name", "walter");

		SecretDocument sink = new SecretDocument();

		this.converter.write(new Person("walter"), sink);

		assertThat(sink).isEqualTo(expected);
	}

	@Test
	void shouldWriteEntityWithEnum() {

		EntityWithEnum entity = new EntityWithEnum();
		entity.setCondition(Condition.BAD);

		SecretDocument sink = new SecretDocument();

		this.converter.write(entity, sink);

		assertThat(sink.getBody()).containsEntry("condition", "BAD");
	}

	@Test
	void shouldWriteEntityWithList() {

		EntityWithListOfStrings entity = new EntityWithListOfStrings();
		entity.setUsernames(Arrays.asList("walter", "heisenberg"));

		SecretDocument sink = new SecretDocument();

		this.converter.write(entity, sink);

		assertThat(sink.getBody()).containsEntry("usernames", Arrays.asList("walter", "heisenberg"));
	}

	@Test
	void shouldWriteEntityWithMap() {

		Map<String, Integer> keyVersions = new LinkedHashMap<>();
		keyVersions.put("foo", 1);
		keyVersions.put("bar", 2);

		EntityWithMap entity = new EntityWithMap();
		entity.setKeyVersions(keyVersions);

		SecretDocument sink = new SecretDocument();

		this.converter.write(entity, sink);

		assertThat(sink.getBody()).containsEntry("keyVersions", keyVersions);
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldWriteEntityWithListOfEntities() {

		EntityWithListOfEntities entity = new EntityWithListOfEntities();
		entity.setNested(Arrays.asList(new NestedType("heisenberg", "hb"), new NestedType("skyler", "marie")));

		Map<String, Object> walter = new LinkedHashMap<>();
		walter.put("username", "heisenberg");
		walter.put("password", "hb");

		Map<String, Object> skyler = new LinkedHashMap<>();
		skyler.put("username", "skyler");
		skyler.put("password", "marie");

		SecretDocument sink = new SecretDocument();

		this.converter.write(entity, sink);

		assertThat((List<Map<String, Object>>) sink.get("nested")).contains(walter, skyler);
	}

	@Test
	void shouldConvertIdentifier() {

		WithUuidId entity = new WithUuidId(UUID.randomUUID(), "foo");

		SecretDocument sink = new SecretDocument();

		this.converter.write(entity, sink);

		assertThat(sink.getId()).isEqualTo(entity.id.toString());
		assertThat(sink.getBody()).containsEntry("name", "foo");
	}

	static class SimpleEntity {

		String id;

		String username;

		String password;

		public String getId() {
			return this.id;
		}

		public String getUsername() {
			return this.username;
		}

		public String getPassword() {
			return this.password;
		}

		public void setId(String id) {
			this.id = id;
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public void setPassword(String password) {
			this.password = password;
		}

	}

	static class VersionedEntity {

		String id;

		String username;

		@Version
		long version;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getUsername() {
			return username;
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public long getVersion() {
			return version;
		}

		public void setVersion(long version) {
			this.version = version;
		}

	}

	static class ExtendedEntity extends SimpleEntity {

		String location;

		public String getLocation() {
			return this.location;
		}

		public void setLocation(String location) {
			this.location = location;
		}

	}

	static class EntityWithNestedType {

		NestedType nested;

		public NestedType getNested() {
			return this.nested;
		}

		public void setNested(NestedType nested) {
			this.nested = nested;
		}

	}

	static class EntityWithEnum {

		Condition condition;

		public Condition getCondition() {
			return this.condition;
		}

		public void setCondition(Condition condition) {
			this.condition = condition;
		}

	}

	static class ConstructorCreation {

		final String id;

		final String username;

		String password;

		public ConstructorCreation(String id, String username) {
			this.id = id;
			this.username = username;
		}

		public String getId() {
			return this.id;
		}

		public String getUsername() {
			return this.username;
		}

		public String getPassword() {
			return this.password;
		}

		public void setPassword(String password) {
			this.password = password;
		}

	}

	static class EntityWithListOfStrings {

		List<String> usernames;

		public List<String> getUsernames() {
			return this.usernames;
		}

		public void setUsernames(List<String> usernames) {
			this.usernames = usernames;
		}

	}

	static class EntityWithListOfEntities {

		List<NestedType> nested;

		public List<NestedType> getNested() {
			return this.nested;
		}

		public void setNested(List<NestedType> nested) {
			this.nested = nested;
		}

	}

	static class EntityWithMap {

		Map<String, Integer> keyVersions;

		public EntityWithMap() {
		}

		public Map<String, Integer> getKeyVersions() {
			return this.keyVersions;
		}

		public void setKeyVersions(Map<String, Integer> keyVersions) {
			this.keyVersions = keyVersions;
		}

	}

	static class NestedType {

		String username;

		String password;

		public NestedType(String username, String password) {
			this.username = username;
			this.password = password;
		}

		public String getUsername() {
			return this.username;
		}

		public String getPassword() {
			return this.password;
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public void setPassword(String password) {
			this.password = password;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof NestedType that))
				return false;
			return Objects.equals(this.username, that.username) && Objects.equals(this.password, that.password);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.username, this.password);
		}

	}

	enum Condition {

		GOOD, BAD

	}

	static class Person {

		final String name;

		public Person(String name) {
			this.name = name;
		}

		public String getName() {
			return this.name;
		}

	}

	static class WithUuidId {

		@Id
		private final UUID id;

		private final String name;

		public WithUuidId(UUID id, String name) {
			this.id = id;
			this.name = name;
		}

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
