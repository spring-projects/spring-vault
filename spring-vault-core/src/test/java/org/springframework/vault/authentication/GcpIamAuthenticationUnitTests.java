/*
 * Copyright 2018 the original author or authors.
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

import java.security.PrivateKey;
import java.time.Duration;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential.Builder;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import org.junit.Before;
import org.junit.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.vault.client.VaultClients.PrefixAwareUriTemplateHandler;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link GcpIamAuthentication}.
 *
 * @author Mark Paluch
 */
public class GcpIamAuthenticationUnitTests {

	private RestTemplate restTemplate;
	private MockRestServiceServer mockRest;
	private MockHttpTransport mockHttpTransport;

	@Before
	public void before() {

		RestTemplate restTemplate = new RestTemplate();
		restTemplate.setUriTemplateHandler(new PrefixAwareUriTemplateHandler());

		this.mockRest = MockRestServiceServer.createServer(restTemplate);
		this.restTemplate = restTemplate;
	}

	@Test
	public void shouldLogin() {

		MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
		response.setStatusCode(200);
		response.setContent("{\"keyId\":\"keyid\", \"signedJwt\":\"my-jwt\"}");

		mockHttpTransport = new MockHttpTransport.Builder().setLowLevelHttpResponse(
				response).build();

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

		PrivateKey privateKeyMock = mock(PrivateKey.class);
		GoogleCredential credential = new Builder().setServiceAccountId("hello@world")
				.setServiceAccountProjectId("foobar")
				.setServiceAccountPrivateKey(privateKeyMock)
				.setServiceAccountPrivateKeyId("key-id").build();
		credential.setAccessToken("foobar");

		GcpIamAuthenticationOptions options = GcpIamAuthenticationOptions.builder()
				.role("dev-role").credential(credential).build();
		GcpIamAuthentication authentication = new GcpIamAuthentication(options,
				restTemplate, mockHttpTransport);

		VaultToken login = authentication.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");

		LoginToken loginToken = (LoginToken) login;
		assertThat(loginToken.isRenewable()).isTrue();
		assertThat(loginToken.getLeaseDuration()).isEqualTo(Duration.ofSeconds(10));
	}
}
