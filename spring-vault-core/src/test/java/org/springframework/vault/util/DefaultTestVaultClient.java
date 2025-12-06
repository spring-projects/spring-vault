/*
 * Copyright 2025 the original author or authors.
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

import org.springframework.http.HttpMethod;
import org.springframework.vault.client.VaultClient;
import org.springframework.web.client.RestClient;

class DefaultTestVaultClient implements TestVaultClient {

	private final VaultClient vaultClient;

	private final RestClient restClient;

	DefaultTestVaultClient(VaultClient vaultClient, RestClient restClient) {
		this.vaultClient = vaultClient;
		this.restClient = restClient;
	}

	@Override
	public RequestHeadersPathSpec<?> get() {
		return vaultClient.get();
	}

	@Override
	public RequestHeadersBodyPathSpec post() {
		return vaultClient.post();
	}

	@Override
	public RequestHeadersBodyPathSpec put() {
		return vaultClient.put();
	}

	@Override
	public RequestHeadersPathSpec<?> delete() {
		return vaultClient.delete();
	}

	@Override
	public RequestHeadersBodyPathSpec method(HttpMethod method) {
		return vaultClient.method(method);
	}

	@Override
	public Builder mutate() {
		return vaultClient.mutate();
	}

	public RestClient getRestClient() {
		return restClient;
	}

}
