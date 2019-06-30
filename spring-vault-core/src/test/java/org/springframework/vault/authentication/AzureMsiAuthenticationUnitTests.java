/*
 * Copyright 2018-2019 the original author or authors.
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
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultClients.PrefixAwareUriTemplateHandler;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link AzureMsiAuthentication}.
 *
 * @author Mark Paluch
 */
class AzureMsiAuthenticationUnitTests {

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
	void loginShouldObtainTokenAndFetchMetadata() {

		AzureMsiAuthenticationOptions options = AzureMsiAuthenticationOptions.builder()
				.role("dev-role") //
				.build();

		expectMetadataRequest();
		expectIdentityTokenRequest();
		expectLoginRequest();

		AzureMsiAuthentication authentication = new AzureMsiAuthentication(options,
				restTemplate);

		VaultToken login = authentication.login();
		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
	}

	@Test
	void loginShouldObtainToken() {

		AzureMsiAuthenticationOptions options = AzureMsiAuthenticationOptions
				.builder()
				.role("dev-role")
				.vmEnvironment(
						new AzureVmEnvironment("foobar-subscription", "vault",
								"vault-client")).build();

		expectIdentityTokenRequest();
		expectLoginRequest();

		AzureMsiAuthentication authentication = new AzureMsiAuthentication(options,
				restTemplate);

		VaultToken login = authentication.login();
		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
	}

	@Test
	void loginWithStepsShouldObtainTokenAndFetchMetadata() {

		AzureMsiAuthenticationOptions options = AzureMsiAuthenticationOptions.builder()
				.role("dev-role") //
				.build();

		expectMetadataRequest();
		expectIdentityTokenRequest();
		expectLoginRequest();

		AuthenticationStepsExecutor authentication = new AuthenticationStepsExecutor(
				AzureMsiAuthentication.createAuthenticationSteps(options), restTemplate);

		VaultToken login = authentication.login();
		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
	}

	@Test
	void loginWithStepsShouldObtainToken() {

		AzureMsiAuthenticationOptions options = AzureMsiAuthenticationOptions
				.builder()
				.role("dev-role")
				.vmEnvironment(
						new AzureVmEnvironment("foobar-subscription", "vault",
								"vault-client")).build();

		expectIdentityTokenRequest();
		expectLoginRequest();

		AuthenticationStepsExecutor authentication = new AuthenticationStepsExecutor(
				AzureMsiAuthentication.createAuthenticationSteps(options), restTemplate);

		VaultToken login = authentication.login();
		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
	}

	private void expectMetadataRequest() {

		mockRest.expect(
				requestTo(AzureMsiAuthenticationOptions.DEFAULT_INSTANCE_METADATA_SERVICE_URI))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Metadata", "true"))
				.andRespond(
						withSuccess()
								.contentType(MediaType.APPLICATION_JSON)
								.body("{\n"
										+ "  \"compute\": {\n"
										+ "   \"name\": \"vault-client\",\n"
										+ "   \"resourceGroupName\": \"vault\",\n"
										+ "   \"subscriptionId\": \"foobar-subscription\"\n"
										+ "  }\n" + "}"));
	}

	private void expectIdentityTokenRequest() {

		mockRest.expect(
				requestTo(AzureMsiAuthenticationOptions.DEFAULT_IDENTITY_TOKEN_SERVICE_URI))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Metadata", "true"))
				.andRespond(
						withSuccess().contentType(MediaType.APPLICATION_JSON).body(
								"{\"access_token\": \"my-token\" }"));

	}

	private void expectLoginRequest() {

		mockRest.expect(requestTo("/auth/azure/login"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.role").value("dev-role"))
				.andExpect(jsonPath("$.jwt").value("my-token"))
				.andExpect(jsonPath("$.subscription_id").value("foobar-subscription"))
				.andExpect(jsonPath("$.resource_group_name").value("vault"))
				.andExpect(jsonPath("$.vm_name").value("vault-client"))
				.andRespond(
						withSuccess().contentType(MediaType.APPLICATION_JSON).body(
								"{" + "\"auth\":{\"client_token\":\"my-token\"}" + "}"));
	}
}
