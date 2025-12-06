/*
 * Copyright 2018-2025 the original author or authors.
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

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.stream.Stream;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.vault.client.VaultClients.PrefixAwareUriBuilderFactory;
import org.springframework.vault.util.MockVaultClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriBuilderFactory;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link ReactiveVaultClient}.
 *
 * @author Mark Paluch
 */
class ReactiveVaultClientUnitTests {

	private final static MockWebServer mockWebServer = new MockWebServer();

	static VaultEndpoint localhost = new VaultEndpoint();

	@BeforeAll
	static void beforeAll() throws IOException {
		mockWebServer.start();
		localhost.setHost("localhost");
		localhost.setPort(mockWebServer.getPort());
		localhost.setScheme("http");
	}

	@AfterAll
	static void afterAll() throws IOException {
		mockWebServer.shutdown();
	}

	@ParameterizedTest
	@MethodSource("clients")
	void shouldExpandPathWithVariables(ReactiveVaultClient client) throws Exception {

		mockWebServer.enqueue(new MockResponse().setResponseCode(200)
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("{}"));

		client.get().path(
				"auth/{foo}", "foo").retrieve().body().as(StepVerifier::create)
				.expectNextCount(1).verifyComplete();

		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getPath()).isEqualTo("/v1/auth/foo");
	}

	@ParameterizedTest
	@MethodSource("clients")
	void shouldExpandPathWithVariableMap(ReactiveVaultClient client) throws Exception {

		mockWebServer.enqueue(new MockResponse().setResponseCode(200)
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("{}"));

		client.get().path(
				"auth/{foo}", Map.of("foo", "foo")).retrieve().body().as(StepVerifier::create)
				.expectNextCount(1).verifyComplete();

		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getPath()).isEqualTo("/v1/auth/foo");
	}

	@ParameterizedTest
	@MethodSource("clients")
	void shouldUseUri(ReactiveVaultClient client) throws Exception {

		mockWebServer.enqueue(new MockResponse().setResponseCode(200)
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("{}"));

		URI uri = mockWebServer.url("/v1/auth/foo").uri();
		client.get().uri(uri).retrieve().body().as(StepVerifier::create)
				.expectNextCount(1).verifyComplete();

		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getPath()).isEqualTo("/v1/auth/foo");
	}

	@ParameterizedTest
	@MethodSource("clients")
	void shouldExpandAbsolutePath(ReactiveVaultClient client) throws InterruptedException {

		mockWebServer.enqueue(new MockResponse().setResponseCode(200)
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("{}"));

		client.get().path("/auth/foo").retrieve().body().as(StepVerifier::create)
				.expectNextCount(1).verifyComplete();

		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getPath()).isEqualTo("/v1/auth/foo");
	}

	@ParameterizedTest
	@MethodSource("clients")
	void shouldEscapeAbsolutePath(ReactiveVaultClient client) throws Exception {

		mockWebServer.enqueue(new MockResponse().setResponseCode(200)
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("{}"));

		client.get().path("https://some.other.server:8200/v1/auth/foo").retrieve().body().as(StepVerifier::create)
				.expectNextCount(1).verifyComplete();

		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getPath()).isEqualTo("/v1/https:/some.other.server:8200/v1/auth/foo");
	}

	@ParameterizedTest
	@MethodSource("safeClients")
	void shouldApplyRelativeUri(ReactiveVaultClient client) throws Exception {

		mockWebServer.enqueue(new MockResponse().setResponseCode(200)
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("{}"));

		client.get().uri(URI.create("/v1/auth/foo")).retrieve().body()
				.as(StepVerifier::create)
				.expectNextCount(1).verifyComplete();

		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getPath()).isEqualTo("/v1/v1/auth/foo");
	}

	@ParameterizedTest
	@MethodSource("unsafeClients")
	@Disabled("URI not resolved correctly for unsafe clients")
	void shouldApplyFullUri(ReactiveVaultClient client) throws Exception {

		mockWebServer.enqueue(new MockResponse().setResponseCode(200)
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("{}"));

		client.get().uri(URI.create("/v1/auth/foo")).retrieve().body()
				.as(StepVerifier::create)
				.expectNextCount(1)
				.verifyComplete();

		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getPath()).isEqualTo("/v1/auth/foo");
	}

	@ParameterizedTest
	@MethodSource("clients")
	void shouldPassthruAbsoluteUri(ReactiveVaultClient client) throws Exception {

		mockWebServer.enqueue(new MockResponse().setResponseCode(200)
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("{}"));

		client.get().uri(URI.create("https://some.other.server:8200/v1/auth/foo")).retrieve().body()
				.as(StepVerifier::create)
				.verifyError(WebClientRequestException.class);
	}

	@Test
	void shouldApplyNamespace() {

		MockVaultClient client = MockVaultClient.create(it -> it.defaultNamespace("foo/bar")
				.configureRestClient(rcb -> rcb.uriBuilderFactory(new PrefixAwareUriBuilderFactory())));

		client.expect(requestTo("/auth/foo")).andExpect(method(HttpMethod.GET))
				.andExpect(header(VaultHttpHeaders.VAULT_NAMESPACE, "foo/bar")).andRespond(withSuccess());

		client.get().path("auth/foo").retrieve().body();
	}

	static Stream<Arguments.ArgumentSet> clients() {
		return Stream.concat(safeClients(), unsafeClients());
	}

	static Stream<Arguments.ArgumentSet> safeClients() {

		UriBuilderFactory simple = VaultClients.createUriBuilderFactory(SimpleVaultEndpointProvider.of(localhost),
				false);
		UriBuilderFactory extended = VaultClients.createUriBuilderFactory(() -> localhost, false);

		return Stream.of(
				Arguments.argumentSet("Simple Endpoint", ReactiveVaultClient.create(localhost)),
				Arguments.argumentSet("Reactive Endpoint Provider",
						ReactiveVaultClient.create(() -> Mono.just(localhost))),
				Arguments.argumentSet("URL Simple", ReactiveVaultClient.builder().uriBuilderFactory(simple).build()),
				Arguments.argumentSet("URL Extended",
						ReactiveVaultClient.builder().uriBuilderFactory(extended).build()));
	}

	static Stream<Arguments.ArgumentSet> unsafeClients() {

		UriBuilderFactory simple = VaultClients.createUriBuilderFactory(SimpleVaultEndpointProvider.of(localhost),
				false);
		UriBuilderFactory extended = VaultClients.createUriBuilderFactory(() -> localhost, false);
		WebClient simpleClient = WebClient.builder().uriBuilderFactory(simple).build();
		WebClient extendedClient = WebClient.builder().uriBuilderFactory(extended).build();

		return Stream.of(
				Arguments.argumentSet("WebClient Simple", ReactiveVaultClient.builder(simpleClient).build()),
				Arguments.argumentSet("WebClient Extended", ReactiveVaultClient.builder(extendedClient).build()));
	}

}
