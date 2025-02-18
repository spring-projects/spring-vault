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
import reactor.test.StepVerifier;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ReactiveVaultKeyValue1Template}.
 *
 * @author Timothy R. Weiand
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
class ReactiveVaultKeyValueTemplateIntegrationTests extends AbstractReactiveVaultKeyValueTemplateIntegrationTests {

	ReactiveVaultKeyValueTemplateIntegrationTests() {
		super("secret", KeyValueBackend.unversioned());
	}

	@Test
	void shouldReadSecretWithTtl() {

		Map<String, Object> secret = new HashMap<>();
		secret.put("key", "value");
		secret.put("ttl", "5");

		kvOperations.put("my-secret", secret).as(StepVerifier::create).verifyComplete();

		kvOperations.get("my-secret").as(StepVerifier::create).consumeNextWith(response -> {
			assertThat(response.getRequiredData()).containsEntry("key", "value");
			assertThat(response.getLeaseDuration()).isEqualTo(5L);
		}).verifyComplete();
	}

}
