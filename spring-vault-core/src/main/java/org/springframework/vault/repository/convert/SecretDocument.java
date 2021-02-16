/*
 * Copyright 2017-2021 the original author or authors.
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
package org.springframework.vault.repository.convert;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.support.VaultResponse;

/**
 * Vault database exchange object containing data before/after it's exchanged with Vault.
 * A {@link SecretDocument} is basically an object with an {@code id} and a body
 * represented as {@link Map} of {@link String} and {@link Object}. It can be created
 * {@link #from(String, VaultResponse) from} an Id and {@link VaultResponse}.
 * <p>
 * A secret document can hold simple properties, {@link java.util.Collection list}
 * properties and nested objects as {@link Map}s.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class SecretDocument {

	private @Nullable String id;

	private final Map<String, Object> body;

	/**
	 * Create a new, empty {@link SecretDocument}.
	 */
	public SecretDocument() {
		this(null, new LinkedHashMap<>());
	}

	/**
	 * Create a new {@link SecretDocument} given a {@link Map body map}.
	 * @param body must not be {@literal null}.
	 */
	public SecretDocument(Map<String, Object> body) {
		this(null, body);
	}

	/**
	 * Create a new {@link SecretDocument} given an {@code id} and {@link Map body map}.
	 * @param id may be {@literal null}.
	 * @param body must not be {@literal null}.
	 */
	public SecretDocument(@Nullable String id, Map<String, Object> body) {

		Assert.notNull(body, "Body must not be null");

		this.id = id;
		this.body = body;
	}

	public SecretDocument(String id) {
		this(id, new LinkedHashMap<>());
	}

	/**
	 * Factory method to create a {@link SecretDocument} from an {@code id} and
	 * {@link VaultResponse}.
	 * @param id must not be {@literal null}.
	 * @param vaultResponse must not be {@literal null}.
	 * @return the {@link SecretDocument}.
	 */
	@SuppressWarnings("ConstantConditions")
	public static SecretDocument from(@Nullable String id, VaultResponse vaultResponse) {
		return new SecretDocument(id, vaultResponse.getData());
	}

	/**
	 * @return the Id or {@literal null} if the Id is not set.
	 */
	@Nullable
	public String getId() {
		return this.id;
	}

	/**
	 * Set the Id.
	 * @param id may be {@literal null}.
	 */
	public void setId(@Nullable String id) {
		this.id = id;
	}

	/**
	 * @return the body of this {@link SecretDocument}
	 */
	public Map<String, Object> getBody() {
		return this.body;
	}

	/**
	 * Retrieve a value from the secret document by its {@code key}.
	 * @param key must not be {@literal null}.
	 * @return the value or {@literal null}, if the value is not present.
	 */
	@Nullable
	public Object get(String key) {
		return this.body.get(key);
	}

	/**
	 * Set a value in the secret document.
	 * @param key must not be {@literal null}.
	 * @param value must not be {@literal null}.
	 */
	public void put(String key, Object value) {
		this.body.put(key, value);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof SecretDocument))
			return false;
		SecretDocument that = (SecretDocument) o;
		return Objects.equals(this.id, that.id) && Objects.equals(this.body, that.body);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.body);
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(getClass().getSimpleName());
		sb.append(" [id='").append(this.id).append('\'');
		sb.append(", body=").append(this.body);
		sb.append(']');
		return sb.toString();
	}

}
