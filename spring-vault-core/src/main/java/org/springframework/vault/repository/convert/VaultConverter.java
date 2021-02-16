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

import org.springframework.data.convert.EntityConverter;
import org.springframework.vault.repository.mapping.VaultPersistentEntity;
import org.springframework.vault.repository.mapping.VaultPersistentProperty;

/**
 * Central Vault-specific converter interface.
 *
 * @since 2.0
 */
public interface VaultConverter
		extends EntityConverter<VaultPersistentEntity<?>, VaultPersistentProperty, Object, SecretDocument> {

}
