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

package org.springframework.vault.authentication;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.assertj.MvcTestResultAssert;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.MockVaultClient;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link GcpComputeAuthentication}.
 *
 * @author Mark Paluch
 */
class GcpComputeAuthenticationUnitTests {

	MockVaultClient client;

	@BeforeEach
	void before() {
		this.client = MockVaultClient.create();
	}

	private void setupMocks() {

		this.client.expect(requestTo(
				"http://metadata/computeMetadata/v1/instance/service-accounts/default/identity?audience=https%3A%2F%2Flocalhost%3A8200%2Fvault%2Fdev-role&format=full"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess().contentType(MediaType.TEXT_PLAIN).body("my-jwt"));

		this.client.expect(requestTo("auth/gcp/login"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.role").value("dev-role"))
				.andExpect(jsonPath("$.jwt").value("my-jwt"))
				.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON)
						.body("{"
								+ "\"auth\":{\"client_token\":\"my-token\", \"renewable\": true, \"lease_duration\": 10}"
								+ "}"));
	}

	@Test
	void shouldLogin() {

		setupMocks();

		GcpComputeAuthenticationOptions options = GcpComputeAuthenticationOptions.builder().role("dev-role").build();

		GcpComputeAuthentication authentication = new GcpComputeAuthentication(options, this.client,
				this.client.getRestClient());

		VaultToken login = authentication.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");

		LoginToken loginToken = (LoginToken) login;
		assertThat(loginToken.isRenewable()).isTrue();
		assertThat(loginToken.getLeaseDuration()).isEqualTo(Duration.ofSeconds(10));
	}

	@Test
	void shouldLoginWithAuthenticationSteps() {

		setupMocks();

		GcpComputeAuthenticationOptions options = GcpComputeAuthenticationOptions.builder().role("dev-role").build();

		GcpComputeAuthentication authentication = new GcpComputeAuthentication(options, this.client,
				this.client.getRestClient());

		AuthenticationStepsExecutor executor = new AuthenticationStepsExecutor(authentication.getAuthenticationSteps(),
				this.client, this.client.getRestClient());

		VaultToken login = executor.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");

		LoginToken loginToken = (LoginToken) login;
		assertThat(loginToken.isRenewable()).isTrue();
		assertThat(loginToken.getLeaseDuration()).isEqualTo(Duration.ofSeconds(10));
	}

}
