/*
 * Copyright 2017-2019 the original author or authors.
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
package org.springframework.vault.repository.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.springframework.data.keyvalue.core.AbstractKeyValueAdapter;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.util.CloseableIterator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.repository.convert.MappingVaultConverter;
import org.springframework.vault.repository.convert.SecretDocument;
import org.springframework.vault.repository.convert.VaultConverter;
import org.springframework.vault.repository.mapping.VaultMappingContext;
import org.springframework.vault.repository.mapping.VaultPersistentEntity;
import org.springframework.vault.repository.mapping.VaultPersistentProperty;
import org.springframework.vault.support.VaultResponse;

/**
 * Vault-specific {@link org.springframework.data.keyvalue.core.KeyValueAdapter}.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class VaultKeyValueAdapter extends AbstractKeyValueAdapter {

	private final VaultOperations vaultOperations;

	private final VaultConverter vaultConverter;

	/**
	 * Create a new {@link VaultKeyValueAdapter} given {@link VaultOperations}.
	 *
	 * @param vaultOperations must not be {@literal null}.
	 */
	public VaultKeyValueAdapter(VaultOperations vaultOperations) {
		this(vaultOperations, new MappingVaultConverter(new VaultMappingContext()));
	}

	/**
	 * Create a new {@link VaultKeyValueAdapter} given {@link VaultOperations} and
	 * {@link VaultConverter}.
	 *
	 * @param vaultOperations must not be {@literal null}.
	 * @param vaultConverter must not be {@literal null}.
	 */
	public VaultKeyValueAdapter(VaultOperations vaultOperations,
			VaultConverter vaultConverter) {

		super(new VaultQueryEngine());

		Assert.notNull(vaultOperations, "VaultOperations must not be null");
		Assert.notNull(vaultConverter, "VaultConverter must not be null");

		this.vaultOperations = vaultOperations;
		this.vaultConverter = vaultConverter;
	}

	@Override
	public Object put(Object id, Object item, String keyspace) {

		SecretDocument secretDocument = new SecretDocument(id.toString());
		vaultConverter.write(item, secretDocument);

		vaultOperations.write(createKey(id, keyspace), secretDocument.getBody());

		return secretDocument;
	}

	@Override
	public boolean contains(Object id, String keyspace) {
		return doList(keyspace).contains(id.toString());
	}

	@Nullable
	@Override
	public Object get(Object id, String keyspace) {
		return get(id, keyspace, Object.class);
	}

	@Nullable
	@Override
	public <T> T get(Object id, String keyspace, Class<T> type) {

		VaultResponse response = vaultOperations.read(createKey(id, keyspace));

		if (response == null) {
			return null;
		}

		SecretDocument document = SecretDocument.from(id.toString(), response);

		return vaultConverter.read(type, document);
	}

	@Nullable
	@Override
	public Object delete(Object id, String keyspace) {
		return delete(id, keyspace, Object.class);
	}

	@Nullable
	@Override
	public <T> T delete(Object id, String keyspace, Class<T> type) {

		T entity = get(id, keyspace, type);

		if (entity == null) {
			return null;
		}

		vaultOperations.delete(createKey(id, keyspace));

		return entity;
	}

	@Override
	public Iterable<?> getAllOf(String keyspace) {

		List<String> list = doList(keyspace);
		List<Object> items = new ArrayList<>(list.size());

		for (String id : list) {
			items.add(get(id, keyspace));
		}

		return items;
	}

	@Override
	public CloseableIterator<Entry<Object, Object>> entries(String keyspace) {

		List<String> list = doList(keyspace);
		Iterator<String> iterator = list.iterator();

		return new CloseableIterator<Entry<Object, Object>>() {
			@Override
			public void close() {

			}

			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override
			public Entry<Object, Object> next() {

				final String key = iterator.next();

				return new Entry<Object, Object>() {
					@Override
					public Object getKey() {
						return key;
					}

					@Nullable
					@Override
					public Object getValue() {
						return get(key, keyspace);
					}

					@Override
					public Object setValue(Object value) {
						throw new UnsupportedOperationException();
					}
				};
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public void deleteAllOf(String keyspace) {

		List<String> ids = doList(keyspace);

		for (String id : ids) {
			vaultOperations.delete(createKey(id, keyspace));
		}
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public long count(String keyspace) {

		List<String> list = doList(keyspace);

		return list.size();
	}

	@Override
	public void destroy() throws Exception {
	}

	List<String> doList(String keyspace) {

		List<String> list = vaultOperations.list(keyspace);

		return list == null ? Collections.emptyList() : list;
	}

	private String createKey(Object id, String keyspace) {
		return String.format("%s/%s", keyspace, id);
	}

	MappingContext<? extends VaultPersistentEntity<?>, VaultPersistentProperty> getMappingContext() {
		return vaultConverter.getMappingContext();
	}
}
