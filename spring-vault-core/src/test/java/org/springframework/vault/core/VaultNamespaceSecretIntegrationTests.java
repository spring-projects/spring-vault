/*
 * Copyright 2019-2024 the original author or authors.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.SimpleSessionManager;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.ClientHttpConnectorFactory;
import org.springframework.vault.client.ClientHttpRequestFactoryFactory;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.VaultEndpointProvider;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.WebClientBuilder;
import org.springframework.vault.config.AbstractVaultConfiguration;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.Policy;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.VaultTokenRequest;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestRestTemplateFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Vault namespaces (Vault Enterprise feature).
 *
 * @author Mark Paluch
 */
class VaultNamespaceSecretIntegrationTests extends IntegrationTestSupport {

	static final Policy POLICY = Policy.of(Policy.Rule.builder()
		.path("/*")
		.capabilities(Policy.BuiltinCapabilities.READ, Policy.BuiltinCapabilities.CREATE,
				Policy.BuiltinCapabilities.UPDATE)
		.build());

	RestTemplateBuilder devRestTemplate;

	RestTemplateBuilder marketingRestTemplate;

	WebClientBuilder marketingWebClientBuilder = WebClientBuilder.builder()
		.httpConnector(ClientHttpConnectorFactory.create(new ClientOptions(), Settings.createSslConfiguration()))
		.endpoint(TestRestTemplateFactory.TEST_VAULT_ENDPOINT)
		.defaultHeader(VaultHttpHeaders.VAULT_NAMESPACE, "marketing");

	String devToken;

	String marketingToken;

	@BeforeEach
	void before() {

		Assumptions.assumeTrue(prepare().getVersion().isEnterprise(), "Namespaces require enterprise version");

		List<String> namespaces = new ArrayList<>(Arrays.asList("dev/", "marketing/"));
		List<String> list = prepare().getVaultOperations().list("sys/namespaces");
		namespaces.removeAll(list);

		for (String namespace : namespaces) {
			prepare().getVaultOperations().write("sys/namespaces/" + namespace.replaceAll("/", ""));
		}

		this.devRestTemplate = RestTemplateBuilder.builder()
			.requestFactory(
					ClientHttpRequestFactoryFactory.create(new ClientOptions(), Settings.createSslConfiguration()))
			.endpoint(TestRestTemplateFactory.TEST_VAULT_ENDPOINT)
			.customizers(
					restTemplate -> restTemplate.getInterceptors().add(VaultClients.createNamespaceInterceptor("dev")));

		this.marketingRestTemplate = RestTemplateBuilder.builder()
			.requestFactory(
					ClientHttpRequestFactoryFactory.create(new ClientOptions(), Settings.createSslConfiguration()))
			.endpoint(TestRestTemplateFactory.TEST_VAULT_ENDPOINT)
			.defaultHeader(VaultHttpHeaders.VAULT_NAMESPACE, "marketing");

		VaultTemplate dev = new VaultTemplate(this.devRestTemplate,
				new SimpleSessionManager(new TokenAuthentication(Settings.token())));

		mountKv(dev, "dev-secrets");
		dev.opsForSys().createOrUpdatePolicy("relaxed", POLICY);
		this.devToken = dev.opsForToken()
			.create(VaultTokenRequest.builder().withPolicy("relaxed").build())
			.getToken()
			.getToken();

		VaultTemplate marketing = new VaultTemplate(this.marketingRestTemplate,
				new SimpleSessionManager(new TokenAuthentication(Settings.token())));

		mountKv(marketing, "marketing-secrets");
		marketing.opsForSys().createOrUpdatePolicy("relaxed", POLICY);
		this.marketingToken = marketing.opsForToken()
			.create(VaultTokenRequest.builder().withPolicy("relaxed").build())
			.getToken()
			.getToken();
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
	void namespaceSecretsAreIsolated() {

		VaultTemplate dev = new VaultTemplate(this.devRestTemplate,
				new SimpleSessionManager(new TokenAuthentication(this.devToken)));
		VaultTemplate marketing = new VaultTemplate(this.marketingRestTemplate,
				new SimpleSessionManager(new TokenAuthentication(this.marketingToken)));

		dev.write("dev-secrets/my-secret", Collections.singletonMap("key", "dev"));
		marketing.write("marketing-secrets/my-secret", Collections.singletonMap("key", "marketing"));

		assertThat(dev.read("marketing-secrets/my-secret")).isNull();
		assertThat(marketing.read("marketing-secrets/my-secret")).isNotNull();
	}

	@Test
	void namespacesSupportedThroughConfiguration() {

		namespaceSecretsAreIsolated();

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				NamespaceConfiguration.class);
		VaultOperations operations = context.getBean(VaultOperations.class);
		assertThat(operations.read("marketing-secrets/my-secret")).isNotNull();

		context.stop();
	}

