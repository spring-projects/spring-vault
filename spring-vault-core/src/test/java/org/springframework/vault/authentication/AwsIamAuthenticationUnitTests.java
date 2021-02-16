/*
 * Copyright 2017-2021 the original author or authors.
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

import com.amazonaws.auth.BasicAWSCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultClients.PrefixAwareUriTemplateHandler;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit test for {@link AwsIamAuthentication}.
 *
 * @author Mark Paluch
 */
class AwsIamAuthenticationUnitTests {

	RestTemplate restTemplate;

	MockRestServiceServer mockRest;

	@BeforeEach
	void before() {

		RestTemplate restTemplate = VaultClients.createRestTemplate();
		restTemplate.setUriTemplateHandler(new PrefixAwareUriTemplateHandler());

		this.mockRest = MockRestServiceServer.createServer(restTemplate);
		this.restTemplate = restTemplate;
	}

	@Test
	void shouldAuthenticate() {

		this.mockRest.expect(requestTo("/auth/aws/login")).andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.iam_http_request_method").value("POST"))
				.andExpect(jsonPath("$.iam_request_url").exists()).andExpect(jsonPath("$.iam_request_body").exists())
				.andExpect(jsonPath("$.iam_request_headers").exists()).andExpect(jsonPath("$.role").value("foo-role"))
				.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON).body(
						"{" + "\"auth\":{\"client_token\":\"my-token\", \"renewable\": true, \"lease_duration\": 10}"
								+ "}"));

		AwsIamAuthenticationOptions options = AwsIamAuthenticationOptions.builder().role("foo-role")
				.credentials(new BasicAWSCredentials("foo", "bar")).build();
		AwsIamAuthentication sut = new AwsIamAuthentication(options, this.restTemplate);

		VaultToken login = sut.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
		assertThat(((LoginToken) login).getLeaseDuration()).isEqualTo(Duration.ofSeconds(10));
		assertThat(((LoginToken) login).isRenewable()).isTrue();
	}

	@Test
	void shouldUsingAuthenticationSteps() {

		this.mockRest.expect(requestTo("/auth/aws/login")).andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.iam_http_request_method").value("POST"))
				.andExpect(jsonPath("$.iam_request_url").exists()).andExpect(jsonPath("$.iam_request_body").exists())
				.andExpect(jsonPath("$.iam_request_headers").exists()).andExpect(jsonPath("$.role").value("foo-role"))
				.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON).body(
						"{" + "\"auth\":{\"client_token\":\"my-token\", \"renewable\": true, \"lease_duration\": 10}"
								+ "}"));

		AwsIamAuthenticationOptions options = AwsIamAuthenticationOptions.builder().role("foo-role")
				.credentials(new BasicAWSCredentials("foo", "bar")).build();

		AuthenticationSteps steps = AwsIamAuthentication.createAuthenticationSteps(options);
		AuthenticationStepsExecutor executor = new AuthenticationStepsExecutor(steps, this.restTemplate);

		VaultToken login = executor.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
		assertThat(((LoginToken) login).getLeaseDuration()).isEqualTo(Duration.ofSeconds(10));
		assertThat(((LoginToken) login).isRenewable()).isTrue();
	}

}
