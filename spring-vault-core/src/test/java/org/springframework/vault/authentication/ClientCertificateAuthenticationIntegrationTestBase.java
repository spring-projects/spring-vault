/*
 * Copyright 2017-2022 the original author or authors.
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
package org.springframework.vault.authentication;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.assertj.core.util.Files;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.core.io.FileSystemResource;
import org.springframework.vault.core.RestOperationsCallback;
import org.springframework.vault.support.Policy;
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
public abstract class ClientCertificateAuthenticationIntegrationTestBase extends IntegrationTestSupport {

	static final Policy POLICY = Policy
			.of(Policy.Rule.builder().path("/*").capabilities(Policy.BuiltinCapabilities.READ,
					Policy.BuiltinCapabilities.CREATE, Policy.BuiltinCapabilities.UPDATE).build());

	@BeforeEach
	public void before() {

		if (!prepare().hasAuth("cert")) {
			prepare().mountAuth("cert");
		}

		prepare().getVaultOperations().opsForSys().createOrUpdatePolicy("cert-auth", POLICY);

		prepare().getVaultOperations().doWithSession((RestOperationsCallback<Object>) restOperations -> {
			File workDir = findWorkDir();

			String certificate = Files.contentOf(new File(workDir, "ca/certs/client.cert.pem"),
					StandardCharsets.US_ASCII);

			Map<String, Object> role = new LinkedHashMap<>();
			role.put("token_policies", "cert-auth");
			role.put("certificate", certificate);

			return restOperations.postForEntity("auth/cert/certs/my-role", role, Map.class);
		});
	}

	static SslConfiguration prepareCertAuthenticationMethod() {
		return prepareCertAuthenticationMethod(SslConfiguration.KeyConfiguration.unconfigured());
	}

	static SslConfiguration prepareCertAuthenticationMethod(SslConfiguration.KeyConfiguration keyConfiguration) {

		SslConfiguration original = createSslConfiguration();

		return new SslConfiguration(KeyStoreConfiguration
				.of(new FileSystemResource(new File(findWorkDir(), "client-cert.jks")), "changeit".toCharArray()),
				keyConfiguration, original.getTrustStoreConfiguration());
	}

}
