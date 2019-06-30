/*
 * Copyright 2018-2019 the original author or authors.
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

import java.util.Collections;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpResponse;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReactiveVaultClients}.
 *
 * @author Mark Paluch
 */
class ReactiveVaultClientsUnitTests {

	@Test
	void shouldApplyNamespace() {

		ClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST,
				"/auth/foo");
		MockClientHttpResponse response = new MockClientHttpResponse(HttpStatus.OK);

		ClientHttpConnector connector = (method, uri, fn) -> fn.apply(request).then(
				Mono.just(response));

		WebClient webClient = WebClient.builder().clientConnector(connector)
				.filter(ReactiveVaultClients.namespace("foo/bar")).build();

		webClient.get().uri("/auth/foo").retrieve().bodyToMono(String.class)
				.as(StepVerifier::create) //
				.verifyComplete();

		assertThat(request.getHeaders()).containsEntry(VaultHttpHeaders.VAULT_NAMESPACE,
				Collections.singletonList("foo/bar"));
	}

	@Test
	void shouldAllowNamespaceOverride() {

		ClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST,
				"/auth/foo");
		MockClientHttpResponse response = new MockClientHttpResponse(HttpStatus.OK);

		ClientHttpConnector connector = (method, uri, fn) -> fn.apply(request).then(
				Mono.just(response));

		WebClient webClient = WebClient.builder().clientConnector(connector)
				.filter(ReactiveVaultClients.namespace("foo/bar")).build();

		webClient.get().uri("/auth/foo").header(VaultHttpHeaders.VAULT_NAMESPACE, "baz")
				.retrieve().bodyToMono(String.class) //
				.as(StepVerifier::create) //
				.verifyComplete();

		assertThat(request.getHeaders()).containsEntry(VaultHttpHeaders.VAULT_NAMESPACE,
				Collections.singletonList("baz"));
	}
}
