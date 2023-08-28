/*
 * Copyright 2018-2022 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import reactor.test.StepVerifier;

/**
 * Integration tests for {@link ReactiveVaultKeyValue2Template} using the versioned
 * Key/Value (k/v version 2) backend.
 *
 * @author Timothy Weiand
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
class ReactiveVaultKeyValueTemplateVersionedIntegrationTests
		extends AbstractReactiveVaultKeyValueTemplateIntegrationTests {

	ReactiveVaultKeyValueTemplateVersionedIntegrationTests() {
		super("versioned", KeyValueBackend.versioned());
	}

	@Test
	void shouldPatchSecret() {

		var oldKey = "key";
		var newKey = "newKey";

		var secret = Collections.singletonMap(oldKey, "value");

		var key = UUID.randomUUID().toString();

		kvOperations.put(key, secret).as(StepVerifier::create).verifyComplete();

		var newSecret = Collections.singletonMap(newKey, "newValue");

		kvOperations.patch(key, newSecret)
			.as(StepVerifier::create)
			.assertNext(b -> assertThat(b).isTrue())
			.verifyComplete();
		kvOperations.list("/")
			.collectList()
			.as(StepVerifier::create)
			.assertNext(list -> assertThat(list).contains(key))
			.verifyComplete();

		kvOperations.get(key).as(StepVerifier::create).assertNext(vaultResponse -> {
			var data = vaultResponse.getRequiredData();
			assertThat(data).containsKey(oldKey).containsKey(newKey);
		}).verifyComplete();
	}

	@Test
	void patchShouldFailWithSecretNotFoundException() {

		kvOperations.patch("unknown", Collections.singletonMap("foo", "newValue"))
			.as(StepVerifier::create)
			.expectErrorSatisfies(t -> {
				if (t instanceof SecretNotFoundException e) {
					assertThat(e).hasMessageContaining("versioned/data/unknown");
					assertThat(e.getPath()).isEqualTo("versioned/unknown");
				}
				else {
					fail("missing SecretNotFoundException");
				}
			})
			.verify();
	}

}
