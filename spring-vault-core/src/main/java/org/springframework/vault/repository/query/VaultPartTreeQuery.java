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
package org.springframework.vault.repository.query;

import org.springframework.data.keyvalue.core.KeyValueOperations;
import org.springframework.data.keyvalue.repository.query.KeyValuePartTreeQuery;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.parser.AbstractQueryCreator;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.data.spel.EvaluationContextProvider;
import org.springframework.vault.repository.mapping.VaultPersistentEntity;
import org.springframework.vault.repository.mapping.VaultPersistentProperty;

/**
 * Vault-specific {@link KeyValuePartTreeQuery}.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class VaultPartTreeQuery extends KeyValuePartTreeQuery {

	/**
	 * Creates a new {@link VaultPartTreeQuery} for the given {@link QueryMethod},
	 * {@link EvaluationContextProvider}, {@link KeyValueOperations} and query creator
	 * type.
	 *
	 * @param queryMethod must not be {@literal null}.
	 * @param evaluationContextProvider must not be {@literal null}.
	 * @param keyValueOperations must not be {@literal null}.
	 * @param queryCreator must not be {@literal null}.
	 */
	@SuppressWarnings("unchecked")
	public VaultPartTreeQuery(QueryMethod queryMethod,
			QueryMethodEvaluationContextProvider evaluationContextProvider,
			KeyValueOperations keyValueOperations,
			Class<? extends AbstractQueryCreator<?, ?>> queryCreator) {

		super(queryMethod, evaluationContextProvider, keyValueOperations,
				new VaultQueryCreatorFactory(
						(MappingContext) keyValueOperations.getMappingContext()));
	}

	static class VaultQueryCreatorFactory
			implements QueryCreatorFactory<VaultQueryCreator> {

		private final MappingContext<VaultPersistentEntity<?>, VaultPersistentProperty> mappingContext;

		public VaultQueryCreatorFactory(
				MappingContext<VaultPersistentEntity<?>, VaultPersistentProperty> mappingContext) {
			this.mappingContext = mappingContext;
		}

		@Override
		public VaultQueryCreator queryCreatorFor(PartTree partTree,
				ParameterAccessor accessor) {
			return new VaultQueryCreator(partTree, accessor, mappingContext);
		}
	}
}
