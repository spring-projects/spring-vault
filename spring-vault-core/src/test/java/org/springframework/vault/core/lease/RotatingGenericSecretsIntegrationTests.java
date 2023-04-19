/*
 * Copyright 2020-2022 the original author or authors.
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
package org.springframework.vault.core.lease;

import java.util.Collections;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.vault.core.VaultIntegrationTestConfiguration;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.PrepareVault;
import org.springframework.vault.util.VaultInitializer;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assumptions.*;

/**
 * Integration tests for rotating generic secrets.
 *
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@SpringJUnitConfig(
		classes = { VaultIntegrationTestConfiguration.class, RotatingGenericSecretsIntegrationTestConfiguration.class })
class RotatingGenericSecretsIntegrationTests extends IntegrationTestSupport {

	@BeforeAll
	static void beforeAll() {

		VaultInitializer initializer = new VaultInitializer();

		initializer.initialize();
		PrepareVault prepare = initializer.prepare();

		assumeThat(prepare.getVersion()).isGreaterThanOrEqualTo(VaultInitializer.VERSIONING_INTRODUCED_WITH);

		VaultKeyValueOperations versioned = prepare.getVaultOperations()
			.opsForKeyValue("versioned", VaultKeyValueOperationsSupport.KeyValueBackend.KV_2);

		versioned.put("rotating", Collections.singletonMap("key", "value"));
	}

	@Test
	void name(@Autowired RotatingGenericSecretsIntegrationTestConfiguration.PropertySourceHolder holder) {

		assertThat(holder.propertySource.getProperty("generic.rotating.key")).isEqualTo("value");
	}

}
