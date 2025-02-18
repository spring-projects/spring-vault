/*
 * Copyright 2017-2025 the original author or authors.
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

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link KubernetesAuthentication}.
 *
 * @author Michal Budzyn
 */
class KubernetesAuthenticationUnitTests {

	private RestTemplate restTemplate;

	private MockRestServiceServer mockRest;

	@BeforeEach
	void before() {

		RestTemplate restTemplate = VaultClients.createRestTemplate();
		restTemplate.setUriTemplateHandler(new VaultClients.PrefixAwareUriBuilderFactory());

		this.mockRest = MockRestServiceServer.createServer(restTemplate);
		this.restTemplate = restTemplate;
	}

	@Test
	void loginShouldObtainTokenWithStaticJwtSupplier() {

		KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions.builder()
			.role("hello") //
			.jwtSupplier(() -> "my-jwt-token")
			.build();

		this.mockRest.expect(requestTo("/auth/kubernetes/login"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.role").value("hello"))
			.andExpect(jsonPath("$.jwt").value("my-jwt-token"))
			.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON)
				.body("{" + "\"auth\":{\"client_token\":\"my-token\"}" + "}"));

		KubernetesAuthentication authentication = new KubernetesAuthentication(options, this.restTemplate);

		VaultToken login = authentication.login();
		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
	}

	@Test
	void loginShouldFail() {

		KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions.builder()
			.role("hello")
			.jwtSupplier(() -> "my-jwt-token")
			.build();

		this.mockRest.expect(requestTo("/auth/kubernetes/login")) //
			.andRespond(withServerError());

		assertThatExceptionOfType(VaultException.class)
			.isThrownBy(() -> new KubernetesAuthentication(options, this.restTemplate).login());
	}

	@Test
	void shouldReuseCachedToken() {

		AtomicReference<String> token = new AtomicReference<>("foo");
		KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions.builder()
			.role("hello") //
			.jwtSupplier(((KubernetesJwtSupplier) token::get).cached())
			.build();

		token.set("bar");

		this.mockRest.expect(requestTo("/auth/kubernetes/login"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.role").value("hello"))
			.andExpect(jsonPath("$.jwt").value("foo"))
			.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON)
				.body("{" + "\"auth\":{\"client_token\":\"my-token\"}" + "}"));

		KubernetesAuthentication authentication = new KubernetesAuthentication(options, this.restTemplate);

		authentication.login();
	}

}
