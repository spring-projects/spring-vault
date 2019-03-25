/*
 * Copyright 2017-2018 the original author or authors.
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
package org.springframework.vault.repository.mapping;

import org.springframework.data.keyvalue.core.mapping.KeyValuePersistentEntity;
import org.springframework.data.mapping.PersistentEntity;

/**
 * Vault specific {@link PersistentEntity}.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public interface VaultPersistentEntity<T> extends
		KeyValuePersistentEntity<T, VaultPersistentProperty> {

	/**
	 * @return the secret backend in which this {@link PersistentEntity} is stored.
	 */
	String getSecretBackend();
}
