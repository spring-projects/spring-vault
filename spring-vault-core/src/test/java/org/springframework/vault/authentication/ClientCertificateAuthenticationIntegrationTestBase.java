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

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.vault.util.Settings.createSslConfiguration;
import static org.springframework.vault.util.Settings.findWorkDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ListAssert;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.core.RestOperationsCallback;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.*;
import org.springframework.vault.support.SslConfiguration.KeyStoreConfiguration;
import org.springframework.vault.util.IntegrationTestSupport;

/**
 * Integration test base class for {@link ClientCertificateAuthentication} tests.
 *
 * @author Mark Paluch
 */
public abstract class ClientCertificateAuthenticationIntegrationTestBase extends IntegrationTestSupport {

	static final Policy DEFAULT_POLICY = Policy.of(Policy.Rule.builder()
		.path("/default/*")
		.capabilities(Policy.BuiltinCapabilities.READ, Policy.BuiltinCapabilities.CREATE,
				Policy.BuiltinCapabilities.UPDATE)
		.build());

	static final Policy ALTERNATE_POLICY = Policy
			.of(Policy.Rule.builder().path("/alternate/*").capabilities(Policy.BuiltinCapabilities.READ,
					Policy.BuiltinCapabilities.CREATE, Policy.BuiltinCapabilities.UPDATE).build());

	VaultOperations vaultOperations;

	@BeforeEach
	public void before() {

		if (!prepare().hasAuth("cert")) {
			prepare().mountAuth("cert");
		}

		vaultOperations = prepare().getVaultOperations();

		vaultOperations.opsForSys().createOrUpdatePolicy("cert-auth1", DEFAULT_POLICY);
		vaultOperations.opsForSys().createOrUpdatePolicy("cert-auth2", ALTERNATE_POLICY);

		vaultOperations.doWithSession((RestOperationsCallback<Object>) restOperations -> {
			File workDir = findWorkDir();

			String certificate = Files.contentOf(new File(workDir, "ca/certs/client.cert.pem"),
					StandardCharsets.US_ASCII);

			Map<String, Object> role = new LinkedHashMap<>();
			role.put("token_policies", "cert-auth1");
			role.put("certificate", certificate);

			restOperations.postForEntity("auth/cert/certs/my-default-role", role, Map.class);

			role.put("token_policies", "cert-auth2");
			restOperations.postForEntity("auth/cert/certs/my-alternate-role", role, Map.class);
			return true;
		});
	}

	ListAssert<String> assertThatPolicies(final VaultToken token) {
		return assertThat(lookupSelf(token).getBody()).isNotNull()
				.extracting("data", as(InstanceOfAssertFactories.map(String.class, Object.class))).isNotNull()
				.extracting("policies", as(InstanceOfAssertFactories.list(String.class))).isNotNull();
	}

	ResponseEntity<Map<String, Object>> lookupSelf(final VaultToken token) {

		return vaultOperations.doWithVault(restOperations -> {
			HttpHeaders headers = new HttpHeaders();
			headers.add(VaultHttpHeaders.VAULT_TOKEN, token.getToken());

			return restOperations.exchange("auth/token/lookup-self", HttpMethod.GET, new HttpEntity<>(headers),
					new ParameterizedTypeReference<Map<String, Object>>() {
					});
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
