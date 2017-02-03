/*
 * Copyright 2016-2017 the original author or authors.
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
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultClients.PrefixAwareUriTemplateHandler;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link CubbyholeAuthentication}.
 *
 * @author Mark Paluch
 */
public class CubbyholeAuthenticationUnitTests {

	private RestTemplate restTemplate;
	private MockRestServiceServer mockRest;

	@Before
	public void before() throws Exception {

		RestTemplate restTemplate = new RestTemplate();
		restTemplate.setUriTemplateHandler(new PrefixAwareUriTemplateHandler());

		this.mockRest = MockRestServiceServer.createServer(restTemplate);
		this.restTemplate = restTemplate;
	}

	@Test
	public void shouldLoginUsingWrappedLogin() throws Exception {

		mockRest.expect(requestTo("/cubbyhole/response"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(VaultHttpHeaders.VAULT_TOKEN, "hello"))
				.andRespond(
						withSuccess()
								.contentType(MediaType.APPLICATION_JSON)
								.body("{\"data\":{\"response\":\"{\\\"request_id\\\":\\\"058222ef-9ab9-ff39-f087-9d5bee64e46d\\\","
										+ "\\\"auth\\\":{\\\"client_token\\\":\\\"5e6332cf-f003-6369-8cba-5bce2330f6cc\\\","
										+ "\\\"accessor\\\":\\\"46b6aebb-187f-932a-26d7-4f3d86a68319\\\"}}\" } }"));

		CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
				.initialToken(VaultToken.of("hello")).wrapped().build();

		CubbyholeAuthentication authentication = new CubbyholeAuthentication(options,
				restTemplate);

		VaultToken login = authentication.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("5e6332cf-f003-6369-8cba-5bce2330f6cc");
	}

	@Test
	public void shouldLoginUsingStoredLogin() throws Exception {

		mockRest.expect(requestTo("/cubbyhole/token"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(VaultHttpHeaders.VAULT_TOKEN, "hello"))
				.andRespond(
						withSuccess()
								.contentType(MediaType.APPLICATION_JSON)
								.body("{\"data\":{\"mytoken\":\"058222ef-9ab9-ff39-f087-9d5bee64e46d\"} }"));

		CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
				.initialToken(VaultToken.of("hello")).path("cubbyhole/token").build();

		CubbyholeAuthentication authentication = new CubbyholeAuthentication(options,
				restTemplate);

		VaultToken login = authentication.login();

		assertThat(login).isNotInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("058222ef-9ab9-ff39-f087-9d5bee64e46d");
	}

	@Test
	public void shouldFailUsingStoredLoginNoData() throws Exception {

		mockRest.expect(requestTo("/cubbyhole/token"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(VaultHttpHeaders.VAULT_TOKEN, "hello"))
				.andRespond(
						withSuccess().contentType(MediaType.APPLICATION_JSON).body(
								"{\"data\":{} }"));

		CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
				.initialToken(VaultToken.of("hello")).path("cubbyhole/token").build();

		CubbyholeAuthentication authentication = new CubbyholeAuthentication(options,
				restTemplate);

		try {
			authentication.login();
			fail("Missing VaultException");
		}
		catch (VaultException e) {
			assertThat(e).hasMessageContaining("does not contain a token");
		}
	}

	@Test
	public void shouldFailUsingStoredMultipleEntries() throws Exception {

		mockRest.expect(requestTo("/cubbyhole/token"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(VaultHttpHeaders.VAULT_TOKEN, "hello"))
				.andRespond(
						withSuccess().contentType(MediaType.APPLICATION_JSON).body(
								"{\"data\":{\"key1\":1, \"key2\":2} }"));

		CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
				.initialToken(VaultToken.of("hello")).path("cubbyhole/token").build();

		CubbyholeAuthentication authentication = new CubbyholeAuthentication(options,
				restTemplate);

		try {
			authentication.login();
			fail("Missing VaultException");
		}
		catch (VaultException e) {
			assertThat(e).hasMessageContaining("does not contain an unique token");
		}
	}
}
