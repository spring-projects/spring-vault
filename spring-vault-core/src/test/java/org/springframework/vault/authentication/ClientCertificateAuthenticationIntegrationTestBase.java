/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.vault.authentication;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import org.assertj.core.util.Files;
import org.junit.Before;

import org.springframework.core.io.FileSystemResource;
import org.springframework.vault.core.RestOperationsCallback;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.SslConfiguration.KeyStoreConfiguration;
import org.springframework.vault.util.IntegrationTestSupport;

import static org.springframework.vault.util.Settings.createSslConfiguration;
import static org.springframework.vault.util.Settings.findWorkDir;

/**
 * Integration test base class for {@link ClientCertificateAuthentication} tests.
 *
 * @author Mark Paluch
 */
public abstract class ClientCertificateAuthenticationIntegrationTestBase extends
		IntegrationTestSupport {

	@Before
	public void before() {

		if (!prepare().hasAuth("cert")) {
			prepare().mountAuth("cert");
		}

		prepare().getVaultOperations().doWithSession(
				(RestOperationsCallback<Object>) restOperations -> {
					File workDir = findWorkDir();

					String certificate = Files.contentOf(new File(workDir,
							"ca/certs/client.cert.pem"), StandardCharsets.US_ASCII);

					return restOperations.postForEntity("auth/cert/certs/my-role",
							Collections.singletonMap("certificate", certificate),
							Map.class);
				});
	}

	static SslConfiguration prepareCertAuthenticationMethod() {

		SslConfiguration original = createSslConfiguration();

		return new SslConfiguration(KeyStoreConfiguration.of(new FileSystemResource(
				new File(findWorkDir(), "client-cert.jks")), "changeit".toCharArray()),
				original.getTrustStoreConfiguration());
	}
}
