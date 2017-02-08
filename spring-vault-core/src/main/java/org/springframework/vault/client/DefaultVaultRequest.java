/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.vault.client;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;

/**
 * @author Mark Paluch
 */
class DefaultVaultRequest<T> implements VaultRequest<T> {

	private final HttpMethod method;

	private final URI url;

	private final HttpHeaders headers;

	private final VaultRequestBody<T> body;

	protected DefaultVaultRequest(HttpMethod method, URI url, HttpHeaders headers,
			VaultRequestBody<T> body) {

		Assert.notNull(method, "HttpMethod must not be null!");
		Assert.notNull(url, "URI must not be null!");
		Assert.notNull(headers, "HttpHeaders must not be null!");
		Assert.notNull(body, "VaultRequestBody must not be null!");

		this.method = method;
		this.url = url;
		this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
		this.body = body;
	}

	/**
	 * Create a builder with the given method and url.
	 * @param method the HTTP method (GET, POST, etc)
	 * @param url the URL
	 * @return the created builder
	 */
	static Builder method(HttpMethod method, URI url) {
		return new DefaultClientRequestBuilder(method, url);
	}

	@Override
	public HttpMethod method() {
		return this.method;
	}

	@Override
	public URI url() {
		return this.url;
	}

	@Override
	public HttpHeaders headers() {
		return this.headers;
	}

	@Override
	public VaultRequestBody<T> body() {
		return body;
	}

	static class DefaultClientRequestBuilder implements VaultRequest.Builder {

		private final HttpMethod method;

		private final URI url;

		private final HttpHeaders headers = new HttpHeaders();

		public DefaultClientRequestBuilder(HttpMethod method, URI url) {
			this.method = method;
			this.url = url;
		}

		@Override
		public VaultRequest.Builder header(String headerName, String... headerValues) {
			for (String headerValue : headerValues) {
				this.headers.add(headerName, headerValue);
			}
			return this;
		}

		@Override
		public VaultRequest.Builder headers(HttpHeaders headers) {
			if (headers != null) {
				this.headers.putAll(headers);
			}
			return this;
		}

		@Override
		public VaultRequest<Void> build() {
			return body(VaultRequestBody.<Void> empty());
		}

		@Override
		public <T> VaultRequest<T> body(VaultRequestBody<T> body) {
			return new DefaultVaultRequest<T>(method, url, headers, body);
		}
	}
}
