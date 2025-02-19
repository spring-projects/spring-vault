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
package org.springframework.vault.repository.convert;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.data.convert.DefaultTypeMapper;
import org.springframework.data.convert.SimpleTypeInformationMapper;
import org.springframework.data.convert.TypeAliasAccessor;
import org.springframework.data.convert.TypeInformationMapper;
import org.springframework.data.mapping.Alias;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.util.TypeInformation;

/**
 * Default implementation of {@link VaultTypeMapper} allowing configuration of the key to
 * lookup and store type information in {@link SecretDocument}. The key defaults to
 * {@link #DEFAULT_TYPE_KEY}. Actual type-to-{@link String} conversion and back is done in
 * {@link #readType(Object)} or {@link #getDefaultedTypeToBeUsed(Object)}. respectively.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class DefaultVaultTypeMapper extends DefaultTypeMapper<Map<String, Object>> implements VaultTypeMapper {

	public static final String DEFAULT_TYPE_KEY = "_class";

	@SuppressWarnings("rawtypes")
	private static final TypeInformation<Map> MAP_TYPE_INFO = TypeInformation.MAP;

	private final @Nullable String typeKey;

	/**
	 * Creates a default {@link VaultTypeMapper} that exchanges types using the type key
	 * {@literal _class}.
	 */
	public DefaultVaultTypeMapper() {
		this(DEFAULT_TYPE_KEY);
	}

	/**
	 * Creates a default {@link VaultTypeMapper} that exchanges types using the given
	 * {@code typeKey}.
	 * @param typeKey may not be {@literal null} to disable type hinting.
	 */
	public DefaultVaultTypeMapper(@Nullable String typeKey) {
		this(typeKey, Collections.singletonList(new SimpleTypeInformationMapper()));
	}

	/**
	 * Creates a default {@link VaultTypeMapper} that exchanges types using the given
	 * {@code typeKey} and {@link MappingContext}.
	 * @param typeKey may not be {@literal null} to disable type hinting.
	 * @param mappingContext must not be {@literal null} or empty.
	 */
	public DefaultVaultTypeMapper(@Nullable String typeKey,
			MappingContext<? extends PersistentEntity<?, ?>, ?> mappingContext) {
		this(typeKey, new SecretDocumentTypeAliasAccessor(typeKey), mappingContext,
				Collections.singletonList(new SimpleTypeInformationMapper()));
	}

	public DefaultVaultTypeMapper(@Nullable String typeKey, List<? extends TypeInformationMapper> mappers) {
		this(typeKey, new SecretDocumentTypeAliasAccessor(typeKey), null, mappers);
	}

	private DefaultVaultTypeMapper(@Nullable String typeKey, TypeAliasAccessor<Map<String, Object>> accessor,
			@Nullable MappingContext<? extends PersistentEntity<?, ?>, ?> mappingContext,
			List<? extends TypeInformationMapper> mappers) {

		super(accessor, mappingContext, mappers);

		this.typeKey = typeKey;
	}

	/**
	 * Checks whether the given key name matches the {@literal typeKey}.
	 * @param key
	 * @return {@literal true} if {@code key} matches the {@literal typeKey}.
	 */
	public boolean isTypeKey(String key) {
		return this.typeKey != null && this.typeKey.equals(key);
	}

	@Override
	protected TypeInformation<?> getFallbackTypeFor(Map<String, Object> source) {
		return MAP_TYPE_INFO;
	}

	/**
	 * {@link TypeAliasAccessor} to store aliases in a {@link SecretDocument}.
	 *
	 * @author Mark Paluch
	 */
	static class SecretDocumentTypeAliasAccessor implements TypeAliasAccessor<Map<String, Object>> {

		private final @Nullable String typeKey;

		SecretDocumentTypeAliasAccessor(@Nullable String typeKey) {
			this.typeKey = typeKey;
		}

		public Alias readAliasFrom(Map<String, Object> source) {
			return this.typeKey == null ? Alias.NONE : Alias.ofNullable(source.get(this.typeKey));
		}

		public void writeTypeTo(Map<String, Object> sink, Object alias) {
			if (this.typeKey != null) {
				sink.put(this.typeKey, alias);
			}
		}

	}

}
