/*
 * Copyright 2017-2020 the original author or authors.
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.repository.mapping.VaultPersistentProperty;

/**
 * Wrapper value object for a {@link SecretDocument} to be able to access raw values by
 * {@link VaultPersistentProperty} references. The accessors will transparently resolve
 * nested document values that a {@link VaultPersistentProperty} might refer to through a
 * path expression in field names.
 *
 * @author Mark Paluch
 * @since 2.0
 */
class SecretDocumentAccessor {

	private final SecretDocument document;

	private final Map<String, Object> body;

	/**
	 * Creates a new {@link SecretDocumentAccessor} for the given {@link SecretDocument}.
	 *
	 * @param document must be a {@link SecretDocument} effectively, must not be
	 * {@literal null}.
	 */
	SecretDocumentAccessor(SecretDocument document) {

		Assert.notNull(document, "SecretDocument must not be null!");

		this.document = document;
		this.body = document.getBody();
	}

	/**
	 * Creates a new {@link SecretDocumentAccessor} for the given {@link SecretDocument}
	 * and {@link Map body}.
	 *
	 * @param document must be a {@link SecretDocument} effectively, must not be
	 * {@literal null}
	 * @param body must not be {@literal null}.
	 */
	private SecretDocumentAccessor(SecretDocument document, Map<String, Object> body) {

		Assert.notNull(document, "SecretDocument must not be null!");
		Assert.notNull(body, "Body must not be null!");

		this.document = document;
		this.body = body;
	}

	/**
	 * Puts the given value into the backing {@link SecretDocument} based on the
	 * coordinates defined through the given {@link VaultPersistentProperty}. By default
	 * this will be the plain field name. But field names might also consist of path
	 * traversals so we might need to create intermediate {@link Map}s.
	 *
	 * @param prop must not be {@literal null}.
	 * @param value
	 */
	void put(VaultPersistentProperty prop, @Nullable Object value) {

		Assert.notNull(prop, "VaultPersistentProperty must not be null!");
		String fieldName = prop.getName();

		if (prop.isIdProperty()) {
			this.document.setId((String) value);
			return;
		}

		if (!fieldName.contains(".")) {
			this.body.put(fieldName, value);
			return;
		}

		Iterator<String> parts = Arrays.asList(fieldName.split("\\.")).iterator();
		Map<String, Object> document = this.body;

		while (parts.hasNext()) {

			String part = parts.next();

			if (parts.hasNext()) {
				document = getOrCreateNestedDocument(part, document);
			}
			else {
				document.put(fieldName, value);
			}
		}
	}

	/**
	 * Returns the value the given {@link VaultPersistentProperty} refers to. By default
	 * this will be a direct field but the method will also transparently resolve nested
	 * values the {@link VaultPersistentProperty} might refer to through a path expression
	 * in the field name metadata.
	 *
	 * @param property must not be {@literal null}.
	 * @return
	 */
	@Nullable
	Object get(VaultPersistentProperty property) {

		String fieldName = property.getName();

		if (property.isIdProperty()) {
			return this.document.getId();
		}

		if (!fieldName.contains(".")) {
			return this.body.get(fieldName);
		}

		Iterator<String> parts = Arrays.asList(fieldName.split("\\.")).iterator();
		Map<String, Object> source = this.body;
		Object result = null;

		while (source != null && parts.hasNext()) {

			result = source.get(parts.next());

			if (parts.hasNext()) {
				source = getAsMap(result);
			}
		}

		return result;
	}

	/**
	 * Returns whether the underlying {@link SecretDocument} has a value ({@literal null}
	 * or non-{@literal null}) for the given {@link VaultPersistentProperty}.
	 *
	 * @param property must not be {@literal null}.
	 * @return
	 */
	boolean hasValue(VaultPersistentProperty property) {

		Assert.notNull(property, "Property must not be null!");

		if (property.isIdProperty()) {
			return StringUtils.hasText(this.document.getId());
		}

		String fieldName = property.getName();

		if (!fieldName.contains(".")) {
			return this.body.containsKey(fieldName);
		}

		String[] parts = fieldName.split("\\.");
		Map<String, Object> source = this.body;

		Object result = null;

		for (int i = 1; i < parts.length; i++) {

			result = source.get(parts[i - 1]);
			source = getAsMap(result);

			if (source == null) {
				return false;
			}
		}

		return source.containsKey(parts[parts.length - 1]);
	}

	/**
	 * Returns the given source object as map, i.e. maps as is or {@literal null}
	 * otherwise.
	 *
	 * @param source can be {@literal null}.
	 * @return
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	private static Map<String, Object> getAsMap(Object source) {

		if (source instanceof Map) {
			return (Map<String, Object>) source;
		}

		return null;
	}

	/**
	 * Returns the {@link Map} which either already exists in the given source under the
	 * given key, or creates a new nested one, registers it with the source and returns
	 * it.
	 *
	 * @param key must not be {@literal null} or empty.
	 * @param source must not be {@literal null}.
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> getOrCreateNestedDocument(String key,
			Map<String, Object> source) {

		Object existing = source.get(key);

		if (existing instanceof Map) {
			return (Map<String, Object>) existing;
		}

		Map<String, Object> nested = new LinkedHashMap<>();
		source.put(key, nested);

		return nested;
	}

	public Map<String, Object> getBody() {
		return body;
	}

	public void setId(String id) {
		this.document.setId(id);
	}

	/**
	 * Obtains a nested {@link SecretDocumentAccessor} for a
	 * {@link VaultPersistentProperty}. Nested accessors allows mapping of structured
	 * hierarchies and represent accessors to nested maps.
	 *
	 * @param property must not be {@literal null}.
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public SecretDocumentAccessor writeNested(VaultPersistentProperty property) {

		Map<String, Object> body = (Map) get(property);

		if (body == null) {
			body = new LinkedHashMap<>();
			put(property, body);
		}

		return new SecretDocumentAccessor(document, body);
	}
}
