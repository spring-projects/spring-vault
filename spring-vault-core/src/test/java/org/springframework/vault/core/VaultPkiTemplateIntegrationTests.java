/*
 * Copyright 2016-2018 the original author or authors.
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

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.util.Files;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.CertificateBundle;
import org.springframework.vault.support.VaultCertificateRequest;
import org.springframework.vault.support.VaultCertificateResponse;
import org.springframework.vault.util.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.vault.util.Settings.findWorkDir;

/**
 * Integration tests for {@link VaultPkiTemplate} through {@link VaultPkiOperations}.
 *
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
public class VaultPkiTemplateIntegrationTests extends IntegrationTestSupport {

	@Autowired
	private VaultOperations vaultOperations;

	private VaultPkiOperations pkiOperations;

	@Before
	public void before() throws Exception {

		pkiOperations = vaultOperations.opsForPki();

		if (!prepare().hasSecret("pki")) {
			prepare().mountSecret("pki");
		}

		File workDir = findWorkDir(new File(System.getProperty("user.dir")));
		String cert = Files.contentOf(
				new File(workDir, "ca/certs/intermediate.cert.pem"), "US-ASCII");
		String key = Files.contentOf(new File(workDir,
				"ca/private/intermediate.decrypted.key.pem"), "US-ASCII");

		Map<String, String> pembundle = Collections
				.singletonMap("pem_bundle", cert + key);

		vaultOperations.write("pki/config/ca", pembundle);

		Map<String, String> role = new HashMap<String, String>();
		role.put("allowed_domains", "localhost,example.com");
		role.put("allow_subdomains", "true");
		role.put("allow_localhost", "true");
		role.put("allow_ip_sans", "true");
		role.put("max_ttl", "72h");

		vaultOperations.write("pki/roles/testrole", role);
	}

	@Test
	public void issueCertificateShouldCreateCertificate() {

		VaultCertificateRequest request = VaultCertificateRequest
				.create("hello.example.com");

		VaultCertificateResponse certificateResponse = pkiOperations.issueCertificate(
				"testrole", request);

		CertificateBundle data = certificateResponse.getData();

		assertThat(data.getPrivateKey()).isNotEmpty();
		assertThat(data.getCertificate()).isNotEmpty();
		assertThat(data.getIssuingCaCertificate()).isNotEmpty();
		assertThat(data.getSerialNumber()).isNotEmpty();
		assertThat(data.getX509Certificate().getSubjectX500Principal().getName())
				.isEqualTo("CN=hello.example.com");
	}

	@Test(expected = VaultException.class)
	public void issueCertificateFail() {

		VaultCertificateRequest request = VaultCertificateRequest.create("not.supported");

		pkiOperations.issueCertificate("testrole", request);
	}
}
