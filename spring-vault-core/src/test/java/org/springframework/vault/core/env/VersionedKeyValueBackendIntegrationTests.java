/*
 * Copyright 2019-2020 the original author or authors.
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
package org.springframework.vault.core.env;

import java.util.Collections;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.vault.annotation.VaultPropertySource;
import org.springframework.vault.core.VaultIntegrationTestConfiguration;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.util.KeyValueDelegate;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.PrepareVault;
import org.springframework.vault.util.RequiresVaultVersion;
import org.springframework.vault.util.VaultInitializer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for secrets retrieved from a versioned Key-Value backend using
 * {@link KeyValueDelegate}.
 *
 * @author Mark Paluch
 * @see SecretLeaseContainer
 * @see org.springframework.vault.core.env.VaultPropertySource
 */
@RequiresVaultVersion(VaultInitializer.VERSIONING_INTRODUCED_WITH_VALUE)
class VersionedKeyValueBackendIntegrationTests extends IntegrationTestSupport {

	@VaultPropertySource("versioned/my/path")
	@Configuration
	static class NonRotatingSecret {
	}

	@VaultPropertySource(value = "versioned/my/path", renewal = VaultPropertySource.Renewal.ROTATE)
	@PropertySource(value = "http://foo", ignoreResourceNotFound = true)
	@Configuration
	static class RotatingSecret {
	}

	@BeforeAll
	static void beforeClass(VaultInitializer initializer) {

		PrepareVault prepare = initializer.prepare();

		VaultKeyValueOperations versionedKv = prepare.getVaultOperations().opsForKeyValue(
				"versioned", VaultKeyValueOperationsSupport.KeyValueBackend.versioned());

		versionedKv.put("my/path", Collections.singletonMap("my-key", "my-value"));
	}

	@Test
	void shouldRetrieveNonLeasedSecret() {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				VaultIntegrationTestConfiguration.class, NonRotatingSecret.class);
		context.registerShutdownHook();

		assertThat(context.getEnvironment().getProperty("my-key")).isEqualTo("my-value");

		context.stop();
	}

	@Test
	void shouldRetrieveRotatingSecret() {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				VaultIntegrationTestConfiguration.class, RotatingSecret.class);
		context.registerShutdownHook();

		assertThat(context.getEnvironment().getProperty("my-key")).isEqualTo("my-value");

		context.stop();
	}
}

