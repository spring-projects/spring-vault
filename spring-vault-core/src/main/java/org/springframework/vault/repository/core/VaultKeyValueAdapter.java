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
package org.springframework.vault.repository.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.keyvalue.core.AbstractKeyValueAdapter;
import org.springframework.data.util.CloseableIterator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.core.util.KeyValueDelegate;
import org.springframework.vault.repository.convert.MappingVaultConverter;
import org.springframework.vault.repository.convert.SecretDocument;
import org.springframework.vault.repository.convert.VaultConverter;
import org.springframework.vault.repository.mapping.VaultMappingContext;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.Versioned;

/**
 * Vault-specific {@link org.springframework.data.keyvalue.core.KeyValueAdapter}.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class VaultKeyValueAdapter extends AbstractKeyValueAdapter {

	private final VaultOperations vaultOperations;

	private final VaultConverter vaultConverter;

	private final KeyValueDelegate keyValueDelegate;

	private final Map<String, VaultKeyValueKeyspaceAccessor> accessors = new ConcurrentHashMap<>();

	/**
	 * Create a new {@link VaultKeyValueAdapter} given {@link VaultOperations}.
	 * @param vaultOperations must not be {@literal null}.
	 */
	public VaultKeyValueAdapter(VaultOperations vaultOperations) {
		this(vaultOperations, new MappingVaultConverter(new VaultMappingContext()));
	}

	/**
	 * Create a new {@link VaultKeyValueAdapter} given {@link VaultOperations} and
	 * {@link VaultConverter}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param vaultConverter must not be {@literal null}.
	 */
	public VaultKeyValueAdapter(VaultOperations vaultOperations, VaultConverter vaultConverter) {

		super(new VaultQueryEngine());

		Assert.notNull(vaultOperations, "VaultOperations must not be null");
		Assert.notNull(vaultConverter, "VaultConverter must not be null");

		this.vaultOperations = vaultOperations;
		this.vaultConverter = vaultConverter;
		this.keyValueDelegate = new KeyValueDelegate(vaultOperations);
	}

	@Override
	public Object put(Object id, Object item, String keyspace) {

		SecretDocument secretDocument = new SecretDocument(id.toString());
		this.vaultConverter.write(item, secretDocument);

		SecretDocument saved = getAccessor(keyspace).put(secretDocument);

		return this.vaultConverter.read(item.getClass(), saved);
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

		VaultKeyValueKeyspaceAccessor accessor = getAccessor(keyspace);

		SecretDocument document = accessor.get(id.toString());

		if (document == null) {
			return null;
		}

		return this.vaultConverter.read(type, document);
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

		return deleteEntity(entity, keyspace);
	}

	public <T> T deleteEntity(T entity, String keyspace) {

		SecretDocument document = new SecretDocument();
		this.vaultConverter.write(entity, document);

		getAccessor(keyspace).delete(document);

		return entity;
	}

	@Override
	public Iterable<?> getAllOf(String keyspace) {

		List<String> list = doList(keyspace);
		List<Object> items = new ArrayList<>(list.size());

		for (String id : list) {

			Object object = get(id, keyspace);
			if (object != null) {
				items.add(object);
			}
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

		VaultKeyValueKeyspaceAccessor accessor = getAccessor(keyspace);
		for (String id : ids) {
			accessor.delete(id);
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

		VaultKeyValueKeyspaceAccessor accessor = getAccessor(keyspace);

		List<String> list = accessor.list(keyspace);

		return list == null ? Collections.emptyList() : list;
	}

	private VaultKeyValueKeyspaceAccessor getAccessor(String keyspace) {

		KeyValueDelegate.MountInfo mountInfo = keyValueDelegate.getMountInfo(keyspace);

		return accessors.computeIfAbsent(keyspace, it -> {

			if (keyValueDelegate.isVersioned(it)) {
				return new VaultKeyValue2KeyspaceAccessor(mountInfo, it,
						this.vaultOperations.opsForVersionedKeyValue(mountInfo.getPath()));
			}

			return new VaultKeyValue1KeyspaceAccessor(mountInfo, it, this.vaultOperations
					.opsForKeyValue(mountInfo.getPath(), VaultKeyValueOperationsSupport.KeyValueBackend.KV_1));
		});

	}

	static abstract class VaultKeyValueKeyspaceAccessor {

		private final KeyValueDelegate.MountInfo mountInfo;

		private final String keyspace;

		private final String pathPrefix;

		protected VaultKeyValueKeyspaceAccessor(KeyValueDelegate.MountInfo mountInfo, String keyspace) {
			this.mountInfo = mountInfo;
			this.keyspace = keyspace;
			this.pathPrefix = getPathInMount(keyspace);
		}

		@Nullable
		abstract List<String> list(String pattern);

		String getPathInMount(String keyspace) {

			if (!keyspace.startsWith(this.mountInfo.getPath())) {
				return keyspace;
			}

			return keyspace.substring(this.mountInfo.getPath().length());
		}

		String createPath(String id) {
			return this.pathPrefix + "/" + id;
		}

		@Nullable
		abstract SecretDocument get(String id);

		abstract SecretDocument put(SecretDocument secretDocument);

		abstract void delete(String id);

		abstract void delete(SecretDocument document);

	}

	static class VaultKeyValue1KeyspaceAccessor extends VaultKeyValueKeyspaceAccessor {

		private final VaultKeyValueOperations operations;

		public VaultKeyValue1KeyspaceAccessor(KeyValueDelegate.MountInfo mountInfo, String keyspace,
				VaultKeyValueOperations operations) {
			super(mountInfo, keyspace);
			this.operations = operations;
		}

		@Nullable
		@Override
		public List<String> list(String pattern) {
			return operations.list(getPathInMount(pattern));
		}

		@Nullable
		@Override
		SecretDocument get(String id) {

			VaultResponse vaultResponse = operations.get(createPath(id));

			if (vaultResponse == null) {
				return null;
			}

			return new SecretDocument(id, vaultResponse.getRequiredData());
		}

		@Override
		SecretDocument put(SecretDocument secretDocument) {

			operations.put(createPath(secretDocument.getRequiredId()), secretDocument.getBody());

			return secretDocument;
		}

		@Override
		void delete(String id) {
			operations.delete(createPath(id));
		}

		@Override
		void delete(SecretDocument document) {
			delete(document.getRequiredId());
		}

	}

	static class VaultKeyValue2KeyspaceAccessor extends VaultKeyValueKeyspaceAccessor {

		private final VaultVersionedKeyValueOperations operations;

		public VaultKeyValue2KeyspaceAccessor(KeyValueDelegate.MountInfo mountInfo, String keyspace,
				VaultVersionedKeyValueOperations operations) {
			super(mountInfo, keyspace);
			this.operations = operations;
		}

		@Nullable
		@Override
		public List<String> list(String pattern) {
			return operations.list(getPathInMount(pattern));
		}

		@Nullable
		@Override
		SecretDocument get(String id) {

			Versioned<Map<String, Object>> versioned = operations.get(createPath(id));

			if (versioned == null || !versioned.hasData()) {
				return null;
			}

			return new SecretDocument(id, versioned.getVersion()
					.getVersion(), versioned.getRequiredData());
		}

		@Override
		SecretDocument put(SecretDocument secretDocument) {

			try {
				Versioned.Metadata metadata;
				if (secretDocument.getVersion() != null) {
					metadata = operations.put(createPath(secretDocument.getRequiredId()), Versioned
							.create(secretDocument.getBody(), Versioned.Version.from(secretDocument.getVersion())));
				}
				else {
					metadata = operations.put(createPath(secretDocument.getRequiredId()), secretDocument.getBody());
				}

				return new SecretDocument(secretDocument.getRequiredId(), metadata.getVersion()
						.getVersion(),
						secretDocument.getBody());
			}
			catch (VaultException e) {
				if (e.getMessage() != null
						&& e.getMessage()
						.contains("check-and-set parameter did not match the current version")) {
					throw new OptimisticLockingFailureException(e.getMessage(), e);
				}

				throw e;
			}
		}

		@Override
		void delete(String id) {
			operations.delete(createPath(id));
		}

		@Override
		void delete(SecretDocument document) {

			if (document.getVersion() != null) {
				operations.delete(createPath(document.getRequiredId()), Versioned.Version.from(document.getVersion()));
			}
			else {
				delete(document.getRequiredId());
			}
		}

	}

}
