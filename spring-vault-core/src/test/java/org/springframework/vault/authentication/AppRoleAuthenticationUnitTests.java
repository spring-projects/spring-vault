/*
 * Copyright 2016 the original author or authors.
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

import org.junit.Before;
import org.junit.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link AppRoleAuthentication}.
 * 
 * @author Mark Paluch
 */
public class AppRoleAuthenticationUnitTests {

	private RestTemplate restTemplate;
	private MockRestServiceServer mockRest;

	@Before
	public void before() throws Exception {

		RestTemplate restTemplate = new RestTemplate();
		this.mockRest = MockRestServiceServer.createServer(restTemplate);
		this.restTemplate = restTemplate;
	}

	@Test
	public void loginShouldObtainToken() throws Exception {

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId("hello") //
				.secretId("world") //
				.build();

		mockRest.expect(requestTo("auth/approle/login"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.role_id").value("hello"))
				.andExpect(jsonPath("$.secret_id").value("world"))
				.andRespond(
						withSuccess().contentType(MediaType.APPLICATION_JSON).body(
								"{" + "\"auth\":{\"client_token\":\"my-token\"}" + "}"));

		AppRoleAuthentication sut = new AppRoleAuthentication(options, restTemplate);

		VaultToken login = sut.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
	}

	@Test
	public void loginShouldObtainTokenWithoutSecretId() throws Exception {

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId("hello") //
				.build();

		mockRest.expect(requestTo("auth/approle/login"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.role_id").value("hello"))
				.andExpect(jsonPath("$.secret_id").doesNotExist())
				.andRespond(
						withSuccess()
								.contentType(MediaType.APPLICATION_JSON)
								.body("{"
										+ "\"auth\":{\"client_token\":\"my-token\", \"lease_duration\": 10, \"renewable\": true}"
										+ "}"));

		AppRoleAuthentication sut = new AppRoleAuthentication(options, restTemplate);

		VaultToken login = sut.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
		assertThat(((LoginToken) login).getLeaseDuration()).isEqualTo(10);
		assertThat(((LoginToken) login).isRenewable()).isTrue();
	}

	@Test(expected = VaultException.class)
	public void loginShouldFail() throws Exception {

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId("hello") //
				.build();

		mockRest.expect(requestTo("auth/approle/login")) //
				.andRespond(withServerError());

		new AppRoleAuthentication(options, restTemplate).login();
	}
}
