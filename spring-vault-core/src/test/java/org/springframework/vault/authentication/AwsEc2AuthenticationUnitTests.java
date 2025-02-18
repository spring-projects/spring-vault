/*
 * Copyright 2016-2025 the original author or authors.
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
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.AwsEc2AuthenticationOptions.InstanceMetadataServiceVersion;
import org.springframework.vault.authentication.AwsEc2AuthenticationOptions.Nonce;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link AwsEc2Authentication}.
 *
 * @author Mark Paluch
 */
class AwsEc2AuthenticationUnitTests {

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
	void shouldObtainIdentityDocumentV1() {

		AwsEc2AuthenticationOptions options = AwsEc2AuthenticationOptions.builder()
			.version(InstanceMetadataServiceVersion.V1)
			.build();

		this.mockRest.expect(requestTo("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")) //
			.andExpect(method(HttpMethod.GET)) //
			.andRespond(withSuccess().body("Hello, world"));

		AwsEc2Authentication authentication = new AwsEc2Authentication(options, this.restTemplate, this.restTemplate);

		assertThat(authentication.getEc2Login()).containsEntry("pkcs7", "Hello, world").containsKey("nonce").hasSize(2);
	}

	@Test
	void shouldObtainIdentityDocument() {

		this.mockRest.expect(requestTo("http://169.254.169.254/latest/api/token")) //
			.andExpect(method(HttpMethod.PUT)) //
			.andExpect(header("X-aws-ec2-metadata-token-ttl-seconds", "60")) //
			.andRespond(withSuccess().body("my-token"));

		this.mockRest.expect(requestTo("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")) //
			.andExpect(method(HttpMethod.GET)) //
			.andExpect(header("X-aws-ec2-metadata-token", "my-token")) //
			.andRespond(withSuccess().body("Hello, world"));

		AwsEc2Authentication authentication = new AwsEc2Authentication(this.restTemplate);

		assertThat(authentication.getEc2Login()).containsEntry("pkcs7", "Hello, world").containsKey("nonce").hasSize(2);
	}

	@Test
	void shouldObtainIdentityDocumentWithImperativeAuthenticationStepsV2() {

		AwsEc2AuthenticationOptions options = AwsEc2AuthenticationOptions.builder()
			.version(InstanceMetadataServiceVersion.V2)
			.build();

		this.mockRest.expect(requestTo("http://169.254.169.254/latest/api/token")) //
			.andExpect(method(HttpMethod.PUT)) //
			.andExpect(header("X-aws-ec2-metadata-token-ttl-seconds", "60")) //
			.andRespond(withSuccess().body("my-token"));

		this.mockRest.expect(requestTo("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")) //
			.andExpect(method(HttpMethod.GET)) //
			.andExpect(header("X-aws-ec2-metadata-token", "my-token")) //
			.andRespond(withSuccess().body("Hello, world"));

		this.mockRest.expect(requestTo("/auth/aws-ec2/login"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.pkcs7").value("Hello, world"))
			.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON)
				.body("{" + "\"auth\":{\"client_token\":\"my-token\", \"lease_duration\":20}" + "}"));

		AuthenticationSteps steps = AwsEc2Authentication.createAuthenticationSteps(options);
		AuthenticationStepsExecutor executor = new AuthenticationStepsExecutor(steps, this.restTemplate);

		assertThat(executor.login()).isEqualTo(LoginToken.of("my-token"));
	}

	@Test
	void shouldCleanUpIdentityResponse() {

		AwsEc2AuthenticationOptions options = AwsEc2AuthenticationOptions.builder()
			.version(InstanceMetadataServiceVersion.V1)
			.build();

		this.mockRest.expect(requestTo("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")) //
			.andExpect(method(HttpMethod.GET)) //
			.andRespond(withSuccess().body("Hello, \r\r\n\nworld"));

		AwsEc2Authentication authentication = new AwsEc2Authentication(options, this.restTemplate, this.restTemplate);

		assertThat(authentication.getEc2Login()).containsEntry("pkcs7", "Hello, world");
	}

