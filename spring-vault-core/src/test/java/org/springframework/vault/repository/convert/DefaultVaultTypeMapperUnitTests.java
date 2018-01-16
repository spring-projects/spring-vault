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
package org.springframework.vault.repository.convert;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

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
public class DefaultVaultTypeMapperUnitTests {

	ConfigurableTypeInformationMapper configurableTypeInformationMapper = new ConfigurableTypeInformationMapper(
			Collections.singletonMap(String.class, "1"));
	SimpleTypeInformationMapper simpleTypeInformationMapper = new SimpleTypeInformationMapper();
	DefaultVaultTypeMapper typeMapper = new DefaultVaultTypeMapper();

	@Test
	public void defaultInstanceWritesClasses() {

		writesTypeToField(new LinkedHashMap<>(), String.class, String.class.getName());
	}

	@Test
	public void defaultInstanceReadsClasses() {

		Map<String, Object> document = new LinkedHashMap<>();
		document.put(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, String.class.getName());

		readsTypeFromField(document, String.class);
	}

	@Test
	public void writesMapKeyForType() {

		typeMapper = new DefaultVaultTypeMapper(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY,
				Collections.singletonList(configurableTypeInformationMapper));

		writesTypeToField(new LinkedHashMap<>(), String.class, "1");
		writesTypeToField(new LinkedHashMap<>(), Object.class, null);
	}

	@Test
	public void writesClassNamesForUnmappedValuesIfConfigured() {

		typeMapper = new DefaultVaultTypeMapper(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY,
				Arrays.asList(configurableTypeInformationMapper,
						simpleTypeInformationMapper));
		writesTypeToField(new LinkedHashMap<>(), String.class, "1");
		writesTypeToField(new LinkedHashMap<>(), Object.class, Object.class.getName());
	}

	@Test
	public void readsTypeForMapKey() {

		typeMapper = new DefaultVaultTypeMapper(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY,
				Collections.singletonList(configurableTypeInformationMapper));

		readsTypeFromField(
				Collections.singletonMap(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, "1"),
				String.class);
		readsTypeFromField(Collections.singletonMap(
				DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, "unmapped"), null);
	}

	@Test
	public void readsTypeLoadingClassesForUnmappedTypesIfConfigured() {

		typeMapper = new DefaultVaultTypeMapper(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY,
				Arrays.asList(configurableTypeInformationMapper,
						simpleTypeInformationMapper));

		readsTypeFromField(
				Collections.singletonMap(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, "1"),
				String.class);
		readsTypeFromField(Collections.singletonMap(
				DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, Object.class.getName()),
				Object.class);
	}

	@Test
	public void addsFullyQualifiedClassNameUnderDefaultKeyByDefault() {
		writesTypeToField(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, new LinkedHashMap<>(),
				String.class);
	}

	@Test
	public void writesTypeToCustomFieldIfConfigured() {

		typeMapper = new DefaultVaultTypeMapper("_custom");
		writesTypeToField("_custom", new LinkedHashMap<>(), String.class);
	}

	@Test
	public void doesNotWriteTypeInformationInCaseKeyIsSetToNull() {

		typeMapper = new DefaultVaultTypeMapper(null);
		writesTypeToField(null, new LinkedHashMap<>(), String.class);
	}

	@Test
	public void readsTypeFromDefaultKeyByDefault() {
		readsTypeFromField(Collections.singletonMap(
				DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, String.class.getName()),
				String.class);
	}

	@Test
	public void readsTypeFromCustomFieldConfigured() {

		typeMapper = new DefaultVaultTypeMapper("_custom");
		readsTypeFromField(Collections.singletonMap("_custom", String.class.getName()),
				String.class);
	}

	@Test
	public void returnsNullIfNoTypeInfoInDocument() {
		readsTypeFromField(new LinkedHashMap<>(), null);
		readsTypeFromField(
				Collections.singletonMap(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, ""),
				null);
	}

	@Test
	public void returnsNullIfClassCannotBeLoaded() {
		readsTypeFromField(Collections.singletonMap(
				DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, "fooBar"), null);
	}

	@Test
	public void returnsNullIfTypeKeySetToNull() {
		typeMapper = new DefaultVaultTypeMapper(null);
		readsTypeFromField(Collections.singletonMap(
				DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, String.class), null);
	}

	@Test
	public void returnsCorrectTypeKey() {

		assertThat(typeMapper.isTypeKey(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY))
				.isTrue();

		typeMapper = new DefaultVaultTypeMapper("_custom");
		assertThat(typeMapper.isTypeKey("_custom")).isTrue();
		assertThat(typeMapper.isTypeKey(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY))
				.isFalse();

		typeMapper = new DefaultVaultTypeMapper(null);
		assertThat(typeMapper.isTypeKey("_custom")).isFalse();
		assertThat(typeMapper.isTypeKey(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY))
				.isFalse();
	}

	private void readsTypeFromField(Map<String, Object> document, @Nullable Class<?> type) {

		TypeInformation<?> typeInfo = typeMapper.readType(document);

		if (type != null) {
			assertThat(typeInfo).isNotNull();
			assertThat(typeInfo.getType()).isAssignableFrom(type);
		}
		else {
			assertThat(typeInfo).isNull();
		}
	}

	private void writesTypeToField(@Nullable String field, Map<String, Object> document,
			Class<?> type) {

		typeMapper.writeType(type, document);

		if (field == null) {
			assertThat(document.keySet()).isEmpty();
		}
		else {
			assertThat(document).containsKey(field);
			assertThat(document).containsEntry(field, type.getName());
		}
	}

	private void writesTypeToField(Map<String, Object> document, Class<?> type,
			@Nullable Object value) {

		typeMapper.writeType(type, document);

		if (value == null) {
			assertThat(document.keySet()).isEmpty();
		}
		else {
			assertThat(document).containsKey(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY);
			assertThat(document).containsEntry(DefaultVaultTypeMapper.DEFAULT_TYPE_KEY,
					value);
		}
	}
}
