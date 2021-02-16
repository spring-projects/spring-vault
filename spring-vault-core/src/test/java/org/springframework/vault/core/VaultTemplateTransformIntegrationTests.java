/*
 * Copyright 2020-2021 the original author or authors.
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.Base64Utils;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.RequiresVaultVersion;
import org.springframework.vault.util.Version;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link VaultTemplate} using the {@code transform} backend.
 *
 * @author Lauren Voswinkel
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
@RequiresVaultVersion("1.4.0")
class VaultTemplateTransformIntegrationTests extends IntegrationTestSupport {

	@Autowired
	VaultOperations vaultOperations;

	Version vaultVersion;

	@BeforeEach
	void before() {

		Assumptions.assumeTrue(prepare().getVersion().isEnterprise(),
				"Transform Secrets Engine requires enterprise version");

		VaultSysOperations adminOperations = this.vaultOperations.opsForSys();

		this.vaultVersion = prepare().getVersion();

		if (!adminOperations.getMounts().containsKey("transform/")) {
			adminOperations.mount("transform", VaultMount.create("transform"));
		}

		// Write a transformation/role
		this.vaultOperations.write("transform/transformation/myssn",
				"{\"type\": \"fpe\", \"template\": \"builtin/socialsecuritynumber\", \"allowed_roles\": [\"myrole\"]}");
		this.vaultOperations.write("transform/role/myrole", "{\"transformations\": [\"myssn\"]}");
	}

	@AfterEach
	void tearDown() {
		this.vaultOperations.delete("transform/role/myrole");
		this.vaultOperations.delete("transform/transformation/myssn");
	}

	@Test
	void shouldEncode() {

		VaultResponse response = this.vaultOperations.write("transform/encode/myrole", String.format(
				"{\"value\": \"123-45-6789\", \"tweak\": \"%s\"}", Base64Utils.encodeToString("somenum".getBytes())));

		assertThat((String) response.getRequiredData().get("encoded_value")).isNotEmpty();
	}

	@Test
	void shouldEncodeAndDecode() {

		String value = "123-45-6789";
		VaultResponse response = this.vaultOperations.write("transform/encode/myrole", String.format(
				"{\"value\": \"%s\", \"tweak\": \"%s\"}", value, Base64Utils.encodeToString("somenum".getBytes())));

		String encoded = (String) response.getRequiredData().get("encoded_value");
		VaultResponse decoded = this.vaultOperations.write("transform/decode/myrole", String.format(
				"{\"value\": \"%s\", \"tweak\": \"%s\"}", encoded, Base64Utils.encodeToString("somenum".getBytes())));

		assertThat((String) decoded.getRequiredData().get("decoded_value")).isEqualTo(value);
	}

}
