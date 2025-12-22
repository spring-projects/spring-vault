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

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.AuthenticationSteps.Node;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.vault.authentication.AuthenticationSteps.HttpRequestBuilder.*;

/**
 * Unit tests for {@link AuthenticationStepsExecutor}.
 *
 * @author Mark Paluch
 */
class AuthenticationStepsExecutorUnitTests {

	RestClient restClient;

	MockRestServiceServer mockRest;

	@BeforeEach
	void before() {

		RestClient.Builder builder = RestClient.builder();
		builder.uriBuilderFactory(new VaultClients.PrefixAwareUriBuilderFactory());

		this.mockRest = MockRestServiceServer.bindTo(builder).build();
		this.restClient = builder.build();
	}

	@Test
	void justTokenShouldLogin() {

		AuthenticationSteps steps = AuthenticationSteps.just(VaultToken.of("my-token"));

		assertThat(login(steps)).isEqualTo(VaultToken.of("my-token"));
	}

	@Test
	void supplierOfStringShouldLoginWithMap() {

		AuthenticationSteps steps = AuthenticationSteps.fromSupplier(() -> "my-token").login(VaultToken::of);

		assertThat(login(steps)).isEqualTo(VaultToken.of("my-token"));
	}

	@Test
	void fileResourceCredentialSupplierShouldBeLoaded() {

		AuthenticationSteps steps = AuthenticationSteps
				.fromSupplier(new ResourceCredentialSupplier(new ClassPathResource("kube-jwt-token")))
				.login(VaultToken::of);

		assertThat(login(steps).getToken()).startsWith("eyJhbGciOiJSUz");
	}

	@Test
	void inputStreamResourceCredentialSupplierShouldBeLoaded() {

		AuthenticationSteps steps = AuthenticationSteps
				.fromSupplier(new ResourceCredentialSupplier(new ByteArrayResource("eyJhbGciOiJSUz".getBytes())))
				.login(VaultToken::of);

		assertThat(login(steps).getToken()).startsWith("eyJhbGciOiJSUz");
	}

	@Test
	void justLoginRequestShouldLogin() {

		this.mockRest.expect(requestTo("/auth/cert/login"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON)
						.body("{"
								+ "\"auth\":{\"client_token\":\"my-token\", \"renewable\": true, \"lease_duration\": 10}"
								+ "}"));

		AuthenticationSteps steps = AuthenticationSteps
				.just(post("/auth/{path}/login", "cert").as(VaultResponse.class));

		assertThat(login(steps)).isEqualTo(VaultToken.of("my-token"));
	}

	@Test
	void justLoginShouldFail() {

		this.mockRest.expect(requestTo("/auth/cert/login"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withBadRequest().body("foo"));

		AuthenticationSteps steps = AuthenticationSteps
				.just(post("/auth/{path}/login", "cert").as(VaultResponse.class));

		assertThatExceptionOfType(VaultException.class).isThrownBy(() -> login(steps))
				.withMessageContaining(
						"HTTP request POST /auth/{path}/login AS class org.springframework.vault.support.VaultResponse "
								+ "in state null failed with Status 400 and body foo");
	}

	@Test
	void initialRequestWithMapShouldLogin() {

		this.mockRest.expect(requestTo("/somewhere/else"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess().contentType(MediaType.TEXT_PLAIN).body("foo"));

		this.mockRest.expect(requestTo("/auth/cert/login"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().string("foo-token"))
				.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON)
						.body("{"
								+ "\"auth\":{\"client_token\":\"foo-token\", \"renewable\": true, \"lease_duration\": 10}"
								+ "}"));

		AuthenticationSteps steps = AuthenticationSteps
				.fromHttpRequest(get(URI.create("somewhere/else")).as(String.class))
				.onNext(System.out::println) //
				.map(s -> s.concat("-token")) //
				.login("/auth/cert/login");

		assertThat(login(steps)).isEqualTo(VaultToken.of("foo-token"));
	}

	@Test
	void requestWithHeadersShouldLogin() {

		this.mockRest.expect(requestTo("/somewhere/else")) //
				.andExpect(header("foo", "bar")) //
				.andExpect(method(HttpMethod.GET)) //
				.andRespond(withSuccess().contentType(MediaType.TEXT_PLAIN).body("foo"));

		this.mockRest.expect(requestTo("/auth/cert/login"))
				.andExpect(content().string("foo"))
				.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON)
						.body("{"
								+ "\"auth\":{\"client_token\":\"foo-token\", \"renewable\": true, \"lease_duration\": 10}"
								+ "}"));

		HttpHeaders headers = new HttpHeaders();
		headers.add("foo", "bar");

		AuthenticationSteps steps = AuthenticationSteps
				.fromHttpRequest(get(URI.create("somewhere/else")).with(headers).as(String.class)) //
				.login("/auth/cert/login");

		assertThat(login(steps)).isEqualTo(VaultToken.of("foo-token"));
	}

	@Test
	void zipWithShouldRequestTwoItems() {

		this.mockRest.expect(requestTo("/auth/login/left"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(
						withSuccess().contentType(MediaType.APPLICATION_JSON).body("{" + "\"request_id\": \"left\"}"));

		this.mockRest.expect(requestTo("/auth/login/right"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(
						withSuccess().contentType(MediaType.APPLICATION_JSON).body("{" + "\"request_id\": \"right\"}"));

		Node<VaultResponse> left = AuthenticationSteps
				.fromHttpRequest(post("/auth/login/left").as(VaultResponse.class));

		Node<VaultResponse> right = AuthenticationSteps
				.fromHttpRequest(post("/auth/login/right").as(VaultResponse.class));

		AuthenticationSteps steps = left.zipWith(right)
				.login(it -> VaultToken.of(it.getLeft().getRequestId() + "-" + it.getRight().getRequestId()));

		assertThat(login(steps)).isEqualTo(VaultToken.of("left-right"));
	}

	private VaultToken login(AuthenticationSteps steps) {
		return new AuthenticationStepsExecutor(steps, this.restClient).login();
	}

}
