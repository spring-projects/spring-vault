/*
 * Copyright 2016-2019 the original author or authors.
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

import java.util.Collections;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultTransitKeyConfiguration;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Version;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link VaultTemplate} using the {@code transit} backend.
 *
 * @author Mark Paluch
 * @author Sven Schürmann
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
public class VaultTemplateTransitIntegrationTests extends IntegrationTestSupport {

	@Autowired
	private VaultOperations vaultOperations;

	private Version vaultVersion;

	@Before
	public void before() {

		VaultSysOperations adminOperations = vaultOperations.opsForSys();

		vaultVersion = prepare().getVersion();

		if (!adminOperations.getMounts().containsKey("transit/")) {
			adminOperations.mount("transit", VaultMount.create("transit"));
		}

		removeKeys();

		vaultOperations.write("transit/keys/mykey", null);
	}

	@After
	public void tearDown() {
		removeKeys();
	}

	private void deleteKey(String keyName) {

		try {
			vaultOperations.opsForTransit().configureKey(keyName,
					VaultTransitKeyConfiguration.builder().deletionAllowed(true).build());
		}
		catch (Exception e) {
		}

		try {
			vaultOperations.opsForTransit().deleteKey(keyName);
		}
		catch (Exception e) {
		}
	}

	private void removeKeys() {

		if (vaultVersion.isGreaterThanOrEqualTo(Version.parse("0.6.4"))) {
			List<String> keys = vaultOperations.opsForTransit().getKeys();
			keys.forEach(this::deleteKey);
		}
		else {
			deleteKey("mykey");
		}
	}

	@Test
	public void shouldEncrypt() {

		VaultResponse response = vaultOperations.write(
				"transit/encrypt/mykey",
				Collections.singletonMap("plaintext",
						Base64.encodeBase64String("that message is secret".getBytes())));

		assertThat((String) response.getRequiredData().get("ciphertext")).isNotEmpty();
	}

	@Test
	public void shouldEncryptAndDecrypt() {

		VaultResponse response = vaultOperations.write(
				"transit/encrypt/mykey",
				Collections.singletonMap("plaintext",
						Base64.encodeBase64String("that message is secret".getBytes())));

		VaultResponse decrypted = vaultOperations.write(
				"transit/decrypt/mykey",
				Collections.singletonMap("ciphertext",
						response.getRequiredData().get("ciphertext")));

		assertThat((String) decrypted.getRequiredData().get("plaintext")).isEqualTo(
				Base64.encodeBase64String("that message is secret".getBytes()));
	}
}
