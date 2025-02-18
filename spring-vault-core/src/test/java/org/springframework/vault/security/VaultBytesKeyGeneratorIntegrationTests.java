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
package org.springframework.vault.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.RequiresVaultVersion;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link VaultBytesKeyGenerator}.
 *
 * @author Mark Paluch
 */
@RequiresVaultVersion("0.6.2")
class VaultBytesKeyGeneratorIntegrationTests extends IntegrationTestSupport {

	@BeforeEach
	void before() {

		if (!prepare().hasSecret("transit")) {
			prepare().mountSecret("transit");
		}
	}

	@Test
	void shouldGenerateRandomBytes() {

		VaultBytesKeyGenerator generator = new VaultBytesKeyGenerator(prepare().getVaultOperations(), "transit", 16);

		assertThat(generator.generateKey()).hasSize(16);
		assertThat(generator.getKeyLength()).isEqualTo(16);
	}

}
