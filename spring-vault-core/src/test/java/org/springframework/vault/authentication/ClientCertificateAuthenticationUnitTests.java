/*
 * Copyright 2016-2024 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

/**
 * Unit tests for {@link ClientCertificateAuthentication}.
 *
 * @author Mark Paluch
 */
class ClientCertificateAuthenticationUnitTests {

	RestTemplate restTemplate;

	MockRestServiceServer mockRest;

	@BeforeEach
	void before() {

		RestTemplate restTemplate = VaultClients.createRestTemplate();
		restTemplate.setUriTemplateHandler(new VaultClients.PrefixAwareUriBuilderFactory());

		this.mockRest = MockRestServiceServer.createServer(restTemplate);
		this.restTemplate = restTemplate;
	}

	@Test
	void loginShouldObtainToken() {

		this.mockRest.expect(requestTo("/auth/my/path/login"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("{\"name\": \"my-default-role\"}"))
			.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON)
				.body("{" + "\"auth\":{\"client_token\":\"my-token\", \"renewable\": true, \"lease_duration\": 10}"
						+ "}"));

		ClientCertificateAuthenticationOptions options = ClientCertificateAuthenticationOptions.builder()
			.role("my-default-role") //
			.path("my/path")
			.build();

		ClientCertificateAuthentication sut = new ClientCertificateAuthentication(options, this.restTemplate);

		VaultToken login = sut.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
		assertThat(((LoginToken) login).getLeaseDuration()).isEqualTo(Duration.ofSeconds(10));
		assertThat(((LoginToken) login).isRenewable()).isTrue();
	}

	@Test
	void loginShouldFail() {

		this.mockRest.expect(requestTo("/auth/cert/login")) //
			.andRespond(withServerError());

		assertThatExceptionOfType(VaultException.class)
			.isThrownBy(() -> new ClientCertificateAuthentication(this.restTemplate).login());
	}

}
