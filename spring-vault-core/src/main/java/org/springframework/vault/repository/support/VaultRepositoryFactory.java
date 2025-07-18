/*
 * Copyright 2017-2025 the original author or authors.
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
package org.springframework.vault.repository.support;

import java.util.Objects;

import org.springframework.data.keyvalue.core.KeyValueOperations;
import org.springframework.data.keyvalue.repository.query.KeyValuePartTreeQuery;
import org.springframework.data.keyvalue.repository.support.KeyValueRepositoryFactory;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryComposition;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.core.support.RepositoryFragment;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.parser.AbstractQueryCreator;
import org.springframework.vault.repository.core.MappingVaultEntityInformation;
import org.springframework.vault.repository.core.VaultKeyValueTemplate;
import org.springframework.vault.repository.mapping.VaultPersistentEntity;
import org.springframework.vault.repository.query.VaultQueryCreator;

/**
 * {@link RepositoryFactorySupport} specific of handing Vault
 * {@link org.springframework.data.keyvalue.repository.KeyValueRepository}.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class VaultRepositoryFactory extends KeyValueRepositoryFactory {

	private final KeyValueOperations operations;

	public VaultRepositoryFactory(KeyValueOperations keyValueOperations) {
		this(keyValueOperations, VaultQueryCreator.class);
	}

	public VaultRepositoryFactory(KeyValueOperations keyValueOperations,
			Class<? extends AbstractQueryCreator<?, ?>> queryCreator) {
		this(keyValueOperations, queryCreator, KeyValuePartTreeQuery.class);
	}

	public VaultRepositoryFactory(KeyValueOperations keyValueOperations,
			Class<? extends AbstractQueryCreator<?, ?>> queryCreator,
			Class<? extends RepositoryQuery> repositoryQueryType) {
		super(keyValueOperations, queryCreator, repositoryQueryType);

		this.operations = keyValueOperations;
	}

	@Override
	protected RepositoryComposition.RepositoryFragments getRepositoryFragments(RepositoryMetadata metadata,
			KeyValueOperations operations) {

		RepositoryComposition.RepositoryFragments fragments = super.getRepositoryFragments(metadata, operations);

		if (RevisionRepository.class.isAssignableFrom(metadata.getRepositoryInterface())
				&& operations instanceof VaultKeyValueTemplate template) {

			VaultPersistentEntity<?> entity = (VaultPersistentEntity<?>) this.operations.getMappingContext()
				.getRequiredPersistentEntity(metadata.getDomainType());
			EntityInformation<?, String> entityInformation = getEntityInformation(metadata.getDomainType());
			VaultRevisionRepository<?> repository = new VaultRevisionRepository<>(entityInformation,
					Objects.requireNonNull(entity.getKeySpace()), template);

			return fragments.append(RepositoryFragment.implemented(repository));
		}

		return fragments;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T, ID> EntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {

		VaultPersistentEntity<T> entity = (VaultPersistentEntity<T>) this.operations.getMappingContext()
			.getRequiredPersistentEntity(domainClass);

		return new MappingVaultEntityInformation<>(entity);
	}

}
