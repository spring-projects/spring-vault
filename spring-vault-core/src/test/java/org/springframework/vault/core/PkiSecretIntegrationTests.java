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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.util.Files;
import org.junit.Before;
import org.junit.Test;

import org.springframework.vault.util.IntegrationTestSupport;

import static org.springframework.vault.util.Settings.findWorkDir;

/**
 * Integration test to request certificates from the Vault PKI backend.
 *
 * @author Mark Paluch
 */
public class PkiSecretIntegrationTests extends IntegrationTestSupport {

	/**
	 * Initialize the pki secret backend.
	 */
	@Before
	public void setUp() {

		if (!prepare().hasSecret("pki")) {
			prepare().mountSecret("pki");
		}

		File workDir = findWorkDir(new File(System.getProperty("user.dir")));

		String cert = Files.contentOf(
				new File(workDir, "ca/certs/intermediate.cert.pem"),
				StandardCharsets.US_ASCII);

		String key = Files.contentOf(new File(workDir,
				"ca/private/intermediate.decrypted.key.pem"), StandardCharsets.US_ASCII);

		Map<String, String> pembundle = Collections
				.singletonMap("pem_bundle", cert + key);

		VaultOperations vaultOperations = prepare().getVaultOperations();
		vaultOperations.write("pki/config/ca", pembundle);

		Map<String, String> role = new HashMap<String, String>();
		role.put("allowed_domains", "localhost,example.com");
		role.put("allow_subdomains", "true");
		role.put("allow_localhost", "true");
		role.put("allow_ip_sans", "true");
		role.put("max_ttl", "72h");

		vaultOperations.write("pki/roles/test", role);

	}

	@Test
	public void shouldCreateCertificateCorrectly() {

	}
}
