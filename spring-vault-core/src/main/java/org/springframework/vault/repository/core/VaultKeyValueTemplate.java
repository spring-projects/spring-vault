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
package org.springframework.vault.repository.core;

import java.util.Collections;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.keyvalue.core.KeyValueAdapter;
import org.springframework.data.keyvalue.core.KeyValueTemplate;
import org.springframework.data.keyvalue.core.event.KeyValueEvent;
import org.springframework.data.keyvalue.core.mapping.KeyValuePersistentEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.repository.convert.VaultConverter;
import org.springframework.vault.repository.mapping.VaultMappingContext;

/**
 * Vault-specific {@link KeyValueTemplate}.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class VaultKeyValueTemplate extends KeyValueTemplate {

	@Nullable
	private ApplicationEventPublisher eventPublisher;

	private boolean publishEvents = true;

	@SuppressWarnings("rawtypes")
	private Set<Class<? extends KeyValueEvent>> eventTypesToPublish = Collections.emptySet();

	/**
	 * Create a new {@link VaultKeyValueTemplate} given {@link KeyValueAdapter} and
	 * {@link VaultMappingContext}.
	 * @param adapter must not be {@literal null}.
	 */
	public VaultKeyValueTemplate(VaultKeyValueAdapter adapter) {
		this(adapter, new VaultMappingContext());
	}

	/**
	 * Create a new {@link VaultKeyValueTemplate} given {@link KeyValueAdapter} and
	 * {@link VaultMappingContext}.
	 * @param adapter must not be {@literal null}.
	 * @param mappingContext must not be {@literal null}.
	 */
	public VaultKeyValueTemplate(VaultKeyValueAdapter adapter, VaultMappingContext mappingContext) {
		super(adapter, mappingContext);
	}

	/**
	 * Define the event types to publish via {@link ApplicationEventPublisher}.
	 * @param eventTypesToPublish use {@literal null} or {@link Collections#emptySet()} to
	 * stop publishing.
	 */
	public void setEventTypesToPublish(Set<Class<? extends KeyValueEvent>> eventTypesToPublish) {

		if (CollectionUtils.isEmpty(eventTypesToPublish)) {
			this.publishEvents = false;
		}
		else {
			this.publishEvents = true;
			this.eventTypesToPublish = Collections.unmodifiableSet(eventTypesToPublish);
		}
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.eventPublisher = applicationEventPublisher;
	}

	@Override
	public <T> T insert(Object id, T objectToInsert) {
		Assert.notNull(id, "Id for object to be inserted must not be null!");
		Assert.notNull(objectToInsert, "Object to be inserted must not be null!");

		String keyspace = resolveKeySpace(objectToInsert.getClass());

		potentiallyPublishEvent(KeyValueEvent.beforeInsert(id, keyspace, objectToInsert.getClass(), objectToInsert));

		T saved = execute(adapter -> {

			if (adapter.contains(id, keyspace)) {
				throw new DuplicateKeyException(
						"Cannot insert existing object with id %s!. Please use update.".formatted(id));
			}

			return (T) adapter.put(id, objectToInsert, keyspace);
		});

		potentiallyPublishEvent(KeyValueEvent.afterInsert(id, keyspace, objectToInsert.getClass(), objectToInsert));

		return saved;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T update(Object id, T objectToUpdate) {

		Assert.notNull(id, "Id for object to be inserted must not be null!");
		Assert.notNull(objectToUpdate, "Object to be updated must not be null!");

		String keyspace = resolveKeySpace(objectToUpdate.getClass());

		potentiallyPublishEvent(KeyValueEvent.beforeUpdate(id, keyspace, objectToUpdate.getClass(), objectToUpdate));

		T updated = execute(adapter -> (T) adapter.put(id, objectToUpdate, keyspace));

		potentiallyPublishEvent(
				KeyValueEvent.afterUpdate(id, keyspace, objectToUpdate.getClass(), objectToUpdate, updated));

		return updated;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T delete(T objectToDelete) {

		Class<T> type = (Class<T>) ClassUtils.getUserClass(objectToDelete);
		KeyValuePersistentEntity<?, ?> entity = getEntity(type);

		Object id = entity.getIdentifierAccessor(objectToDelete).getIdentifier();

		String keyspace = resolveKeySpace(type);

		potentiallyPublishEvent(KeyValueEvent.beforeDelete(id, keyspace, type));

		T result = execute(adapter -> ((VaultKeyValueAdapter) adapter).deleteEntity(objectToDelete, keyspace));

		potentiallyPublishEvent(KeyValueEvent.afterDelete(id, keyspace, type, result));

		return result;
	}

	@Override
	public void destroy() throws Exception {
		// no-op to prevent clear() call.
	}

	private String resolveKeySpace(Class<?> type) {

		KeyValuePersistentEntity<?, ?> entity = getEntity(type);
		return entity.getKeySpace();
	}

	@SuppressWarnings("rawtypes")
	private KeyValuePersistentEntity<?, ?> getEntity(Class<?> type) {
		return (KeyValuePersistentEntity) getMappingContext().getRequiredPersistentEntity(type);
	}

	@SuppressWarnings("rawtypes")
	private void potentiallyPublishEvent(KeyValueEvent event) {

		if (eventPublisher == null) {
			return;
		}

		if (publishEvents && (eventTypesToPublish.isEmpty() || eventTypesToPublish.contains(event.getClass()))) {
			eventPublisher.publishEvent(event);
		}
	}

	public VaultConverter getConverter() {
		return execute(adapter -> ((VaultKeyValueAdapter) adapter).getConverter());
	}

	public VaultOperations getVaultOperations() {
		return execute(adapter -> ((VaultKeyValueAdapter) adapter).getVaultOperations());
	}

}
