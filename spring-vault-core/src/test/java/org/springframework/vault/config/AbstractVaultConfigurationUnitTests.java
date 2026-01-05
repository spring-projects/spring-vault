/*
 * Copyright 2020-present the original author or authors.
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.vault.client.RestTemplateCustomizer;
import org.springframework.vault.client.RestTemplateFactory;
import org.springframework.vault.client.VaultClientCustomizer;
import org.springframework.vault.config.AbstractVaultConfiguration.TaskSchedulerWrapper;
import org.springframework.vault.core.VaultIntegrationTestConfiguration;
import org.springframework.vault.core.VaultOperations;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link AbstractVaultConfiguration}.
 *
 * @author Mark Paluch
 */
class AbstractVaultConfigurationUnitTests {

	@Test
	void shouldApplyCustomizerToRestTemplateFactory() {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				VaultIntegrationTestConfiguration.class, RestTemplateCustomizerConfiguration.class);

		RestTemplateFactory factory = context.getBean(RestTemplateFactory.class);
		RestTemplate restTemplate = factory.create();

		assertThatExceptionOfType(CustomizedSignal.class).isThrownBy(() -> restTemplate.delete("/foo"));
	}

	@Test
	void shouldApplyRestTemplateCustomizer() {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				VaultIntegrationTestConfiguration.class, RestTemplateCustomizerConfiguration.class);

		VaultOperations operations = context.getBean(VaultOperations.class);

		assertThatExceptionOfType(CustomizedSignal.class).isThrownBy(() -> operations.opsForSys().health());
	}

	@Test
	void shouldApplyVaultClientCustomizerToTemplate() {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

		context.register(VaultIntegrationTestConfiguration.class);
		context.registerBean(VaultClientCustomizer.class, () -> {
			return builder -> builder.requestFactory((uri, httpMethod) -> {
				throw new CustomizedSignal();
			});
		});

		context.refresh();

		VaultOperations operations = context.getBean(VaultOperations.class);

		assertThatExceptionOfType(CustomizedSignal.class).isThrownBy(() -> operations.opsForSys().health());
	}

	@Test
	void taskSchedulerWrapperShouldCallLifecycleMethods() {

		ThreadPoolTaskScheduler mock = mock(ThreadPoolTaskScheduler.class);

		TaskSchedulerWrapper wrapper = new TaskSchedulerWrapper(mock);

		wrapper.afterPropertiesSet();
		wrapper.destroy();

		verify(mock).afterPropertiesSet();
		verify(mock).destroy();
	}

	@Test
	void taskSchedulerWrapperFromInstanceShouldNotCallLifecycleMethods() {

		ThreadPoolTaskScheduler mock = mock(ThreadPoolTaskScheduler.class);

		TaskSchedulerWrapper wrapper = TaskSchedulerWrapper.fromInstance(mock);

		wrapper.afterPropertiesSet();
		wrapper.destroy();

		verifyNoInteractions(mock);
	}

	@Configuration(proxyBeanMethods = false)
	static class RestTemplateCustomizerConfiguration {

		@Bean
		public RestTemplateCustomizer customizer() {
			return restTemplate -> restTemplate.setRequestFactory((ClientHttpRequestFactory) (uri, httpMethod) -> {
				throw new CustomizedSignal();
			});
		}

	}

	static class CustomizedSignal extends RuntimeException {

	}

}
