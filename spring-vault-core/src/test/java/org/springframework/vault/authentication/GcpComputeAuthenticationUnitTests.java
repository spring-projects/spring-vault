/*
 * Copyright 2018-2019 the original author or authors.
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
package org.springframework.vault.authentication;

import java.time.Duration;

import org.junit.Before;
import org.junit.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link GcpComputeAuthentication}.
 *
 * @author Mark Paluch
 */
public class GcpComputeAuthenticationUnitTests {

	private RestTemplate restTemplate;
	private MockRestServiceServer mockRest;

	@Before
	public void before() {

		RestTemplate restTemplate = new RestTemplate();

		this.mockRest = MockRestServiceServer.createServer(restTemplate);
		this.restTemplate = restTemplate;
	}

	private void setupMocks() {

		mockRest.expect(
				requestTo("http://metadata/computeMetadata/v1/instance/service-accounts/default/identity?audience=https://localhost:8200/vault/dev-role&format=full"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(
						withSuccess().contentType(MediaType.TEXT_PLAIN).body("my-jwt"));

		mockRest.expect(requestTo("/auth/gcp/login"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.role").value("dev-role"))
				.andExpect(jsonPath("$.jwt").value("my-jwt"))
				.andRespond(
						withSuccess()
								.contentType(MediaType.APPLICATION_JSON)
								.body("{"
										+ "\"auth\":{\"client_token\":\"my-token\", \"renewable\": true, \"lease_duration\": 10}"
										+ "}"));
	}

	@Test
	public void shouldLogin() {

		setupMocks();

		GcpComputeAuthenticationOptions options = GcpComputeAuthenticationOptions
				.builder().role("dev-role").build();

		GcpComputeAuthentication authentication = new GcpComputeAuthentication(options,
				restTemplate);

		VaultToken login = authentication.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");

		LoginToken loginToken = (LoginToken) login;
		assertThat(loginToken.isRenewable()).isTrue();
		assertThat(loginToken.getLeaseDuration()).isEqualTo(Duration.ofSeconds(10));
	}

	@Test
	public void shouldLoginWithAuthenticationSteps() {

		setupMocks();

		GcpComputeAuthenticationOptions options = GcpComputeAuthenticationOptions
				.builder().role("dev-role").build();

		GcpComputeAuthentication authentication = new GcpComputeAuthentication(options,
				restTemplate);

		AuthenticationStepsExecutor executor = new AuthenticationStepsExecutor(
				authentication.getAuthenticationSteps(), restTemplate);

		VaultToken login = executor.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");

		LoginToken loginToken = (LoginToken) login;
		assertThat(loginToken.isRenewable()).isTrue();
		assertThat(loginToken.getLeaseDuration()).isEqualTo(Duration.ofSeconds(10));
	}
}
