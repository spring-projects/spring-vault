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

package org.springframework.vault.config;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.WebClientCustomizer;
import org.springframework.vault.client.WebClientFactory;
import org.springframework.vault.core.ReactiveVaultOperations;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.util.Settings;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Integration tests for {@link AbstractReactiveVaultConfiguration}.
 *
 * @author Mark Paluch
 */
class AbstractReactiveVaultConfigurationUnitTests {

	@Test
	void shouldApplyCustomizerToWebClientFactory() {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				WebClientCustomizerConfiguration.class);

		WebClientFactory factory = context.getBean(WebClientFactory.class);
		WebClient webClient = factory.create();

		webClient.get()
				.uri("/foo")
				.exchangeToMono(it -> it.bodyToMono(String.class))
				.as(StepVerifier::create)
				.verifyError(CustomizedSignal.class);
	}

	@Test
	void shouldApplyCustomizerToTemplate() {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				WebClientCustomizerConfiguration.class);

		ReactiveVaultOperations operations = context.getBean(ReactiveVaultOperations.class);

		operations.read("/foo").as(StepVerifier::create).verifyError(CustomizedSignal.class);
	}

	@Configuration(proxyBeanMethods = false)
	static class WebClientCustomizerConfiguration extends AbstractReactiveVaultConfiguration {

		@Override
		public VaultEndpoint vaultEndpoint() {
			return Settings.TEST_VAULT_ENDPOINT;
		}

		@Override
		public ClientAuthentication clientAuthentication() {
			return new TokenAuthentication(Settings.token());
		}

		@Override
		public SslConfiguration sslConfiguration() {
			return Settings.createSslConfiguration();
		}

		@Bean
		public WebClientCustomizer customizer() {
			return builder -> builder.exchangeFunction(request -> {
				throw new CustomizedSignal();
			});
		}

	}

	static class CustomizedSignal extends RuntimeException {

	}

}
