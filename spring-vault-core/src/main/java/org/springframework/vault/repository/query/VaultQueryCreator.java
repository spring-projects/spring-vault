/*
 * Copyright 2017-2019 the original author or authors.
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Sort;
import org.springframework.data.keyvalue.core.query.KeyValueQuery;
import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.parser.AbstractQueryCreator;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.Part.IgnoreCaseType;
import org.springframework.data.repository.query.parser.Part.Type;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.vault.repository.mapping.VaultPersistentEntity;
import org.springframework.vault.repository.mapping.VaultPersistentProperty;

/**
 * Query creator for Vault queries. Vault queries are limited to criterias constraining
 * the {@link org.springframework.data.annotation.Id} property. A query consists of
 * chained {@link Predicate}s that are evaluated for each Id value.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class VaultQueryCreator
		extends AbstractQueryCreator<KeyValueQuery<VaultQuery>, VaultQuery> {

	private final MappingContext<VaultPersistentEntity<?>, VaultPersistentProperty> mappingContext;

	/**
	 * Create a new {@link VaultQueryCreator} given {@link PartTree} and
	 * {@link ParameterAccessor}.
	 *
	 * @param tree must not be {@literal null}.
	 * @param parameters must not be {@literal null}.
	 * @param mappingContext must not be {@literal null}.
	 */
	public VaultQueryCreator(PartTree tree, ParameterAccessor parameters,
			MappingContext<VaultPersistentEntity<?>, VaultPersistentProperty> mappingContext) {

		super(tree, parameters);
		this.mappingContext = mappingContext;
	}

	@Override
	protected VaultQuery create(Part part, Iterator<Object> parameters) {
		return new VaultQuery(createPredicate(part, parameters));
	}

	@Override
	protected VaultQuery and(Part part, VaultQuery base, Iterator<Object> parameters) {
		return base.and(createPredicate(part, parameters));
	}

	private Predicate<String> createPredicate(Part part, Iterator<Object> parameters) {

		PersistentPropertyPath<VaultPersistentProperty> propertyPath = mappingContext
				.getPersistentPropertyPath(part.getProperty());

		if (propertyPath.getLeafProperty() != null
				&& !propertyPath.getLeafProperty().isIdProperty()) {
			throw new InvalidDataAccessApiUsageException(
					String.format("Cannot create criteria for non-@Id property %s",
							propertyPath.getLeafProperty()));
		}

		VariableAccessor accessor = getVariableAccessor(part);

		Predicate<String> predicate = from(part, accessor, parameters);

		return it -> predicate.test(accessor.toString(it));
	}

	/**
	 * Return a {@link Predicate} depending on the {@link Part} given.
	 *
	 * @param part
	 * @param parameters
	 * @return
	 */
	private static Predicate<String> from(Part part, VariableAccessor accessor,
			Iterator<Object> parameters) {

		Type type = part.getType();

		switch (type) {
		case AFTER:
		case GREATER_THAN:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> it.compareTo(value) > 0);
		case GREATER_THAN_EQUAL:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> it.compareTo(value) >= 0);
		case BEFORE:
		case LESS_THAN:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> it.compareTo(value) < 0);
		case LESS_THAN_EQUAL:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> it.compareTo(value) <= 0);
		case BETWEEN:

			String from = accessor.nextString(parameters);
			String to = accessor.nextString(parameters);

			return it -> it.compareTo(from) >= 0 && it.compareTo(to) <= 0;
		case NOT_IN:
			return new Criteria<>(accessor.nextAsArray(parameters),
					(value, it) -> Arrays.binarySearch(value, it) < 0);
		case IN:
			return new Criteria<>(accessor.nextAsArray(parameters),
					(value, it) -> Arrays.binarySearch(value, it) >= 0);
		case STARTING_WITH:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> it.startsWith(value));
		case ENDING_WITH:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> it.endsWith(value));
		case CONTAINING:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> it.contains(value));
		case NOT_CONTAINING:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> !it.contains(value));
		case REGEX:
			return Pattern
					.compile((String) parameters.next(),
							isIgnoreCase(part) ? Pattern.CASE_INSENSITIVE : 0)
					.asPredicate();
		case TRUE:
			return it -> it.equalsIgnoreCase("true");
		case FALSE:
			return it -> it.equalsIgnoreCase("false");
		case SIMPLE_PROPERTY:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> it.equals(value));
		case NEGATING_SIMPLE_PROPERTY:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> !it.equals(value));
		default:
			throw new IllegalArgumentException("Unsupported keyword!");
		}
	}

	@Override
	protected VaultQuery or(VaultQuery vaultQuery, VaultQuery other) {
		return vaultQuery.or(other);
	}

	@Override
	protected KeyValueQuery<VaultQuery> complete(VaultQuery vaultQuery, Sort sort) {

		KeyValueQuery<VaultQuery> query = new KeyValueQuery<>(vaultQuery);

		if (sort.isSorted()) {
			query.orderBy(sort);
		}

		return query;
	}

	private static VariableAccessor getVariableAccessor(Part part) {
		return isIgnoreCase(part) ? VariableAccessor.Lowercase : VariableAccessor.AsIs;
	}

	private static boolean isIgnoreCase(Part part) {
		return part.shouldIgnoreCase() != IgnoreCaseType.NEVER;
	}

	static final class Criteria<T> implements Predicate<String> {

		private final T value;
		private final BiPredicate<T, String> predicate;

		public Criteria(T value, BiPredicate<T, String> predicate) {
			this.value = value;
			this.predicate = predicate;
		}

		@Override
		public boolean test(String s) {
			return predicate.test(value, s);
		}

		public T getValue() {
			return this.value;
		}

		public BiPredicate<T, String> getPredicate() {
			return this.predicate;
		}

		public boolean equals(final Object o) {
			if (o == this)
				return true;
			if (!(o instanceof Criteria))
				return false;
			final Criteria<?> other = (Criteria<?>) o;
			final Object this$value = this.getValue();
			final Object other$value = other.getValue();
			if (this$value == null ? other$value != null
					: !this$value.equals(other$value))
				return false;
			final Object this$predicate = this.getPredicate();
			final Object other$predicate = other.getPredicate();
			if (this$predicate == null ? other$predicate != null
					: !this$predicate.equals(other$predicate))
				return false;
			return true;
		}

		public int hashCode() {
			final int PRIME = 59;
			int result = 1;
			final Object $value = this.getValue();
			result = result * PRIME + ($value == null ? 43 : $value.hashCode());
			final Object $predicate = this.getPredicate();
			result = result * PRIME + ($predicate == null ? 43 : $predicate.hashCode());
			return result;
		}

		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append(getClass().getSimpleName());
			sb.append(" [value=").append(value);
			sb.append(", predicate=").append(predicate);
			sb.append(']');
			return sb.toString();
		}
	}

	enum VariableAccessor {

		AsIs {

			@Override
			String nextString(Iterator<Object> parameters) {
				return parameters.next().toString();
			}

			@Override
			String[] nextAsArray(Iterator<Object> iterator) {

				Object next = iterator.next();

				if (next instanceof Collection) {
					return ((Collection<?>) next).toArray(new String[0]);
				}
				else if (next != null && next.getClass().isArray()) {
					return (String[]) next;
				}

				return new String[] { (String) next };
			}

			@Override
			String toString(String value) {
				return value;
			}
		},

		Lowercase {

			@Override
			String nextString(Iterator<Object> parameters) {
				return AsIs.nextString(parameters).toLowerCase();
			}

			@Override
			String[] nextAsArray(Iterator<Object> iterator) {

				String[] original = AsIs.nextAsArray(iterator);
				String[] lowercase = new String[original.length];

				for (int i = 0; i < original.length; i++) {
					lowercase[i] = original[i].toLowerCase();
				}

				return lowercase;
			}

			@Override
			String toString(String value) {
				return value.toLowerCase();
			}
		};

		abstract String[] nextAsArray(Iterator<Object> iterator);

		abstract String nextString(Iterator<Object> iterator);

		abstract String toString(String value);
	}
}
