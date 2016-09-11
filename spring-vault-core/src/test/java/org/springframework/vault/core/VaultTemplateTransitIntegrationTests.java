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

import org.apache.commons.codec.binary.Base64;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.util.IntegrationTestSupport;

/**
 * Integration tests for {@link VaultTemplate} using the {@code transit} backend.
 * 
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
public class VaultTemplateTransitIntegrationTests extends IntegrationTestSupport {

	@Autowired private VaultOperations vaultOperations;

	@Before
	public void before() throws Exception {

		VaultSysOperations adminOperations = vaultOperations.opsForSys();

		if (!adminOperations.getMounts().containsKey("transit/")) {
			adminOperations.mount("transit", new VaultMount("transit"));

			vaultOperations.write("transit/keys/mykey", null);
			vaultOperations.write("transit/keys/derived", Collections.singletonMap("derived", true));
		}
	}

	@Test
	public void shouldEncrypt() throws Exception {

		VaultResponse response = vaultOperations.write("transit/encrypt/mykey",
				Collections.singletonMap("plaintext", Base64.encodeBase64String("that message is secret".getBytes())));

		assertThat((String) response.getData().get("ciphertext")).isNotEmpty();
	}

	@Test
	public void shouldEncryptAndDecrypt() throws Exception {

		VaultResponse response = vaultOperations.write("transit/encrypt/mykey",
				Collections.singletonMap("plaintext", Base64.encodeBase64String("that message is secret".getBytes())));

		VaultResponse decrypted = vaultOperations.write("transit/decrypt/mykey",
				Collections.singletonMap("ciphertext", response.getData().get("ciphertext")));

		assertThat((String) decrypted.getData().get("plaintext")).isEqualTo(Base64.encodeBase64String("that message is secret".getBytes()));
	}
}
