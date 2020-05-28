/*
 * Copyright 2017-2020 the original author or authors.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.vault.client.VaultClients.PrefixAwareUriTemplateHandler;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link LoginTokenAdapter}.
 *
 * @author Mark Paluch
 */
class LoginTokenAdapterUnitTests {

	RestTemplate restTemplate;

	MockRestServiceServer mockRest;

	@BeforeEach
	void before() throws Exception {

		RestTemplate restTemplate = new RestTemplate();
		restTemplate.setUriTemplateHandler(new PrefixAwareUriTemplateHandler());

		this.mockRest = MockRestServiceServer.createServer(restTemplate);
		this.restTemplate = restTemplate;
	}

	@Test
	void shouldSelfLookupToken() throws Exception {

		this.mockRest.expect(requestTo("/auth/token/lookup-self")).andExpect(method(HttpMethod.GET))
				.andExpect(header(VaultHttpHeaders.VAULT_TOKEN, "5e6332cf-f003-6369-8cba-5bce2330f6cc"))
				.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON).body("{\"data\": {\n"
						+ "    \"creation_ttl\": 600,\n" + "    \"renewable\": false,\n" + "    \"ttl\": 456} }"));

		LoginTokenAdapter adapter = new LoginTokenAdapter(
				new TokenAuthentication("5e6332cf-f003-6369-8cba-5bce2330f6cc"), this.restTemplate);

		VaultToken login = adapter.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("5e6332cf-f003-6369-8cba-5bce2330f6cc");

		LoginToken loginToken = (LoginToken) login;
		assertThat(loginToken.isRenewable()).isFalse();
		assertThat(loginToken.getLeaseDuration().getSeconds()).isEqualTo(456);
	}

}
