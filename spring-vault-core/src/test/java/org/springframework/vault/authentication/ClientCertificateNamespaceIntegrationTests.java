/*
 * Copyright 2019-2020 the original author or authors.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.util.Files;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.vault.client.ClientHttpConnectorFactory;
import org.springframework.vault.client.ClientHttpRequestFactoryFactory;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.WebClientBuilder;
import org.springframework.vault.core.ReactiveVaultTemplate;
import org.springframework.vault.core.RestOperationsCallback;
import org.springframework.vault.core.VaultSysOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.Policy;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestRestTemplateFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.vault.util.Settings.findWorkDir;

/**
 * Integration tests for Vault using namespaces (Enterprise feature) with Client
 * Certificate authentication.
 *
 * @author Mark Paluch
 */
class ClientCertificateNamespaceIntegrationTests extends IntegrationTestSupport {

	static final Policy POLICY = Policy
			.of(Policy.Rule.builder().path("/*").capabilities(Policy.BuiltinCapabilities.READ,
					Policy.BuiltinCapabilities.CREATE, Policy.BuiltinCapabilities.UPDATE).build());

	@BeforeEach
	void before() {

		Assumptions.assumeTrue(prepare().getVersion().isEnterprise(), "Namespaces require enterprise version");

		List<String> namespaces = new ArrayList<>(Arrays.asList("dev/", "marketing/"));
		List<String> list = prepare().getVaultOperations().list("sys/namespaces");
		namespaces.removeAll(list);

		for (String namespace : namespaces) {
			prepare().getVaultOperations().write("sys/namespaces/" + namespace.replaceAll("/", ""));
		}

		RestTemplateBuilder devRestTemplate = RestTemplateBuilder.builder()
				.requestFactory(
						ClientHttpRequestFactoryFactory.create(new ClientOptions(), Settings.createSslConfiguration()))
				.endpoint(TestRestTemplateFactory.TEST_VAULT_ENDPOINT).customizers(restTemplate -> restTemplate
						.getInterceptors().add(VaultClients.createNamespaceInterceptor("dev")));

		VaultTemplate dev = new VaultTemplate(devRestTemplate,
				new SimpleSessionManager(new TokenAuthentication(Settings.token())));

		mountKv(dev, "dev-secrets");
		dev.opsForSys().createOrUpdatePolicy("relaxed", POLICY);

		if (!dev.opsForSys().getAuthMounts().containsKey("cert/")) {
			dev.opsForSys().authMount("cert", VaultMount.create("cert"));
		}

		dev.doWithSession((RestOperationsCallback<Object>) restOperations -> {

			File workDir = findWorkDir();

			String certificate = Files.contentOf(new File(workDir, "ca/certs/client.cert.pem"),
					StandardCharsets.US_ASCII);

			Map<String, String> role = new LinkedHashMap<>();
			role.put("token_policies", "relaxed");
			role.put("policies", "relaxed");
			role.put("certificate", certificate);

			return restOperations.postForEntity("auth/cert/certs/relaxed", role, Map.class);
		});
	}

	private void mountKv(VaultTemplate template, String path) {

		VaultSysOperations vaultSysOperations = template.opsForSys();

		Map<String, VaultMount> mounts = vaultSysOperations.getMounts();

		if (!mounts.containsKey(path + "/")) {
			vaultSysOperations.mount(path,
					VaultMount.builder().type("kv").options(Collections.singletonMap("version", "1")).build());
		}
	}

	@Test
	void shouldAuthenticateWithNamespace() {

		ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory.create(new ClientOptions(),
				ClientCertificateAuthenticationIntegrationTestBase.prepareCertAuthenticationMethod());

		RestTemplateBuilder builder = RestTemplateBuilder.builder()
				.endpoint(TestRestTemplateFactory.TEST_VAULT_ENDPOINT).requestFactory(clientHttpRequestFactory)
				.defaultHeader(VaultHttpHeaders.VAULT_NAMESPACE, "dev");

		RestTemplate forAuthentication = builder.build();

		ClientCertificateAuthentication authentication = new ClientCertificateAuthentication(forAuthentication);

		VaultTemplate dev = new VaultTemplate(builder, new SimpleSessionManager(authentication));

		dev.write("dev-secrets/my-secret", Collections.singletonMap("key", "dev"));

		assertThat(dev.read("dev-secrets/my-secret").getRequiredData()).containsEntry("key", "dev");
	}

	@Test
	void shouldAuthenticateReactiveWithNamespace() {

		ClientHttpConnector connector = ClientHttpConnectorFactory.create(new ClientOptions(),
				ClientCertificateAuthenticationIntegrationTestBase.prepareCertAuthenticationMethod());

		WebClientBuilder builder = WebClientBuilder.builder().endpoint(TestRestTemplateFactory.TEST_VAULT_ENDPOINT)
				.httpConnector(connector).defaultHeader(VaultHttpHeaders.VAULT_NAMESPACE, "dev");

		WebClient forAuthentication = builder.build();

		AuthenticationSteps steps = ClientCertificateAuthentication.createAuthenticationSteps();

		AuthenticationStepsOperator operator = new AuthenticationStepsOperator(steps, forAuthentication);

		ReactiveVaultTemplate dev = new ReactiveVaultTemplate(builder, operator);

		dev.write("dev-secrets/my-secret", Collections.singletonMap("key", "dev")).as(StepVerifier::create)
				.verifyComplete();

		dev.read("dev-secrets/my-secret").as(StepVerifier::create).consumeNextWith(actual -> {

			assertThat(actual.getRequiredData()).containsEntry("key", "dev");
		}).verifyComplete();
	}

}
