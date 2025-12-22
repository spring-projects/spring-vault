/*
 * Copyright 2018-2025 the original author or authors.
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

package org.springframework.vault.core;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.support.VaultResponse;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link VaultKeyValue2Template} using the non-versioned
 * Key/Value (k/v version 1) secrets engine.
 *
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
class VaultKeyValueTemplateIntegrationTests extends AbstractVaultKeyValueTemplateIntegrationTests {

	VaultKeyValueTemplateIntegrationTests() {
		super("secret", KeyValueBackend.unversioned());
	}

	@Test
	void shouldReadSecretWithTtl() {

		Map<String, Object> secret = new HashMap<>();
		secret.put("key", "value");
		secret.put("ttl", "5");

		this.kvOperations.put("my-secret", secret);

		VaultResponse response = this.kvOperations.get("my-secret");

		assertThat(response.getRequiredData()).containsEntry("key", "value");
		assertThat(response.getLeaseDuration()).isEqualTo(5L);
	}

}
