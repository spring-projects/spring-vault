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
package org.springframework.vault.repository.query;

import java.util.function.Predicate;

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

	/**
	 * Create a new {@link VaultQuery} that evaluates unconditionally to {@literal true}.
	 */
	public VaultQuery() {
		this(s -> true);
	}

	/**
	 * Create a new {@link VaultQuery} given {@link Predicate}.
	 * @param predicate must not be {@literal null}.
	 */
	public VaultQuery(Predicate<String> predicate) {

		Assert.notNull(predicate, "Predicate must not be null");

		this.predicate = predicate;
	}

	/**
	 * Evaluate the query against a {@link SecretDocument}.
	 * @param document must not be {@literal null}.
	 * @return {@literal true} if the predicate matches, {@literal false} otherwise.
	 */
	public boolean test(SecretDocument document) {

		Assert.notNull(this.predicate, "Predicate must not be null");

		return this.predicate.test(document.getId());
	}

	/**
	 * Evaluate the query against a {@link String}.
	 * @param id must not be {@literal null}.
	 * @return {@literal true} if the predicate matches, {@literal false} otherwise.
	 */
	public boolean test(String id) {

		Assert.notNull(id, "Id to test must not be null");

		return this.predicate.test(id);
	}

	/**
	 * Compose a new {@link VaultQuery} using predicates of {@literal this} and the
	 * {@code other} query using logical {@code AND}.
	 * @param other must not be {@literal null}.
	 * @return a new composed {@link VaultQuery}.
	 * @see Predicate#and(Predicate)
	 */
	public VaultQuery and(VaultQuery other) {
		return new VaultQuery(this.predicate.and(other.predicate));
	}

	/**
	 * Compose a new {@link VaultQuery} using predicates of {@literal this} and the
	 * {@code other} query using logical {@code AND}.
	 * @param predicate must not be {@literal null}.
	 * @return a new composed {@link VaultQuery}.
	 * @see Predicate#and(Predicate)
	 */
	public VaultQuery and(Predicate<String> predicate) {
		return new VaultQuery(this.predicate.and(predicate));
	}

	/**
	 * Compose a new {@link VaultQuery} using predicates of {@literal this} and the
	 * {@code other} query using logical {@code OR}.
	 * @param other must not be {@literal null}.
	 * @return a new composed {@link VaultQuery}.
	 * @see Predicate#and(Predicate)
	 */
	public VaultQuery or(VaultQuery other) {
		return new VaultQuery(this.predicate.or(other.predicate));
	}

	/**
	 * @return the underlying predicate.
	 */
	public Predicate<String> getPredicate() {
		return this.predicate;
	}

}
