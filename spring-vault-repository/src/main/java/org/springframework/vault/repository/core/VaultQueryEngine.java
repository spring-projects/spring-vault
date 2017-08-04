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
package org.springframework.vault.repository.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.NullHandling;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.keyvalue.core.CriteriaAccessor;
import org.springframework.data.keyvalue.core.QueryEngine;
import org.springframework.data.keyvalue.core.SortAccessor;
import org.springframework.data.keyvalue.core.SpelPropertyComparator;
import org.springframework.data.keyvalue.core.query.KeyValueQuery;
import org.springframework.data.mapping.PropertyPath;
import org.springframework.data.mapping.context.PersistentPropertyPath;
import org.springframework.expression.spel.standard.SpelExpressionParser;
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
class VaultQueryEngine extends
		QueryEngine<VaultKeyValueAdapter, VaultQuery, Comparator<?>> {

	static final VaultQueryEngine INSTANCE = new VaultQueryEngine();

	private VaultQueryEngine() {
		super(VaultCriteriaAccessor.INSTANCE, SpelSortAccessor.INSTANCE);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Collection<?> execute(VaultQuery vaultQuery, Comparator<?> comparator,
			long offset, int rows, String keyspace) {
		return execute(vaultQuery, comparator, offset, rows, keyspace, Object.class);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Collection<T> execute(VaultQuery vaultQuery, Comparator<?> comparator,
			long offset, int rows, String keyspace, Class<T> type) {

		validatePropertyPaths(vaultQuery);

		Stream<String> stream = getAdapter().doList(keyspace).stream();

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

		Stream<T> typed = stream.map(it -> getAdapter().get(it, keyspace, type));

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
	public long count(VaultQuery vaultQuery, String keyspace) {

		validatePropertyPaths(vaultQuery);

		Stream<String> stream = getAdapter().doList(keyspace).stream();

		if (vaultQuery != null) {
			stream = stream.filter(vaultQuery::test);
		}

		return stream.count();
	}

	private void validatePropertyPaths(VaultQuery vaultQuery) {

		if (vaultQuery == null) {
			return;
		}

		Stream<PropertyPath> stream = vaultQuery.getPropertyPaths().stream();

		stream.map(it -> getAdapter().getMappingContext().getPersistentPropertyPath(it))
				.filter(it -> it.getLeafProperty() != null)
				.map(PersistentPropertyPath::getLeafProperty)
				.filter(it -> !it.isIdProperty())
				.forEach(
						property -> {
							throw new InvalidDataAccessApiUsageException(String.format(
									"Cannot create criteria for non-@Id property %s",
									property));
						});
	}

	enum VaultCriteriaAccessor implements CriteriaAccessor<VaultQuery> {

		INSTANCE;
		@Override
		public VaultQuery resolve(KeyValueQuery<?> query) {
			return (VaultQuery) query.getCriteria();
		}
	}

	/**
	 * {@link SortAccessor} implementation capable of creating
	 * {@link SpelPropertyComparator}.
	 */
	enum SpelSortAccessor implements SortAccessor<Comparator<?>> {
		INSTANCE;

		private final SpelExpressionParser parser = new SpelExpressionParser();

		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Override
		public Comparator<?> resolve(KeyValueQuery<?> query) {

			if (query == null || query.getSort() == null || query.getSort().isUnsorted()) {
				return null;
			}

			Optional<Comparator<?>> comparator = Optional.empty();
			for (Order order : query.getSort()) {

				SpelPropertyComparator<Object> spelSort = new SpelPropertyComparator<>(
						order.getProperty(), parser);

				if (Direction.DESC.equals(order.getDirection())) {

					spelSort.desc();

					if (!NullHandling.NATIVE.equals(order.getNullHandling())) {
						spelSort = NullHandling.NULLS_FIRST.equals(order
								.getNullHandling()) ? spelSort.nullsFirst() : spelSort
								.nullsLast();
					}
				}

				if (!comparator.isPresent()) {
					comparator = Optional.of(spelSort);
				}
				else {

					SpelPropertyComparator<Object> spelSortToUse = spelSort;
					comparator = comparator.map(it -> it.thenComparing(spelSortToUse));
				}
			}

			return comparator
					.orElseThrow(() -> new IllegalStateException(
							"No sort definitions have been added to this CompoundComparator to compare"));
		}
	}
}