	@Test
	void reactiveNamespaceSecretsAreIsolated() {

		VaultTemplate marketing = new VaultTemplate(this.marketingRestTemplate,
				new SimpleSessionManager(new TokenAuthentication(this.marketingToken)));

		ReactiveVaultTemplate reactiveMarketing = new ReactiveVaultTemplate(this.marketingWebClientBuilder,
				() -> Mono.just(VaultToken.of(this.marketingToken)));

		marketing.write("marketing-secrets/my-secret", Collections.singletonMap("key", "marketing"));

		assertThat(marketing.read("marketing-secrets/my-secret")).isNotNull();

		reactiveMarketing.read("marketing-secrets/my-secret").as(StepVerifier::create).consumeNextWith(actual -> {
			assertThat(actual.getRequiredData()).containsEntry("key", "marketing");
		}).verifyComplete();
	}

	@Test
	void shouldReportInitialized() {

		VaultTemplate marketing = new VaultTemplate(this.marketingRestTemplate,
				new SimpleSessionManager(new TokenAuthentication(this.marketingToken)));

		assertThat(marketing.opsForSys().isInitialized()).isTrue();
	}

	@Test
	void shouldReportHealth() {

		VaultTemplate marketing = new VaultTemplate(this.marketingRestTemplate,
				new SimpleSessionManager(new TokenAuthentication(this.marketingToken)));

		assertThat(marketing.opsForSys().health().isInitialized()).isTrue();
	}

	@Test
	void shouldReportReactiveInitialized() {

		ReactiveVaultTemplate reactiveMarketing = new ReactiveVaultTemplate(this.marketingWebClientBuilder,
				() -> Mono.just(VaultToken.of(this.marketingToken)));

		reactiveMarketing.doWithSession(webClient -> {
			return webClient.get()
				.uri("sys/init")
				.header(VaultHttpHeaders.VAULT_NAMESPACE, "")
				.exchangeToMono(it -> it.bodyToMono(Map.class));
		})
			.as(StepVerifier::create)
			.assertNext(actual -> assertThat(actual).containsEntry("initialized", true))
			.verifyComplete();
	}

	@Configuration
	static class NamespaceConfiguration extends AbstractVaultConfiguration {

		@Override
		public VaultEndpoint vaultEndpoint() {
			return TestRestTemplateFactory.TEST_VAULT_ENDPOINT;
		}

		@Override
		public ClientAuthentication clientAuthentication() {
			return new TokenAuthentication(Settings.token());
		}

		@Override
		public SslConfiguration sslConfiguration() {
			return Settings.createSslConfiguration();
		}

		@Override
		protected RestTemplateBuilder restTemplateBuilder(VaultEndpointProvider endpointProvider,
				ClientHttpRequestFactory requestFactory) {
			return super.restTemplateBuilder(endpointProvider, requestFactory)
				.defaultHeader(VaultHttpHeaders.VAULT_NAMESPACE, "marketing");
		}

	}

}
