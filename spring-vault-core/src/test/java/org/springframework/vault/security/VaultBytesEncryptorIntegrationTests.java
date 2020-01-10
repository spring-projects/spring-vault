/*
 * Copyright 2017-2020 the original author or authors.
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
package org.springframework.vault.security;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.VaultTransitKeyConfiguration;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Version;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link VaultBytesEncryptor}.
 *
 * @author Mark Paluch
 */
public class VaultBytesEncryptorIntegrationTests extends IntegrationTestSupport {

	static final String KEY_NAME = "security-encryptor";

	private VaultTransitOperations transit;

	private Version vaultVersion;

	@Before
	public void before() {

		transit = prepare().getVaultOperations().opsForTransit();
		vaultVersion = prepare().getVersion();

		if (!prepare().hasSecret("transit")) {
			prepare().mountSecret("transit");
		}

		removeKeys();
		transit.createKey(KEY_NAME);
	}

	private void removeKeys() {

		if (vaultVersion.isGreaterThanOrEqualTo(Version.parse("0.6.4"))) {
			List<String> keys = transit.getKeys();
			keys.forEach(this::deleteKey);
		}
		else {
			deleteKey(KEY_NAME);
		}
	}

	private void deleteKey(String keyName) {

		try {
			transit.configureKey(keyName, VaultTransitKeyConfiguration.builder()
					.deletionAllowed(true).build());
		}
		catch (Exception e) {
		}

		try {
			transit.deleteKey(keyName);
		}
		catch (Exception e) {
		}
	}

	@Test
	public void shouldEncryptAndDecrypt() {

		VaultBytesEncryptor encryptor = new VaultBytesEncryptor(transit, KEY_NAME);

		byte[] plaintext = "foo-bar+ü¿ß~€¢".getBytes();
		byte[] ciphertext = encryptor.encrypt(plaintext);

		byte[] result = encryptor.decrypt(ciphertext);

		assertThat(ciphertext).isNotEqualTo(plaintext).startsWith("vault:v1".getBytes());
		assertThat(result).isEqualTo(plaintext);
	}
}
