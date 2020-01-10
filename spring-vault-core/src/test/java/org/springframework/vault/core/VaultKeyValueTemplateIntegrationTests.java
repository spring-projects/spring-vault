/*
 * Copyright 2018-2020 the original author or authors.
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

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.support.VaultResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link VaultKeyValue2Template} using the non-versioned Key/Value
 * (k/v version 1) backend.
 *
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
public class VaultKeyValueTemplateIntegrationTests extends
		AbstractVaultKeyValueTemplateIntegrationTests {

	public VaultKeyValueTemplateIntegrationTests() {
		super("secret", KeyValueBackend.unversioned());
	}

	@Test
	public void shouldReadSecretWithTtl() {

		Map<String, Object> secret = new HashMap<>();
		secret.put("key", "value");
		secret.put("ttl", "5");

		kvOperations.put("my-secret", secret);

		VaultResponse response = kvOperations.get("my-secret");

		assertThat(response.getRequiredData()).containsEntry("key", "value");
		assertThat(response.getLeaseDuration()).isEqualTo(5L);
	}
}
