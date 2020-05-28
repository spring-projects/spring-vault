/*
 * Copyright 2016-2020 the original author or authors.
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
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultClients.PrefixAwareUriTemplateHandler;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link AppIdAuthentication}.
 *
 * @author Mark Paluch
 */
class AppIdAuthenticationUnitTests {

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
	void loginShouldObtainTokenWithStaticUserId() {

		AppIdAuthenticationOptions options = AppIdAuthenticationOptions.builder().appId("hello") //
				.userIdMechanism(new StaticUserId("world")) //
				.build();

		this.mockRest.expect(requestTo("/auth/app-id/login")).andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.app_id").value("hello")).andExpect(jsonPath("$.user_id").value("world"))
				.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON)
						.body("{" + "\"auth\":{\"client_token\":\"my-token\"}" + "}"));

		AppIdAuthentication authentication = new AppIdAuthentication(options, this.restTemplate);

		VaultToken login = authentication.login();
		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
	}

	@Test
	void loginShouldFail() {

		AppIdAuthenticationOptions options = AppIdAuthenticationOptions.builder().appId("hello") //
				.userIdMechanism(new StaticUserId("world")) //
				.build();

		this.mockRest.expect(requestTo("/auth/app-id/login")) //
				.andRespond(withServerError());

		assertThatExceptionOfType(VaultException.class)
				.isThrownBy(() -> new AppIdAuthentication(options, this.restTemplate).login());
	}

}
