/*
 * Copyright 2020-2022 the original author or authors.
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
package org.springframework.vault.client;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestRestTemplateFactory;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReactiveVaultClients}.
 *
 * @author Mark Paluch
 */
class ReactiveVaultClientsIntegrationTests extends IntegrationTestSupport {

	@Test
	void shouldUseVaultEndpointProvider() {

		AtomicReference<Thread> resolver = new AtomicReference<>();

		WebClient client = ReactiveVaultClients.createWebClient(() -> {

			return Mono.fromSupplier(() -> {
				resolver.set(Thread.currentThread());
				return TestRestTemplateFactory.TEST_VAULT_ENDPOINT;
			});
		}, ClientHttpConnectorFactory.create(new ClientOptions(), Settings.createSslConfiguration()));

		client.get().uri("/sys/health").exchange().flatMap(it -> it.bodyToMono(String.class)).as(StepVerifier::create)
				.consumeNextWith(actual -> {
					assertThat(actual).contains("initialized").contains("standby");
				}).verifyComplete();

		client.get().uri("sys/health").exchange().flatMap(it -> it.bodyToMono(String.class)).as(StepVerifier::create)
				.consumeNextWith(actual -> {
					assertThat(actual).contains("initialized").contains("standby");
				}).verifyComplete();

		assertThat(resolver).hasValue(Thread.currentThread());
	}

}
