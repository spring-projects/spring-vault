/*
 * Copyright 2026-present the original author or authors.
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.PrepareVault;
import org.springframework.vault.util.VaultInitializer;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assumptions.*;

/**
 * Integration tests for Key/Value secrets rotation through
 * {@link SecretLeaseContainer}.
 * @author Mark Paluch
 */
class KeyValueSecretRotationLeaseIntegrationTests extends IntegrationTestSupport {

	SecretLeaseContainer container;

	VaultKeyValueOperations kv1;

	VaultKeyValueOperations kv2;

	@BeforeEach
	void before() {
		PrepareVault prepare = prepare();

		assumeThat(prepare.getVersion()).isGreaterThanOrEqualTo(VaultInitializer.VERSIONING_INTRODUCED_WITH);

		kv1 = prepare.getVaultOperations().opsForKeyValue("secret");
		kv2 = prepare.getVaultOperations().opsForKeyValue("versioned");

		kv1.put("with-ttl", Map.of("unversioned", "value1", "ttl", "10m"));
		kv1.put("without-ttl", Map.of("unversioned", "value1", "ttl", "0"));
		kv2.put("rotating", Map.of("versioned", "value1"));

		container = new SecretLeaseContainer(prepare().getVaultOperations());
		container.afterPropertiesSet();
		container.start();
	}

	@AfterEach
	void after() throws Exception {
		container.stop();
		container.destroy();
	}

	@Test
	void shouldRotateUnversionedSecretWithoutTtl() {

		AtomicReference<String> secretRef = new AtomicReference<>();
		ManagedSecret secret = ManagedSecret.rotating("secret/without-ttl", secretAccessor -> {
			secretRef.set(secretAccessor.getRequiredString("unversioned"));
		});
		secret.registerSecret(container);

		assertThat(secretRef).hasValue("value1");

		kv1.put("without-ttl", Collections.singletonMap("unversioned", "value2"));

		container.rotate(RequestedSecret.rotating("secret/without-ttl"));

		assertThat(secretRef).hasValue("value2");
	}

	@Test
	void shouldRotateUnversionedSecretWithTtl() {

		AtomicReference<String> secretRef = new AtomicReference<>();
		ManagedSecret secret = ManagedSecret.rotating("secret/with-ttl", secretAccessor -> {
			secretRef.set(secretAccessor.getRequiredString("unversioned"));
		});
		secret.registerSecret(container);

		assertThat(secretRef).hasValue("value1");

		kv1.put("with-ttl", Collections.singletonMap("unversioned", "value2"));

		container.rotate(RequestedSecret.rotating("secret/with-ttl"));

		assertThat(secretRef).hasValue("value2");
	}

	@Test
	void shouldRotateVersionedSecret() {

		AtomicReference<String> secretRef = new AtomicReference<>();
		ManagedSecret secret = ManagedSecret.rotating("versioned/rotating", secretAccessor -> {
			secretRef.set(secretAccessor.getRequiredString("versioned"));
		});
		secret.registerSecret(container);

		assertThat(secretRef).hasValue("value1");

		kv2.put("rotating", Collections.singletonMap("versioned", "value2"));

		container.rotate(RequestedSecret.rotating("versioned/rotating"));

		assertThat(secretRef).hasValue("value2");
	}

}
