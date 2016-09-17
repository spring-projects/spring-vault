/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.vault.core;

import static org.assertj.core.api.Assertions.*;

import java.util.Collections;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultUnsealStatus;
import org.springframework.vault.util.IntegrationTestSupport;

/**
 * Integration tests for {@link VaultSysTemplate} through {@link VaultSysOperations}.
 *
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
public class VaultSysTemplateIntegrationTests extends IntegrationTestSupport {

	@Autowired private VaultOperations vaultOperations;

	private VaultSysOperations adminOperations;

	@Before
	public void before() throws Exception {
		adminOperations = vaultOperations.opsForSys();
	}

	@Test
	public void getMountsShouldContainSecretBackend() {

		Map<String, VaultMount> mounts = adminOperations.getMounts();

		assertThat(mounts).containsKey("secret/");

		VaultMount secret = mounts.get("secret/");
		assertThat(secret.getDescription()).isEqualTo("generic secret storage");
		assertThat(secret.getType()).isEqualTo("generic");
	}

	@Test
	public void mountShouldMountGenericSecret() {

		if (adminOperations.getMounts().containsKey("other/")) {
			adminOperations.unmount("other");
		}

		VaultMount mount = VaultMount.builder().type("generic") //
				.config(Collections.singletonMap("default_lease_ttl", (Object) "1h")) //
				.description("hello, world").build();

		adminOperations.mount("other", mount);

		Map<String, VaultMount> mounts = adminOperations.getMounts();

		assertThat(mounts).containsKey("other/");

		VaultMount secret = mounts.get("other/");
		assertThat(secret.getDescription()).isEqualTo(mount.getDescription());
		assertThat(secret.getConfig()).containsEntry("default_lease_ttl", 3600);
		assertThat(secret.getType()).isEqualTo("generic");
	}

	@Test
	public void getAuthMountsShouldContainSecretBackend() {

		Map<String, VaultMount> mounts = adminOperations.getAuthMounts();

		assertThat(mounts).containsKey("token/");

		VaultMount secret = mounts.get("token/");
		assertThat(secret.getDescription()).isEqualTo("token based credentials");
		assertThat(secret.getType()).isEqualTo("token");
	}

	@Test
	public void authMountShouldMountGenericSecret() {

		if (adminOperations.getAuthMounts().containsKey("other/")) {
			adminOperations.authUnmount("other");
		}

		VaultMount mount = VaultMount.builder().type("userpass").description("hello, world").build();

		adminOperations.authMount("other", mount);

		Map<String, VaultMount> mounts = adminOperations.getAuthMounts();

		assertThat(mounts).containsKey("other/");

		VaultMount secret = mounts.get("other/");
		assertThat(secret.getDescription()).isEqualTo(mount.getDescription());
		assertThat(secret.getType()).isEqualTo("userpass");
	}

	@Test
	public void isInitializedShouldReturnTrue() {
		assertThat(adminOperations.isInitialized()).isTrue();
	}

	@Test
	public void getUnsealStatusShouldReturnStatus() {

		VaultUnsealStatus unsealStatus = adminOperations.getUnsealStatus();
		assertThat(unsealStatus.isSealed()).isFalse();
		assertThat(unsealStatus.getProgress()).isEqualTo(0);
	}
}
