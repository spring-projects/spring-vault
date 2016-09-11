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

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.util.Collections;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.VaultException;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

/**
 * Unit tests for {@link AwsEc2Authentication}.
 *
 * @author Mark Paluch
 */
public class AwsEc2AuthenticationUnitTests {

	private VaultClient vaultClient;
	private MockRestServiceServer mockRest;

	@Before
	public void before() throws Exception {

		RestTemplate restTemplate = new RestTemplate();
		mockRest = MockRestServiceServer.createServer(restTemplate);
		vaultClient = new VaultClient(restTemplate, new VaultEndpoint());
	}

	@Test
	public void shouldObtainIdentityDocument() throws Exception {

		mockRest.expect(requestTo("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")) //
				.andExpect(method(HttpMethod.GET)) //
				.andRespond(withSuccess().body("Hello, world"));

		AwsEc2Authentication authentication = new AwsEc2Authentication(vaultClient);

		assertThat(authentication.getEc2Login()).containsEntry("pkcs7", "Hello, world").containsKey("nonce").hasSize(2);
	}

	@Test
	public void shouldContainRole() throws Exception {

		AwsEc2AuthenticationOptions options = AwsEc2AuthenticationOptions.builder().role("ami").build();

		mockRest.expect(requestTo("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")) //
				.andExpect(method(HttpMethod.GET)) //
				.andRespond(withSuccess().body("Hello, world"));

		AwsEc2Authentication authentication = new AwsEc2Authentication(options, vaultClient, vaultClient.getRestTemplate());

		assertThat(authentication.getEc2Login()) //
				.containsEntry("pkcs7", "Hello, world") //
				.containsEntry("role", "ami") //
				.containsKey("nonce").hasSize(3);
	}

	@Test
	public void shouldLogin() throws Exception {

		mockRest.expect(requestTo("https://localhost:8200/v1/auth/aws-ec2/login")) //
				.andExpect(method(HttpMethod.POST)) //
				.andExpect(jsonPath("$.pkcs7").value("value")) //
				.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON)
						.body("{" + "\"auth\":{\"client_token\":\"my-token\"}" + "}"));

		AwsEc2Authentication authentication = new AwsEc2Authentication(vaultClient) {
			@Override
			protected Map<String, String> getEc2Login() {
				return Collections.singletonMap("pkcs7", "value");
			}
		};

		VaultToken vaultToken = authentication.login();

		assertThat(vaultToken.getToken()).isEqualTo("my-token");
	}

	@Test(expected = VaultException.class)
	public void loginShouldFailWhileObtainingIdentityDocument() throws Exception {

		mockRest.expect(requestTo("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")) //
				.andRespond(withServerError());

		new AwsEc2Authentication(vaultClient).login();
	}

	@Test(expected = VaultException.class)
	public void loginShouldFail() throws Exception {

		mockRest.expect(requestTo("https://localhost:8200/v1/auth/aws-ec2/login")) //
				.andRespond(withServerError());

		new AwsEc2Authentication(vaultClient) {
			@Override
			protected Map<String, String> getEc2Login() {
				return Collections.singletonMap("pkcs7", "value");
			}
		}.login();
	}
}
