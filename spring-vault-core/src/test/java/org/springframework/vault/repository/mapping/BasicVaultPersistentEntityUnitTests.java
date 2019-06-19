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
package org.springframework.vault.repository.mapping;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import org.springframework.data.annotation.Id;
import org.springframework.data.spel.ExtensionAwareEvaluationContextProvider;
import org.springframework.data.spel.spi.EvaluationContextExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BasicVaultPersistentEntity} via {@link VaultMappingContext}.
 *
 * @author Mark Paluch
 */
public class BasicVaultPersistentEntityUnitTests {

	private VaultMappingContext mappingContext = new VaultMappingContext();

	@Test
	public void shouldSetIdPropertyThroughName() {

		VaultPersistentEntity<?> persistentEntity = mappingContext
				.getPersistentEntity(IdProperty.class);

		assertThat(persistentEntity.getIdProperty()).isNotNull();
	}

	@Test
	public void shouldSetIdPropertyThroughAnnotation() {

		VaultPersistentEntity<?> persistentEntity = mappingContext
				.getPersistentEntity(ExplicitId.class);

		assertThat(persistentEntity.getIdProperty()).isNotNull();
	}

	@Test
	public void shouldEvaluateExpression() {

		VaultPersistentEntity<?> persistentEntity = mappingContext
				.getPersistentEntity(ExpressionEntity.class);

		persistentEntity
				.setEvaluationContextProvider(new ExtensionAwareEvaluationContextProvider(
						Collections.singletonList(new SampleExtension())));

		assertThat(persistentEntity.getSecretBackend()).isEqualTo("collectionName");

		assertThat(persistentEntity.getKeySpace()).isEqualTo("collectionName/foo");
	}

	static class IdProperty {
		String id, username;
	}

	static class ExplicitId {
		@Id
		String username;
	}

	@Secret(backend = "#{myProperty}", value = "#{myKeySpace}")
	static class ExpressionEntity {
		@Id
		String username;
	}

	static class SampleExtension implements EvaluationContextExtension {

		@Override
		public String getExtensionId() {
			return "sampleExtension";
		}

		@Override
		public Map<String, Object> getProperties() {

			Map<String, Object> properties = new LinkedHashMap<>();
			properties.put("myProperty", "collectionName");
			properties.put("myKeySpace", "foo");
			return properties;
		}
	}
}
