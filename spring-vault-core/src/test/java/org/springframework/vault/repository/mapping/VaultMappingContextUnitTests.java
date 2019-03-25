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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link VaultMappingContext}.
 *
 * @author Mark Paluch
 */
public class VaultMappingContextUnitTests {

	VaultMappingContext context = new VaultMappingContext();

	@Test
	public void shouldCreatePersistentEntity() {

		VaultPersistentEntity<?> entity = context.getPersistentEntity(Person.class);

		assertThat(entity).isNotNull();
		assertThat(entity.getSecretBackend()).isEqualTo("secret");
		assertThat(entity.getKeySpace()).isEqualTo("secret/person");
	}

	@Test
	public void shouldDetermineKeyspace() {

		assertThat(context.getRequiredPersistentEntity(Login.class).getSecretBackend())
				.isEqualTo("secret");
		assertThat(context.getRequiredPersistentEntity(Login.class).getKeySpace())
				.isEqualTo("secret/login");

		assertThat(
				context.getRequiredPersistentEntity(Credentials.class).getSecretBackend())
				.isEqualTo("shared");
		assertThat(context.getRequiredPersistentEntity(Credentials.class).getKeySpace())
				.isEqualTo("shared/Email");
	}

	private static class Person {
	}

	@Secret
	private static class Login {
	}

	@Secret(value = "Email", backend = "shared")
	private static class Credentials {
	}
}
