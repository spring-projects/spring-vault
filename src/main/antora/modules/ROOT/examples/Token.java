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

import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultTokenOperations;
import org.springframework.vault.support.CertificateBundle;
import org.springframework.vault.support.VaultCertificateResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.VaultTokenRequest;
import org.springframework.vault.support.VaultTokenResponse;

/**
 * @author Mark Paluch
 */
//@formatter:off
public class Token {

	void tokenApi() {

		// tag::tokenApi[]
		VaultOperations operations = new VaultTemplate(new VaultEndpoint());
		VaultTokenOperations tokenOperations = operations.opsForToken();

		VaultTokenResponse tokenResponse = tokenOperations.create();                          // <1>
		VaultToken justAToken = tokenResponse.getToken();

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder().withPolicy("policy-for-myapp")
											.displayName("Access tokens for myapp")
											.renewable()
											.ttl(Duration.ofHours(1))
											.build();

		VaultTokenResponse appTokenResponse = tokenOperations.create(tokenRequest);          // <2>
		VaultToken appToken = appTokenResponse.getToken();

		tokenOperations.renew(appToken);                                                     // <3>

		tokenOperations.revoke(appToken);                                                    // <4>

		// end::tokenApi[]
	}

}
