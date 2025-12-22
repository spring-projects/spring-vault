/*
 * Copyright 2016-2025 the original author or authors.
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

import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultTransitKeyConfiguration;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Version;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link VaultTemplate} using the {@code transit} secrets
 * engine.
 *
 * @author Mark Paluch
 * @author Sven Schürmann
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
class VaultTemplateTransitIntegrationTests extends IntegrationTestSupport {

	@Autowired
	VaultOperations vaultOperations;

	Version vaultVersion;

	@BeforeEach
	void before() {

		VaultSysOperations adminOperations = this.vaultOperations.opsForSys();

		this.vaultVersion = prepare().getVersion();

		if (!adminOperations.getMounts().containsKey("transit/")) {
			adminOperations.mount("transit", VaultMount.create("transit"));
		}

		removeKeys();

		this.vaultOperations.write("transit/keys/mykey", null);
	}

	@AfterEach
	void tearDown() {
		removeKeys();
	}

	private void deleteKey(String keyName) {

		try {
			this.vaultOperations.opsForTransit()
					.configureKey(keyName, VaultTransitKeyConfiguration.builder().deletionAllowed(true).build());
		} catch (Exception e) {
		}

		try {
			this.vaultOperations.opsForTransit().deleteKey(keyName);
		} catch (Exception e) {
		}
	}

	private void removeKeys() {

		if (this.vaultVersion.isGreaterThanOrEqualTo(Version.parse("0.6.4"))) {
			List<String> keys = this.vaultOperations.opsForTransit().getKeys();
			keys.forEach(this::deleteKey);
		} else {
			deleteKey("mykey");
		}
	}

	@Test
	void shouldEncrypt() {

		VaultResponse response = this.vaultOperations.write("transit/encrypt/mykey", Collections
				.singletonMap("plaintext", Base64.getEncoder().encodeToString("that message is secret".getBytes())));

		assertThat((String) response.getRequiredData().get("ciphertext")).isNotEmpty();
	}

	@Test
	void shouldEncryptAndDecrypt() {

		VaultResponse response = this.vaultOperations.write("transit/encrypt/mykey", Collections
				.singletonMap("plaintext", Base64.getEncoder().encodeToString("that message is secret".getBytes())));

		VaultResponse decrypted = this.vaultOperations.write("transit/decrypt/mykey",
				Collections.singletonMap("ciphertext", response.getRequiredData().get("ciphertext")));

		assertThat((String) decrypted.getRequiredData().get("plaintext"))
				.isEqualTo(Base64.getEncoder().encodeToString("that message is secret".getBytes()));
	}

}
