/*
 * Copyright 2020-2025 the original author or authors.
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

package org.springframework.vault.documentation;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.spec.KeySpec;
import java.time.Duration;
import java.util.Arrays;

import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultPkiOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.CertificateBundle;
import org.springframework.vault.support.VaultCertificateRequest;
import org.springframework.vault.support.VaultCertificateResponse;

/**
 * @author Mark Paluch
 */
//@formatter:off
public class PKI {

	void pkiApi() {

		// tag::pkiApi[]
		VaultOperations operations = new VaultTemplate(new VaultEndpoint());
		VaultPkiOperations pkiOperations = operations.opsForPki("pki");

		VaultCertificateRequest request = VaultCertificateRequest.builder()								// <1>
					.ttl(Duration.ofHours(48))
					.altNames(Arrays.asList("prod.dc-1.example.com", "prod.dc-2.example.com"))
					.withIpSubjectAltName("1.2.3.4")
					.commonName("hello.example.com")
					.build();

		VaultCertificateResponse response = pkiOperations.issueCertificate("production", request); 		// <2>
		CertificateBundle certificateBundle = response.getRequiredData();

		KeyStore keyStore = certificateBundle.createKeyStore("my-keystore");							// <3>

		KeySpec privateKey = certificateBundle.getPrivateKeySpec();										// <4>
		X509Certificate certificate = certificateBundle.getX509Certificate();
		X509Certificate caCertificate = certificateBundle.getX509IssuerCertificate();

		pkiOperations.revoke(certificateBundle.getSerialNumber());										// <5>
		// end::pkiApi[]
	}

}
