/*
 * Copyright 2017-2022 the original author or authors.
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
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.data.convert.ConfigurableTypeInformationMapper;
import org.springframework.data.convert.SimpleTypeInformationMapper;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultVaultTypeMapper}.
 *
 * @author Mark Paluch
 */
class DefaultVaultTypeMapperUnitTests {

	ConfigurableTypeInformationMapper configurableTypeInformationMapper = new ConfigurableTypeInformationMapper(
			Collections.singletonMap(String.class, "1"));

	SimpleTypeInformationMapper simpleTypeInformationMapper = new SimpleTypeInformationMapper();

	DefaultVaultTypeMapper typeMapper = new DefaultVaultTypeMapper();

	@Test
	void defaultInstanceWritesClasses() {

		writesTypeToField(new LinkedHashMap<>(), String.class, String.class.getName());
	}

	@Test
	void defaultInstanceReadsClasses() {

		Map<String, Object> document = new LinkedHashMap<>();
		document.put(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, String.class.getName());

		readsTypeFromField(document, String.class);
	}

	@Test
	void writesMapKeyForType() {

		this.typeMapper = new DefaultVaultTypeMapper(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY,
				Collections.singletonList(this.configurableTypeInformationMapper));

		writesTypeToField(new LinkedHashMap<>(), String.class, "1");
		writesTypeToField(new LinkedHashMap<>(), Object.class, null);
	}

	@Test
	void writesClassNamesForUnmappedValuesIfConfigured() {

		this.typeMapper = new DefaultVaultTypeMapper(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY,
				Arrays.asList(this.configurableTypeInformationMapper, this.simpleTypeInformationMapper));
		writesTypeToField(new LinkedHashMap<>(), String.class, "1");
		writesTypeToField(new LinkedHashMap<>(), Object.class, Object.class.getName());
	}

	@Test
	void readsTypeForMapKey() {

		this.typeMapper = new DefaultVaultTypeMapper(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY,
				Collections.singletonList(this.configurableTypeInformationMapper));

		readsTypeFromField(Collections.singletonMap(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, "1"), String.class);
		readsTypeFromField(Collections.singletonMap(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, "unmapped"), null);
	}

	@Test
	void readsTypeLoadingClassesForUnmappedTypesIfConfigured() {

		this.typeMapper = new DefaultVaultTypeMapper(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY,
				Arrays.asList(this.configurableTypeInformationMapper, this.simpleTypeInformationMapper));

		readsTypeFromField(Collections.singletonMap(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, "1"), String.class);
		readsTypeFromField(Collections.singletonMap(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, Object.class.getName()),
				Object.class);
	}

	@Test
	void addsFullyQualifiedClassNameUnderDefaultKeyByDefault() {
		writesTypeToField(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, new LinkedHashMap<>(), String.class);
	}

	@Test
	void writesTypeToCustomFieldIfConfigured() {

		this.typeMapper = new DefaultVaultTypeMapper("_custom");
		writesTypeToField("_custom", new LinkedHashMap<>(), String.class);
	}

	@Test
	void doesNotWriteTypeInformationInCaseKeyIsSetToNull() {

		this.typeMapper = new DefaultVaultTypeMapper(null);
		writesTypeToField(null, new LinkedHashMap<>(), String.class);
	}

	@Test
	void readsTypeFromDefaultKeyByDefault() {
		readsTypeFromField(Collections.singletonMap(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, String.class.getName()),
				String.class);
	}

	@Test
	void readsTypeFromCustomFieldConfigured() {

		this.typeMapper = new DefaultVaultTypeMapper("_custom");
		readsTypeFromField(Collections.singletonMap("_custom", String.class.getName()), String.class);
	}

	@Test
	void returnsNullIfNoTypeInfoInDocument() {
		readsTypeFromField(new LinkedHashMap<>(), null);
		readsTypeFromField(Collections.singletonMap(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, ""), null);
	}

	@Test
	void returnsNullIfClassCannotBeLoaded() {
		readsTypeFromField(Collections.singletonMap(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, "fooBar"), null);
	}

	@Test
	void returnsNullIfTypeKeySetToNull() {
		this.typeMapper = new DefaultVaultTypeMapper(null);
		readsTypeFromField(Collections.singletonMap(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, String.class), null);
	}

	@Test
	void returnsCorrectTypeKey() {

		assertThat(this.typeMapper.isTypeKey(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY)).isTrue();

		this.typeMapper = new DefaultVaultTypeMapper("_custom");
		assertThat(this.typeMapper.isTypeKey("_custom")).isTrue();
		assertThat(this.typeMapper.isTypeKey(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY)).isFalse();

		this.typeMapper = new DefaultVaultTypeMapper(null);
		assertThat(this.typeMapper.isTypeKey("_custom")).isFalse();
		assertThat(this.typeMapper.isTypeKey(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY)).isFalse();
	}

	private void readsTypeFromField(Map<String, Object> document, @Nullable Class<?> type) {

		TypeInformation<?> typeInfo = this.typeMapper.readType(document);

		if (type != null) {
			assertThat(typeInfo).isNotNull();
			assertThat(typeInfo.getType()).isAssignableFrom(type);
		}
		else {
			assertThat(typeInfo).isNull();
		}
	}

	private void writesTypeToField(@Nullable String field, Map<String, Object> document, Class<?> type) {

		this.typeMapper.writeType(type, document);

		if (field == null) {
			assertThat(document.keySet()).isEmpty();
		}
		else {
			assertThat(document).containsKey(field);
			assertThat(document).containsEntry(field, type.getName());
		}
	}

	private void writesTypeToField(Map<String, Object> document, Class<?> type, @Nullable Object value) {

		this.typeMapper.writeType(type, document);

		if (value == null) {
			assertThat(document.keySet()).isEmpty();
		}
		else {
			assertThat(document).containsKey(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY);
			assertThat(document).containsEntry(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, value);
		}
	}

}
