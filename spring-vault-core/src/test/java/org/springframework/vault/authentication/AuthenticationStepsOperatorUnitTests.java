/*
 * Copyright 2017-2018 the original author or authors.
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

import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpResponse;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.vault.authentication.AuthenticationSteps.HttpRequestBuilder.post;

/**
 * Unit tests for {@link AuthenticationStepsOperator}.
 *
 * @author Mark Paluch
 */
public class AuthenticationStepsOperatorUnitTests {

	@Before
	public void before() {
	}

	@Test
	public void justTokenShouldLogin() {

		AuthenticationSteps steps = AuthenticationSteps.just(VaultToken.of("my-token"));

		StepVerifier.create(login(steps)).expectNext(VaultToken.of("my-token"))
				.verifyComplete();
	}

	@Test
	public void supplierOfStringShouldLoginWithMap() {

		AuthenticationSteps steps = AuthenticationSteps.fromSupplier(() -> "my-token")
				.login(VaultToken::of);

		StepVerifier.create(login(steps)).expectNext(VaultToken.of("my-token"))
				.verifyComplete();
	}

	@Test
	public void justLoginRequestShouldLogin() {

		ClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST,
				"/auth/cert/login");
		MockClientHttpResponse response = new MockClientHttpResponse(HttpStatus.OK);
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		response.setBody("{"
				+ "\"auth\":{\"client_token\":\"my-token\", \"renewable\": true, \"lease_duration\": 10}"
				+ "}");
		ClientHttpConnector connector = (method, uri, fn) -> fn.apply(request).then(
				Mono.just(response));

		WebClient webClient = WebClient.builder().clientConnector(connector).build();

		AuthenticationSteps steps = AuthenticationSteps.just(post("/auth/{path}/login",
				"cert").as(VaultResponse.class));

		StepVerifier.create(login(steps, webClient))
				.expectNext(VaultToken.of("my-token")).verifyComplete();
	}

	@Test
	public void justLoginShouldFail() {

		ClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST,
				"/auth/cert/login");
		MockClientHttpResponse response = new MockClientHttpResponse(
				HttpStatus.BAD_REQUEST);
		ClientHttpConnector connector = (method, uri, fn) -> fn.apply(request).then(
				Mono.just(response));

		WebClient webClient = WebClient.builder().clientConnector(connector).build();

		AuthenticationSteps steps = AuthenticationSteps.just(post("/auth/{path}/login",
				"cert").as(VaultResponse.class));

		StepVerifier.create(login(steps, webClient)).expectError().verify();
	}

	private Mono<VaultToken> login(AuthenticationSteps steps) {

		AuthenticationStepsOperator operator = new AuthenticationStepsOperator(steps,
				WebClient.create());
		return operator.getVaultToken();
	}

	private Mono<VaultToken> login(AuthenticationSteps steps, WebClient webClient) {

		AuthenticationStepsOperator operator = new AuthenticationStepsOperator(steps,
				webClient);
		return operator.getVaultToken();
	}
}
