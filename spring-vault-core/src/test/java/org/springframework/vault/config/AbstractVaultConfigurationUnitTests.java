/*
 * Copyright 2020 the original author or authors.
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

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.RestTemplateCustomizer;
import org.springframework.vault.client.RestTemplateFactory;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestRestTemplateFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Integration tests for {@link AbstractVaultConfiguration}.
 * 
 * @author Mark Paluch
 */
class AbstractVaultConfigurationUnitTests {

	@Test
	void shouldApplyCustomizerToRestTemplateFactory() {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				RestTemplateCustomizerConfiguration.class);

		RestTemplateFactory factory = context.getBean(RestTemplateFactory.class);
		RestTemplate restTemplate = factory.create();

		assertThatExceptionOfType(CustomizedSignal.class)
				.isThrownBy(() -> restTemplate.delete("/foo"));
	}

	@Test
	void shouldApplyCustomizerToTemplate() {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				RestTemplateCustomizerConfiguration.class);

		VaultOperations operations = context.getBean(VaultOperations.class);

		assertThatExceptionOfType(CustomizedSignal.class)
				.isThrownBy(() -> operations.opsForSys().health());
	}

	@Configuration(proxyBeanMethods = false)
	static class RestTemplateCustomizerConfiguration extends AbstractVaultConfiguration {

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

		@Bean
		public RestTemplateCustomizer customizer() {
			return restTemplate -> restTemplate
					.setRequestFactory((ClientHttpRequestFactory) (uri, httpMethod) -> {
						throw new CustomizedSignal();
					});
		}
	}

	static class CustomizedSignal extends RuntimeException {

	}
}
