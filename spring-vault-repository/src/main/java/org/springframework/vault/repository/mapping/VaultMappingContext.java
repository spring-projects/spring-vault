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
package org.springframework.vault.repository.mapping;

import org.springframework.data.keyvalue.core.mapping.KeySpaceResolver;
import org.springframework.data.keyvalue.core.mapping.context.KeyValueMappingContext;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.util.TypeInformation;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Mapping context for {@link VaultPersistentEntity Vault-specific entities}.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class VaultMappingContext extends
		KeyValueMappingContext<VaultPersistentEntity<?>, VaultPersistentProperty> {

	private KeySpaceResolver fallbackKeySpaceResolver = SimpleClassNameKeySpaceResolver.INSTANCE;

	public KeySpaceResolver getFallbackKeySpaceResolver() {
		return fallbackKeySpaceResolver;
	}

	@Override
	public void setFallbackKeySpaceResolver(KeySpaceResolver fallbackKeySpaceResolver) {
		this.fallbackKeySpaceResolver = fallbackKeySpaceResolver;
	}

	@Override
	protected <T> VaultPersistentEntity<?> createPersistentEntity(
			TypeInformation<T> typeInformation) {
		return new BasicVaultPersistentEntity<>(typeInformation, fallbackKeySpaceResolver);
	}

	@Override
	protected VaultPersistentProperty createPersistentProperty(Property property,
			VaultPersistentEntity<?> owner, SimpleTypeHolder simpleTypeHolder) {
		return new VaultPersistentProperty(property, owner, simpleTypeHolder);
	}

	/**
	 * Most trivial implementation of {@link KeySpaceResolver} returning the
	 * {@link Class#getName()}.
	 *
	 * @author Mark Paluch
	 */
	enum SimpleClassNameKeySpaceResolver implements KeySpaceResolver {

		INSTANCE;

		@Override
		public String resolveKeySpace(Class<?> type) {

			Assert.notNull(type, "Type must not be null!");
			return StringUtils
					.uncapitalize(ClassUtils.getUserClass(type).getSimpleName());
		}
	}
}
