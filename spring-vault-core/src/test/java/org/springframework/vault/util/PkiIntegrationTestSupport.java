/*
 * Copyright 2026 the original author or authors.
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

package org.springframework.vault.util;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.util.Files;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.vault.core.VaultOperations;

/**
 * Support class for integration tests for using Vault's PKI secrets engine.
 *
 * @author Mark Paluch
 */
public abstract class PkiIntegrationTestSupport extends IntegrationTestSupport {

	protected VaultOperations template;

	protected enum KeyType {

		rsa(2048), ec(256);

		private final int bits;

		KeyType(int bits) {
			this.bits = bits;
		}

	}

	@BeforeEach
	protected void before() {

		if (!prepare().hasSecretsEngine("pki")) {
			prepare().mountSecretsEngine("pki");
		}

		template = prepare().getVaultOperations();

		setup();
	}

	private void setup() {
		File workDir = Settings.findWorkDir(new File(System.getProperty("user.dir")));
		String caCert = Files.contentOf(new File(workDir, "ca/certs/ca.cert.pem"), "US-ASCII");
		String cert = Files.contentOf(new File(workDir, "ca/certs/intermediate.cert.pem"), "US-ASCII");
		String key = Files.contentOf(new File(workDir, "ca/private/intermediate.decrypted.key.pem"), "US-ASCII");

		Map<String, String> pembundle = Collections.singletonMap("pem_bundle", cert + key + caCert);

		this.template.write("pki/config/ca", pembundle);

		Map<String, String> role = new HashMap<>();
		role.put("allowed_domains", "localhost,example.com");
		role.put("allow_subdomains", "true");
		role.put("allow_localhost", "true");
		role.put("allowed_user_ids", "humanoid,robot");
		role.put("allow_ip_sans", "true");
		role.put("ttl", "20d");
		role.put("max_ttl", "30d");

		this.template.write("pki/roles/testrole", role);

		for (KeyType value : KeyType.values()) {
			role.put("key_type", value.name());
			role.put("key_bits", "" + value.bits);
			this.template.write("pki/roles/testrole-" + value.name(), role);
		}
	}

}
