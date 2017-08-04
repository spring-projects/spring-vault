/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.vault.repository.support;

import java.io.Serializable;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.data.keyvalue.core.KeyValueOperations;
import org.springframework.data.keyvalue.repository.support.KeyValueRepositoryFactoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.parser.AbstractQueryCreator;

/**
 * Adapter for Springs {@link FactoryBean} interface to allow easy setup of
 * {@link VaultRepositoryFactory} via Spring configuration.
 *
 * @param <T> The repository type.
 * @param <S> The repository domain type.
 * @param <ID> The repository id type.
 * @since 2.0
 */
public class VaultRepositoryFactoryBean<T extends Repository<S, ID>, S, ID extends Serializable>
		extends KeyValueRepositoryFactoryBean<T, S, ID> {

	/**
	 * Creates a new {@link VaultRepositoryFactoryBean} for the given repository
	 * interface.
	 *
	 * @param repositoryInterface must not be {@literal null}.
	 */
	public VaultRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
		super(repositoryInterface);
	}

	@Override
	protected VaultRepositoryFactory createRepositoryFactory(
			KeyValueOperations operations,
			Class<? extends AbstractQueryCreator<?, ?>> queryCreator,
			Class<? extends RepositoryQuery> repositoryQueryType) {

		return new VaultRepositoryFactory(operations, queryCreator, repositoryQueryType);
	}
}
