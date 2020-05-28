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
package org.springframework.vault.repository.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.keyvalue.core.CriteriaAccessor;
import org.springframework.data.keyvalue.core.QueryEngine;
import org.springframework.data.keyvalue.core.SpelSortAccessor;
import org.springframework.data.keyvalue.core.query.KeyValueQuery;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;
import org.springframework.vault.repository.query.VaultQuery;

/**
 * Query engine for Vault repository query methods. This engine queries Vault for all
 * elements in the keyspace and applies {@link java.util.function.Predicate}s to the
 * object id. Queries can contain only predicate subjects pointing to the
 * {@link org.springframework.data.annotation.Id} property.
 *
 * @author Mark Paluch
 * @since 2.0
 * @see VaultQuery
 * @see org.springframework.vault.repository.query.VaultQueryCreator
 */
class VaultQueryEngine extends QueryEngine<VaultKeyValueAdapter, VaultQuery, Comparator<?>> {

	private static final SpelExpressionParser parser = new SpelExpressionParser();

	VaultQueryEngine() {
		super(VaultCriteriaAccessor.INSTANCE, new SpelSortAccessor(parser));
	}

	@Override
	@SuppressWarnings("unchecked")
	public Collection<?> execute(@Nullable VaultQuery vaultQuery, @Nullable Comparator<?> comparator, long offset,
			int rows, String keyspace) {
		return execute(vaultQuery, comparator, offset, rows, keyspace, Object.class);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Collection<T> execute(@Nullable VaultQuery vaultQuery, @Nullable Comparator<?> comparator, long offset,
			int rows, String keyspace, Class<T> type) {

		Stream<String> stream = getRequiredAdapter().doList(keyspace).stream();

		if (vaultQuery != null) {
			stream = stream.filter(vaultQuery::test);
		}

		if (comparator == null) {

			if (offset > 0) {
				stream = stream.skip(offset);
			}

			if (rows > 0) {
				stream = stream.limit(rows);
			}
		}

		Stream<T> typed = stream.map(it -> getRequiredAdapter().get(it, keyspace, type));

		if (comparator != null) {

			typed = typed.sorted((Comparator) comparator);

			if (offset > 0) {
				typed = typed.skip(offset);
			}

			if (rows > 0) {
				typed = typed.limit(rows);
			}
		}

		return typed.collect(Collectors.toCollection(ArrayList::new));
	}

	@Override
	public long count(@Nullable VaultQuery vaultQuery, String keyspace) {

		Stream<String> stream = getRequiredAdapter().doList(keyspace).stream();

		if (vaultQuery != null) {
			stream = stream.filter(vaultQuery::test);
		}

		return stream.count();
	}

	enum VaultCriteriaAccessor implements CriteriaAccessor<VaultQuery> {

		INSTANCE;

		@Override
		public VaultQuery resolve(KeyValueQuery<?> query) {
			return (VaultQuery) query.getCriteria();
		}

	}

}
