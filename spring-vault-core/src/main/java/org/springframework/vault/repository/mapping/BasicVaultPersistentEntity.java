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
package org.springframework.vault.repository.mapping;

import org.springframework.data.keyvalue.core.mapping.BasicKeyValuePersistentEntity;
import org.springframework.data.keyvalue.core.mapping.KeySpaceResolver;
import org.springframework.data.util.TypeInformation;
import org.springframework.util.StringUtils;

/**
 * {@link VaultPersistentEntity} implementation.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class BasicVaultPersistentEntity<T> extends
		BasicKeyValuePersistentEntity<T, VaultPersistentProperty> implements
		VaultPersistentEntity<T> {

	private final String keyspace;

	private final String secretBackend;

	/**
	 * Creates new {@link BasicVaultPersistentEntity}.
	 *
	 * @param information must not be {@literal null}.
	 * @param fallbackKeySpaceResolver can be {@literal null}.
	 */
	public BasicVaultPersistentEntity(TypeInformation<T> information,
			KeySpaceResolver fallbackKeySpaceResolver) {
		super(information, fallbackKeySpaceResolver);

		Secret annotation = findAnnotation(Secret.class);

		String keyspace = super.getKeySpace();
		String secretBackend = "secret";

		if (annotation != null) {
			if (StringUtils.hasText(annotation.backend())) {
				secretBackend = annotation.backend();
			}
		}

		this.secretBackend = secretBackend;
		this.keyspace = String.format("%s/%s", secretBackend, keyspace);
	}

	@Override
	public String getKeySpace() {
		return keyspace;
	}

	@Override
	public String getSecretBackend() {
		return secretBackend;
	}
}
