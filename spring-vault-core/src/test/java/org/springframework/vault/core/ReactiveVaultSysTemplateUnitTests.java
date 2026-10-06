/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.vault.core;

import java.io.IOException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.vault.client.ReactiveVaultClient;
import org.springframework.vault.client.VaultEndpoint;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ReactiveVaultSysTemplate}.
 *
 * @author Henk Hofs
 */
class ReactiveVaultSysTemplateUnitTests {

	private static final MockWebServer mockWebServer = new MockWebServer();

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

	@Test
	void shouldDeserializeHealthErrorResponse() throws Exception {

		mockWebServer.enqueue(new MockResponse().setResponseCode(429)
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("""
						{
							"initialized": true,
							"sealed": false,
							"standby": true,
							"performance_standby": false,
							"ha_connection_healthy": true,
							"server_time_utc": 123,
							"version": "1.20.0"
						}
						"""));

		ReactiveVaultSysOperations sysOperations = new ReactiveVaultSysTemplate(
				new ReactiveVaultTemplate(ReactiveVaultClient.create(localhost)));

		sysOperations.health().as(StepVerifier::create).assertNext(health -> {
			assertThat(health.isInitialized()).isTrue();
			assertThat(health.isSealed()).isFalse();
			assertThat(health.isStandby()).isTrue();
			assertThat(health.isPerformanceStandby()).isFalse();
			assertThat(health.getHaConnectionHealthy()).isTrue();
			assertThat(health.getServerTimeUtc()).isEqualTo(123);
			assertThat(health.getVersion()).isEqualTo("1.20.0");
		}).verifyComplete();

		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo(HttpMethod.GET.name());
		assertThat(recordedRequest.getPath()).isEqualTo("/v1/sys/health");
	}

}
