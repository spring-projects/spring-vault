/*
 * Copyright 2016-2021 the original author or authors.
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

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.support.Policy;
import org.springframework.vault.support.Policy.Rule;
import org.springframework.vault.support.VaultHealth;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultUnsealStatus;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.RequiresVaultVersion;
import org.springframework.vault.util.VaultInitializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.vault.support.Policy.BuiltinCapabilities.READ;
import static org.springframework.vault.support.Policy.BuiltinCapabilities.UPDATE;

/**
 * Integration tests for {@link VaultSysTemplate} through {@link VaultSysOperations}.
 *
 * @author Mark Paluch
 * @author Maciej Drozdzowski
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
class VaultSysTemplateIntegrationTests extends IntegrationTestSupport {

	@Autowired
	VaultOperations vaultOperations;

	VaultSysOperations adminOperations;

	@BeforeEach
	void before() {
		this.adminOperations = this.vaultOperations.opsForSys();
	}

	@Test
	void getMountsShouldContainSecretBackend() {

		Map<String, VaultMount> mounts = this.adminOperations.getMounts();

		assertThat(mounts).containsKey("secret/");

		VaultMount secret = mounts.get("secret/");
		assertThat(Arrays.asList("kv", "generic")).contains(secret.getType());
	}

	@Test
	void mountShouldMountGenericSecret() {

		if (this.adminOperations.getMounts().containsKey("other/")) {
			this.adminOperations.unmount("other");
		}

		VaultMount mount = VaultMount.builder().type("generic")
				.config(Collections.singletonMap("default_lease_ttl", "1h")).description("hello, world").build();

		this.adminOperations.mount("other", mount);

		Map<String, VaultMount> mounts = this.adminOperations.getMounts();

		assertThat(mounts).containsKey("other/");

		VaultMount secret = mounts.get("other/");
		assertThat(secret.getDescription()).isEqualTo(mount.getDescription());
		assertThat(secret.getConfig()).containsEntry("default_lease_ttl", 3600);
		assertThat(Arrays.asList("kv", "generic")).contains(secret.getType());
	}

	@Test
	@RequiresVaultVersion(VaultInitializer.VERSIONING_INTRODUCED_WITH_VALUE)
	void mountShouldMountKv1Secret() {

		if (this.adminOperations.getMounts().containsKey("kVv1/")) {
			this.adminOperations.unmount("kVv1");
		}

		VaultMount mount = VaultMount.builder().type("kv").config(Collections.singletonMap("default_lease_ttl", "1h"))
				.description("hello, world").build();

		this.adminOperations.mount("kVv1", mount);

		Map<String, VaultMount> mounts = this.adminOperations.getMounts();

		assertThat(mounts).containsKey("kVv1/");

		VaultMount kVv1 = mounts.get("kVv1/");
		assertThat(kVv1.getDescription()).isEqualTo(mount.getDescription());
		assertThat(kVv1.getConfig()).containsEntry("default_lease_ttl", 3600);
		assertThat(kVv1.getType()).isEqualTo("kv");

		this.vaultOperations.write("secret/mykey", Collections.singletonMap("hello", "world"));

		VaultResponse read = this.vaultOperations.read("secret/mykey");
		assertThat(read).isNotNull();
		assertThat(read.getRequiredData()).containsEntry("hello", "world");
	}

	@Test
	@RequiresVaultVersion(VaultInitializer.VERSIONING_INTRODUCED_WITH_VALUE)
	void mountShouldMountKv2Secret() {

		if (this.adminOperations.getMounts().containsKey("kVv2/")) {
			this.adminOperations.unmount("kVv2");
		}

		VaultMount mount = VaultMount.builder().type("kv").config(Collections.singletonMap("default_lease_ttl", "1h"))
				.options(Collections.singletonMap("version", "2")).description("hello, world").build();

		this.adminOperations.mount("kVv2", mount);

		Map<String, VaultMount> mounts = this.adminOperations.getMounts();

		assertThat(mounts).containsKey("kVv2/");

		VaultMount kVv2 = mounts.get("kVv2/");
		assertThat(kVv2.getDescription()).isEqualTo(mount.getDescription());
		assertThat(kVv2.getConfig()).containsEntry("default_lease_ttl", 3600);
		assertThat(kVv2.getType()).isEqualTo("kv");
		assertThat(kVv2.getOptions()).containsEntry("version", "2");

		VaultVersionedKeyValueOperations versionedOperations = this.vaultOperations.opsForVersionedKeyValue("kVv2");

		versionedOperations.put("secret/mykey", Collections.singletonMap("key", "value"));
		assertThat(versionedOperations.get("secret/mykey").getRequiredData()).containsEntry("key", "value");
	}

	@Test
	void getAuthMountsShouldContainSecretBackend() {

		Map<String, VaultMount> mounts = this.adminOperations.getAuthMounts();

		assertThat(mounts).containsKey("token/");

		VaultMount secret = mounts.get("token/");
		assertThat(secret.getDescription()).isEqualTo("token based credentials");
		assertThat(secret.getType()).isEqualTo("token");
	}

	@Test
	void authMountShouldMountGenericSecret() {

		if (this.adminOperations.getAuthMounts().containsKey("other/")) {
			this.adminOperations.authUnmount("other");
		}

		VaultMount mount = VaultMount.builder().type("userpass").description("hello, world").build();

		this.adminOperations.authMount("other", mount);

		Map<String, VaultMount> mounts = this.adminOperations.getAuthMounts();

		assertThat(mounts).containsKey("other/");

		VaultMount secret = mounts.get("other/");
		assertThat(secret.getDescription()).isEqualTo(mount.getDescription());
		assertThat(secret.getType()).isEqualTo("userpass");
	}

	@Test
	@RequiresVaultVersion("0.6.1")
	void shouldEnumeratePolicyNames() {

		List<String> policyNames = this.adminOperations.getPolicyNames();

		assertThat(policyNames).contains("root", "default");
	}

	@Test
	@RequiresVaultVersion("0.6.1")
	void shouldReadRootPolicy() {

		Policy root = this.adminOperations.getPolicy("root");

		assertThat(root).isEqualTo(Policy.empty());
	}

	@Test
	@RequiresVaultVersion("0.6.1")
	void shouldReadAbsentRootPolicy() {

		Policy root = this.adminOperations.getPolicy("absent-policy");

		assertThat(root).isNull();
	}

	@Test
	@RequiresVaultVersion("0.6.1")
	void shouldReadDefaultPolicy() {

		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> this.adminOperations.getPolicy("default"));
	}

	@Test
	@RequiresVaultVersion("0.7.0")
	void shouldCreatePolicy() {

		Rule rule = Rule.builder().path("foo").capabilities(READ, UPDATE).minWrappingTtl(Duration.ofSeconds(100))
				.maxWrappingTtl(Duration.ofHours(2)).build();

		this.adminOperations.createOrUpdatePolicy("foo", Policy.of(rule));

		assertThat(this.adminOperations.getPolicyNames()).contains("foo");

		Policy loaded = this.adminOperations.getPolicy("foo");
		assertThat(loaded.getRules()).contains(rule);
	}

	@Test
	@RequiresVaultVersion("0.6.0")
	void shouldDeletePolicy() {

		Rule rule = Rule.builder().path("foo").capabilities(READ).build();

		this.adminOperations.createOrUpdatePolicy("foo", Policy.of(rule));

		this.adminOperations.deletePolicy("foo");

		assertThat(this.adminOperations.getPolicyNames()).doesNotContain("foo");
	}

	@Test
	@RequiresVaultVersion("0.6.1")
	void shouldReportHealth() {

		VaultHealth health = this.adminOperations.health();

		assertThat(health.isInitialized()).isTrue();
		assertThat(health.isSealed()).isFalse();
		assertThat(health.isPerformanceStandby()).isFalse();
		assertThat(health.isRecoveryReplicationSecondary()).isFalse();
		assertThat(health.isStandby()).isFalse();
	}

	@Test
	void isInitializedShouldReturnTrue() {
		assertThat(this.adminOperations.isInitialized()).isTrue();
	}

	@Test
	void getUnsealStatusShouldReturnStatus() {

		VaultUnsealStatus unsealStatus = this.adminOperations.getUnsealStatus();

		assertThat(unsealStatus.isSealed()).isFalse();
		assertThat(unsealStatus.getProgress()).isEqualTo(0);
	}

}
