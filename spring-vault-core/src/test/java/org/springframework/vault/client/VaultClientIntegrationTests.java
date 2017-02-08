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

import java.util.Map;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mark Paluch
 */
public class VaultClientIntegrationTests {

	@ClassRule
	public static MockWebServer mockWebServer = new MockWebServer();
	VaultEndpoint vaultEndpoint;

	@Before
	public void before() throws Exception {
		this.vaultEndpoint = VaultEndpoint.plain(mockWebServer.getHostName(),
				mockWebServer.getPort());
	}

	@Test
	public void withoutBody() throws Exception {

		VaultClient vaultClient = DefaultVaultClient.create(new RestTemplate(),
				vaultEndpoint);

		mockWebServer.enqueue(new MockResponse().setResponseCode(204).addHeader(
				HttpHeaders.CONTENT_TYPE, "application/json"));

		VaultResponseEntity<Void> response = vaultClient.get()
				.uri("/{hello}/{world}", "hello", "world")
				.header(HttpHeaders.ACCEPT, "text/json").exchange();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		RecordedRequest recordedRequest = mockWebServer.takeRequest();

		assertThat(recordedRequest.getBodySize()).isEqualTo(0);
		assertThat(recordedRequest.getHeader(HttpHeaders.ACCEPT)).isEqualTo("text/json");
		assertThat(recordedRequest.getPath()).isEqualTo("/v1/hello/world");
	}

	@Test
	public void withResponseBody() throws Exception {

		VaultClient vaultClient = DefaultVaultClient.create(new RestTemplate(),
				vaultEndpoint);

		mockWebServer.enqueue(new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
				.setBody("{ \"ok\": 1}"));

		VaultResponseEntity<Void> response = vaultClient.get()
				.uri("/{hello}/{world}", "hello", "world")
				.header(HttpHeaders.ACCEPT, "text/json").exchange();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNull();
	}

	@Test
	public void withResponseBodyAsMap() throws Exception {

		VaultClient vaultClient = DefaultVaultClient.create(new RestTemplate(),
				vaultEndpoint);

		mockWebServer.enqueue(new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
				.setBody("{ \"ok\": 1}"));

		VaultResponseEntity<Map<String, Integer>> response = vaultClient.get()
				.uri("/{hello}/{world}", "hello", "world")
				.header(HttpHeaders.ACCEPT, "text/json").exchange(Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull().containsEntry("ok", 1);
	}
}
