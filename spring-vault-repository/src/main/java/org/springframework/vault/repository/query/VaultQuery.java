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
package org.springframework.vault.repository.query;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.data.mapping.PropertyPath;
import org.springframework.util.Assert;
import org.springframework.vault.repository.convert.SecretDocument;

/**
 * Vault query consisting of a single {@link Predicate}. A new (empty) query evaluates
 * unconditionally to {@literal true} and can be composed using {@link #and(VaultQuery)}
 * and {@link #or(VaultQuery)}.
 * <p />
 * A query can express predicates only against the
 * {@link org.springframework.data.annotation.Id} field of a {@link SecretDocument}.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class VaultQuery {

	private final Predicate<String> predicate;

	private final Set<PropertyPath> propertyPaths;

	/**
	 * Create a new {@link VaultQuery} that evaluates unconditionally to {@literal true}.
	 */
	public VaultQuery() {
		this(s -> true);
	}

	/**
	 * Create a new {@link VaultQuery} given {@link Predicate}.
	 *
	 * @param predicate must not be {@literal null}.
	 */
	public VaultQuery(Predicate<String> predicate) {
		this(predicate, Collections.emptySet());
	}

	/**
	 * Create a new {@link VaultQuery} given {@link Predicate} and {@link PropertyPath}.
	 *
	 * @param predicate must not be {@literal null}.
	 */
	public VaultQuery(Predicate<String> predicate, PropertyPath propertyPath) {

		Assert.notNull(predicate, "Predicate must not be null");
		Assert.notNull(propertyPath, "PropertyPath must not be null");

		this.predicate = predicate;
		this.propertyPaths = Collections.singleton(propertyPath);
	}

	private VaultQuery(Predicate<String> predicate, Set<PropertyPath> propertyPaths) {

		Assert.notNull(propertyPaths, "PropertyPaths must not be null");
		Assert.notNull(predicate, "Predicate must not be null");

		this.predicate = predicate;
		this.propertyPaths = propertyPaths;
	}

	/**
	 * Evaluate the query against a {@link SecretDocument}.
	 *
	 * @param document must not be {@literal null}.
	 * @return {@literal true} if the predicate matches, {@literal false} otherwise.
	 */
	public boolean test(SecretDocument document) {

		Assert.notNull(predicate, "Predicate must not be null");

		return predicate.test(document.getId());
	}

	/**
	 * Evaluate the query against a {@link String}.
	 *
	 * @param id must not be {@literal null}.
	 * @return {@literal true} if the predicate matches, {@literal false} otherwise.
	 */
	public boolean test(String id) {

		Assert.notNull(id, "Id to test must not be null");

		return predicate.test(id);
	}

	/**
	 * Compose a new {@link VaultQuery} using predicates of {@literal this} and the
	 * {@code other} query using logical {@code AND}.
	 *
	 * @param other must not be {@literal null}.
	 * @return a new composed {@link VaultQuery}.
	 * @see Predicate#and(Predicate)
	 */
	public VaultQuery and(VaultQuery other) {

		Set<PropertyPath> propertyPaths = new HashSet<>(this.propertyPaths.size()
				+ other.propertyPaths.size(), 1);
		propertyPaths.addAll(this.propertyPaths);
		propertyPaths.addAll(other.propertyPaths);

		return new VaultQuery(this.predicate.and(other.predicate), propertyPaths);
	}

	/**
	 * Compose a new {@link VaultQuery} using predicates of {@literal this} and the
	 * {@code other} query using logical {@code OR}.
	 *
	 * @param other must not be {@literal null}.
	 * @return a new composed {@link VaultQuery}.
	 * @see Predicate#and(Predicate)
	 */
	public VaultQuery or(VaultQuery other) {

		Set<PropertyPath> propertyPaths = new HashSet<>(this.propertyPaths.size()
				+ other.propertyPaths.size(), 1);
		propertyPaths.addAll(this.propertyPaths);
		propertyPaths.addAll(other.propertyPaths);

		return new VaultQuery(this.predicate.or(other.predicate), propertyPaths);
	}

	/**
	 * Compose a new {@link VaultQuery} using predicates of {@literal this} query and the
	 * {@code other} {@link Predicate} using logical {@code AND}.
	 *
	 * @param other must not be {@literal null}.
	 * @return a new composed {@link VaultQuery}.
	 * @see Predicate#and(Predicate)
	 */
	public VaultQuery and(Predicate<String> predicate, PropertyPath propertyPath) {

		Set<PropertyPath> propertyPaths = new HashSet<>(this.propertyPaths.size() + 1, 1);
		propertyPaths.addAll(this.propertyPaths);
		propertyPaths.add(propertyPath);

		return new VaultQuery(this.predicate.and(predicate), propertyPaths);
	}

	/**
	 * @return the underlying predicate.
	 */
	public Predicate<String> getPredicate() {
		return predicate;
	}

	/**
	 * @return constrained {@link PropertyPath}s for this {@link VaultQuery}.
	 */
	public Set<PropertyPath> getPropertyPaths() {
		return Collections.unmodifiableSet(propertyPaths);
	}
}
