/*
 * Copyright 2018-2022 the original author or authors.
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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.time.Duration;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential.Builder;
import com.google.api.client.googleapis.testing.auth.oauth2.MockGoogleCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link GcpIamAuthentication}.
 *
 * @author Mark Paluch
 */
class GcpIamAuthenticationUnitTests {

	RestTemplate restTemplate;

	MockRestServiceServer mockRest;

	@BeforeEach
	void before() {

		RestTemplate restTemplate = new RestTemplate();
		restTemplate.setUriTemplateHandler(new VaultClients.PrefixAwareUriBuilderFactory());

		this.mockRest = MockRestServiceServer.createServer(restTemplate);
		this.restTemplate = restTemplate;
	}

	@Test
	void shouldLogin() throws NoSuchAlgorithmException {

		this.mockRest.expect(requestTo("/auth/gcp/login")).andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.role").value("dev-role")).andExpect(jsonPath("$.jwt").value("my-jwt"))
				.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON).body(
						"{" + "\"auth\":{\"client_token\":\"my-token\", \"renewable\": true, \"lease_duration\": 10}"
								+ "}"));

		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
		keyGen.initialize(1024);
		KeyPair key = keyGen.generateKeyPair();

		GoogleCredential credential = new MockGoogleCredential.Builder().setServiceAccountId("hello@world")
				.setServiceAccountProjectId("foobar").setServiceAccountPrivateKey(key.getPrivate())
				.setServiceAccountPrivateKeyId("key-id").setJsonFactory(new GsonFactory())
				.setTransport(new MockHttpTransport.Builder().setLowLevelHttpResponse(createMockHttpResponse()).build())
				.build();
		credential.setAccessToken("foobar");

		GcpIamAuthenticationOptions options = GcpIamAuthenticationOptions.builder().role("dev-role")
				.credential(credential).build();
		GcpIamAuthentication authentication = new GcpIamAuthentication(options, this.restTemplate,
				new MockHttpTransport.Builder().setLowLevelHttpResponse(createMockHttpResponse()).build());

		VaultToken login = authentication.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");

		LoginToken loginToken = (LoginToken) login;
		assertThat(loginToken.isRenewable()).isTrue();
		assertThat(loginToken.getLeaseDuration()).isEqualTo(Duration.ofSeconds(10));
	}

	private MockLowLevelHttpResponse createMockHttpResponse() {
		MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
		response.setStatusCode(200);
		response.setContent("{\"keyId\":\"keyid\", \"signedJwt\":\"my-jwt\"}");
		return response;
	}

	@Test
	void shouldCreateNewGcpIamObjectInstance() throws GeneralSecurityException, IOException {

		PrivateKey privateKeyMock = mock(PrivateKey.class);
		GoogleCredential credential = new Builder().setServiceAccountId("hello@world")
				.setServiceAccountProjectId("foobar").setServiceAccountPrivateKey(privateKeyMock)
				.setServiceAccountPrivateKeyId("key-id").build();
		credential.setAccessToken("foobar");

		GcpIamAuthenticationOptions options = GcpIamAuthenticationOptions.builder().role("dev-role")
				.credential(credential).build();

		new GcpIamAuthentication(options, this.restTemplate);
	}

}
