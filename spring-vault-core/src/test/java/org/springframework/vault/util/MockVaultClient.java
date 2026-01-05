/*
 * Copyright 2025-present the original author or authors.
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

package org.springframework.vault.util;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.vault.client.VaultClient;
import org.springframework.web.client.RestClient;

/**
 * Test variant of {@link VaultClient} using {@link MockRestServiceServer}.
 *
 * @author Mark Paluch
 */
public class MockVaultClient implements TestVaultClient {

	private final VaultClient client;

	private final RestClient restClient;

	private final MockRestServiceServer mockRest;

	private MockVaultClient(VaultClient client, RestClient restClient, MockRestServiceServer mockRest) {
		this.client = client;
		this.restClient = restClient;
		this.mockRest = mockRest;
	}

	/**
	 * Create a {@link MockVaultClient} bound to {@link MockRestServiceServer}.
	 * @return the {@link MockVaultClient} for testing.
	 */
	public static MockVaultClient create() {
		return create(builder -> {

		});
	}

	/**
	 * Create a {@link MockVaultClient} with custom {@link RestClient} configuration
	 * bound to {@link MockRestServiceServer}.
	 * @param builderConsumer consumer to configure the {@link VaultClient.Builder}.
	 * @return the configured {@link VaultClient}.
	 */
	public static MockVaultClient create(Consumer<VaultClient.Builder> builderConsumer) {

		AtomicReference<MockRestServiceServer> serverRef = new AtomicReference<>();
		AtomicReference<RestClient> restClientRef = new AtomicReference<>();
		Builder builder = VaultClient.builder().configureRestClient(it -> {
			serverRef.set(MockRestServiceServer.bindTo(it).build());
			restClientRef.set(it.build());
		});
		builderConsumer.accept(builder);
		VaultClient client = builder.build();

		return new MockVaultClient(client, restClientRef.get(), serverRef.get());
	}

	@Override
	public RequestHeadersPathSpec<?> get() {
		return client.get();
	}

	@Override
	public RequestHeadersBodyPathSpec post() {
		return client.post();
	}

	@Override
	public RequestHeadersBodyPathSpec put() {
		return client.put();
	}

	@Override
	public RequestHeadersPathSpec<?> delete() {
		return client.delete();
	}

	@Override
	public RequestHeadersBodyPathSpec method(HttpMethod method) {
		return client.method(method);
	}

	@Override
	public Builder mutate() {
		return client.mutate();
	}

	public RestClient getRestClient() {
		return restClient;
	}

	public ResponseActions expect(RequestMatcher requestMatcher) {
		return mockRest.expect(requestMatcher);
	}

}
