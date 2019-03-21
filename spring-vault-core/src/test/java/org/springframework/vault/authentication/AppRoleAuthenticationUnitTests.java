/*
 * Copyright 2016-2018 the original author or authors.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.SecretId;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultClients.PrefixAwareUriTemplateHandler;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link AppRoleAuthentication}.
 *
 * @author Mark Paluch
 * @author Vincent Le Nair
 * @author Christophe Tafani-Dereeper
 */
public class AppRoleAuthenticationUnitTests {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private RestTemplate restTemplate;
	private MockRestServiceServer mockRest;

	@Before
	public void before() {

		RestTemplate restTemplate = VaultClients.createRestTemplate();
		restTemplate.setUriTemplateHandler(new PrefixAwareUriTemplateHandler());

		this.mockRest = MockRestServiceServer.createServer(restTemplate);
		this.restTemplate = restTemplate;
	}

	@Test
	public void loginShouldObtainToken() {

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId("hello") //
				.secretId("world") //
				.build();

		mockRest.expect(requestTo("/auth/approle/login"))
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
	public void loginShouldPullRoleIdAndSecretId() throws Exception {

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.appRole("app_role").initialToken(VaultToken.of("initial_token")).build();

		mockRest.expect(requestTo("/auth/approle/role/app_role/role-id"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("X-Vault-token", "initial_token"))
				.andRespond(
						withSuccess().contentType(MediaType.APPLICATION_JSON).body(
								"{\"data\": {\"role_id\": \"hello\"}}"));

		mockRest.expect(requestTo("/auth/approle/role/app_role/secret-id"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("X-Vault-token", "initial_token"))
				.andRespond(
						withSuccess().contentType(MediaType.APPLICATION_JSON).body(
								"{\"data\": {\"secret_id\": \"world\"}}"));

		mockRest.expect(requestTo("/auth/approle/login"))
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

	@Test(expected = IllegalArgumentException.class)
	public void optionsShouldRequireTokenOrRoleIdIfNothingIsSet() {
		AppRoleAuthenticationOptions.builder().build();
	}

	@Test(expected = IllegalArgumentException.class)
	public void optionsShouldRequireTokenOrRoleIdIfTokenIsSet() {
		AppRoleAuthenticationOptions.builder().initialToken(VaultToken.of("foo")).build();
	}

	@Test(expected = IllegalArgumentException.class)
	public void optionsShouldRequireTokenOrRoleIdIfAppRoleIdIsSet() {
		AppRoleAuthenticationOptions.builder().appRole("app_role").build();
	}

	@Test
	public void loginShouldObtainTokenWithoutSecretId() {

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId("hello") //
				.build();

		mockRest.expect(requestTo("/auth/approle/login"))
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
		assertThat(((LoginToken) login).getLeaseDuration()).isEqualTo(
				Duration.ofSeconds(10));
		assertThat(((LoginToken) login).isRenewable()).isTrue();
	}

	@Test(expected = VaultException.class)
	public void loginShouldFail() {

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId("hello") //
				.build();

		mockRest.expect(requestTo("/auth/approle/login")) //
				.andRespond(withServerError());

		new AppRoleAuthentication(options, restTemplate).login();
	}

	@Test
	public void loginShouldUnwrapSecretIdResponse() throws Exception {

		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.roleId("my_role_id")
				.secretId(SecretId.wrapped(VaultToken.of("unwrapping_token"))).build();

		String wrappedResponse = "{"
				+ "  \"request_id\": \"aad6a19b-a42b-b750-cafb-51087662f53e\","
				+ "  \"lease_id\": \"\"," + "  \"renewable\": false,"
				+ "  \"lease_duration\": 0," + "  \"data\": {"
				+ "    \"secret_id\": \"my_secret_id\","
				+ "    \"secret_id_accessor\": \"my_secret_id_accessor\"" + "  },"
				+ "  \"wrap_info\": null," + "  \"warnings\": null," + "  \"auth\": null"
				+ "}";

		// Expect a first request to unwrap the response
		mockRest.expect(requestTo("/cubbyhole/response"))
				.andExpect(header("X-Vault-Token", "unwrapping_token"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(
						withSuccess().contentType(MediaType.APPLICATION_JSON).body(
								"{\"data\":{\"response\":"
										+ OBJECT_MAPPER
												.writeValueAsString(wrappedResponse)
										+ "} }"));

		// Also expect a second request to retrieve a token
		mockRest.expect(requestTo("/auth/approle/login"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.role_id").value("my_role_id"))
				.andExpect(jsonPath("$.secret_id").value("my_secret_id"))
				.andRespond(
						withSuccess()
								.contentType(MediaType.APPLICATION_JSON)
								.body("{"
										+ "\"auth\":{\"client_token\":\"my-token\", \"lease_duration\": 10, \"renewable\": true}"
										+ "}"));

		AppRoleAuthentication auth = new AppRoleAuthentication(options, restTemplate);

		VaultToken login = auth.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
		assertThat(((LoginToken) login).getLeaseDuration()).isEqualTo(
				Duration.ofSeconds(10));
		assertThat(((LoginToken) login).isRenewable()).isTrue();
	}
}