	@Test
	void shouldContainRole() {

		AwsEc2AuthenticationOptions options = AwsEc2AuthenticationOptions.builder()
			.role("ami")
			.version(InstanceMetadataServiceVersion.V1)
			.build();

		this.mockRest.expect(requestTo("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")) //
			.andExpect(method(HttpMethod.GET)) //
			.andRespond(withSuccess().body("Hello, world"));

		AwsEc2Authentication authentication = new AwsEc2Authentication(options, this.restTemplate, this.restTemplate);

		assertThat(authentication.getEc2Login()) //
			.containsEntry("pkcs7", "Hello, world") //
			.containsEntry("role", "ami") //
			.containsKey("nonce")
			.hasSize(3);
	}

	@Test
	void shouldLogin() {

		Nonce nonce = Nonce.provided("foo".toCharArray());

		AwsEc2AuthenticationOptions authenticationOptions = AwsEc2AuthenticationOptions.builder()
			.nonce(nonce)
			.version(InstanceMetadataServiceVersion.V1)
			.build();

		this.mockRest.expect(requestTo("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")) //
			.andExpect(method(HttpMethod.GET)) //
			.andRespond(withSuccess().body("value"));

		this.mockRest.expect(requestTo("/auth/aws-ec2/login"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.pkcs7").value("value"))
			.andExpect(jsonPath("$.nonce").value("foo"))
			.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON)
				.body("{" + "\"auth\":{\"client_token\":\"my-token\", \"lease_duration\":20}" + "}"));

		AwsEc2Authentication authentication = new AwsEc2Authentication(authenticationOptions, this.restTemplate,
				this.restTemplate);

		VaultToken login = authentication.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
		assertThat(((LoginToken) login).getLeaseDuration()).isEqualTo(Duration.ofSeconds(20));
		assertThat(((LoginToken) login).isRenewable()).isFalse();
	}

	@Test
	void authenticationChainShouldLogin() {

		Nonce nonce = Nonce.provided("foo".toCharArray());

		AwsEc2AuthenticationOptions options = AwsEc2AuthenticationOptions.builder()
			.nonce(nonce)
			.version(InstanceMetadataServiceVersion.V1)
			.build();

		this.mockRest.expect(requestTo("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")) //
			.andExpect(method(HttpMethod.GET)) //
			.andRespond(withSuccess().body("value"));

		this.mockRest.expect(requestTo("/auth/aws-ec2/login"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.pkcs7").value("value"))
			.andExpect(jsonPath("$.nonce").value("foo"))
			.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON)
				.body("{" + "\"auth\":{\"client_token\":\"my-token\", \"lease_duration\":20}" + "}"));

		AuthenticationStepsExecutor executor = new AuthenticationStepsExecutor(
				AwsEc2Authentication.createAuthenticationSteps(options), this.restTemplate);
		VaultToken login = executor.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
		assertThat(((LoginToken) login).getLeaseDuration()).isEqualTo(Duration.ofSeconds(20));
		assertThat(((LoginToken) login).isRenewable()).isFalse();
	}

	@Test
	void loginShouldFailWhileObtainingIdentityDocument() {

		AwsEc2AuthenticationOptions options = AwsEc2AuthenticationOptions.builder()
			.version(InstanceMetadataServiceVersion.V1)
			.build();

		this.mockRest.expect(requestTo("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")) //
			.andRespond(withServerError());

		assertThatExceptionOfType(VaultException.class)
			.isThrownBy(() -> new AwsEc2Authentication(options, this.restTemplate, this.restTemplate).login());
	}

	@Test
	void loginShouldFail() {

		AwsEc2AuthenticationOptions options = AwsEc2AuthenticationOptions.builder()
			.version(InstanceMetadataServiceVersion.V1)
			.build();

		this.mockRest.expect(requestTo("/auth/aws-ec2/login")) //
			.andRespond(withServerError());

		assertThatExceptionOfType(VaultException.class)
			.isThrownBy(() -> new AwsEc2Authentication(options, this.restTemplate, this.restTemplate) {
				@Override
				protected Map<String, String> getEc2Login() {
					return Collections.singletonMap("pkcs7", "value");
				}
			}.login());
	}

}
