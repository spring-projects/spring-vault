/*
 * Copyright 2016-2017 the original author or authors.
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
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultExportKeyTypes;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultTransitKey;
import org.springframework.vault.support.VaultTransitKeyConfiguration;
import org.springframework.vault.support.VaultTransitKeyCreationRequest;
import org.springframework.vault.support.VaultTransitKeyExport;
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

	@Before
	public void before() throws Exception {

		VaultSysOperations adminOperations = vaultOperations.opsForSys();

		if (!adminOperations.getMounts().containsKey("transit/")) {
			adminOperations.mount("transit", VaultMount.create("transit"));
		}

		removeKeys();

		vaultOperations.write("transit/keys/mykey", null);
		vaultOperations.write("transit/keys/derived",
				Collections.singletonMap("derived", true));
		vaultOperations.write("transit/keys/export",
				Collections.singletonMap("exportable", true));
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

		if (prepare().getVersion().isGreaterThanOrEqualTo(Version.parse("0.6.4"))) {
			List<String> keys = vaultOperations.opsForTransit().getKeys();
			for (String keyName : keys) {
				deleteKey(keyName);
			}
		}
		else {
			deleteKey("mykey");
			deleteKey("derived");
			deleteKey("export");
		}
	}

	@Test
	public void shouldEncrypt() throws Exception {

		VaultResponse response = vaultOperations.write(
				"transit/encrypt/mykey",
				Collections.singletonMap("plaintext",
						Base64.encodeBase64String("that message is secret".getBytes())));

		assertThat((String) response.getData().get("ciphertext")).isNotEmpty();
	}

	@Test
	public void shouldEncryptAndDecrypt() throws Exception {

		VaultResponse response = vaultOperations.write(
				"transit/encrypt/mykey",
				Collections.singletonMap("plaintext",
						Base64.encodeBase64String("that message is secret".getBytes())));

		VaultResponse decrypted = vaultOperations.write(
				"transit/decrypt/mykey",
				Collections.singletonMap("ciphertext",
						response.getData().get("ciphertext")));

		assertThat((String) decrypted.getData().get("plaintext")).isEqualTo(
				Base64.encodeBase64String("that message is secret".getBytes()));
	}

	@Test
	public void shouldCreateNewExportableKey() throws Exception {

		VaultTransitOperations vaultTransitOperations = vaultOperations
				.opsForTransit();
		VaultTransitKeyCreationRequest vaultTransitKeyCreationRequest = VaultTransitKeyCreationRequest
				.builder().exportable(true).derived(true).build();

		vaultTransitOperations.createKey("export-test", vaultTransitKeyCreationRequest);

		VaultTransitKey vaultTransitKey = vaultTransitOperations
				.getKey("export-test");

		assertThat(vaultTransitKey.getName()).isEqualTo("export-test");
		assertThat(vaultTransitKey.isExportable()).isTrue();

	}

	@Test
	public void shouldNotCreateExportableKeyPerDefault() throws Exception {

		VaultTransitOperations vaultTransitOperations = vaultOperations
				.opsForTransit();

		vaultTransitOperations.createKey("no-export");

		VaultTransitKey vaultTransitKey = vaultTransitOperations
				.getKey("no-export");

		assertThat(vaultTransitKey.getName()).isEqualTo("no-export");
		assertThat(vaultTransitKey.isExportable()).isFalse();

	}

	@Test
	public void shouldExportEncryptionKey() throws Exception {

		VaultTransitOperations vaultTransitOperations = vaultOperations
				.opsForTransit();

		VaultTransitKeyExport vaultTransitKeyExport = vaultTransitOperations
				.exportKey("export", VaultExportKeyTypes.ENCRYPTION_KEY);

		assertThat(vaultTransitKeyExport.getName()).isEqualTo("export");
		assertThat(vaultTransitKeyExport.getKeys()).isNotEmpty();
		assertThat(vaultTransitKeyExport.getKeys().get("1")).isNotBlank();

	}

	@Test(expected = VaultException.class)
	public void shouldNotExportSigningKey() throws Exception {

		VaultTransitOperations vaultTransitOperations = vaultOperations
				.opsForTransit();

		VaultTransitKeyExport vaultTransitKeyExport = vaultTransitOperations
				.exportKey("export", VaultExportKeyTypes.SIGNING_KEY);

	}

	@Test
	public void shouldExportHmacKey() throws Exception {

		VaultTransitOperations vaultTransitOperations = vaultOperations
				.opsForTransit();

		VaultTransitKeyExport vaultTransitKeyExport = vaultTransitOperations
				.exportKey("export", VaultExportKeyTypes.HMAC_KEY);

		assertThat(vaultTransitKeyExport.getName()).isEqualTo("export");
		assertThat(vaultTransitKeyExport.getKeys()).isNotEmpty();
		assertThat(vaultTransitKeyExport.getKeys().get("1")).isNotBlank();

	}

}
