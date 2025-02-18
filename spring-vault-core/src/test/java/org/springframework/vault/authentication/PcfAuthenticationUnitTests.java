/*
 * Copyright 2019-2025 the original author or authors.
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link PcfAuthentication}.
 *
 * @author Mark Paluch
 */
class PcfAuthenticationUnitTests {

	RestTemplate restTemplate;

	MockRestServiceServer mockRest;

	String instanceKey = "-----BEGIN RSA PRIVATE KEY-----\n"
			+ "MIIEpAIBAAKCAQEAzhDAw7m5EuCcQkT7cesJF9J/0FWeSICLg4F/3R8EpkjPfZSW\n"
			+ "BeIjSs6v+wUjPCdBomZpYnrphYvSdiDrHWIDKrFNcWMFms0t6A0jEyGG/k9xDf6u\n"
			+ "VjDw4Gi4LJ0o0sNK0y9ULgdrViwRkSKNdhZx+34l/aeWyrg9WG6KAdjbPQE2J4Vi\n"
			+ "3x7yJVEh/Ya0uR/UtN/8hB8Makuaz6SRcLvooUO0FXYpy20olA/nlmowCl1PxpVv\n"
			+ "smZZG7qDJGL/P3C3M9YRX4VPXLTiyvEFwIgWNz6QIhX/Enm0q+xyw+gd5hRQzF4Q\n"
			+ "Fs03VrzHsQX9H3GMd/HoqsM1vD+PPfpKI7re7wIDAQABAoIBAQCk83oq8wd4Wf4b\n"
			+ "ejbBWQB9Zk5UCcVbijKjwU0GR2ckaNJXV1LEQOI5ZrwuN02eQFpk0o/3eiZmdaey\n"
			+ "UeWDLssUKLuyUS7SXP4rbCCwlr0F47e/GSia7DBVot4TMHbWR+gkpxU+h0ffwgUJ\n"
			+ "5dvRNGRnifKFWtr1SYgpusqUcfAFovvcbLZ4X+VkV7uazHAbtor5ol2UuRN04Hnu\n"
			+ "i5gxKBvr/j4ULDnZDJZjJKPVyaS9x4ewVJx241mFbE3aOWxRl6QwouqXTqhIs/Lv\n"
			+ "85MR67nuVlEWMToR/sx0/5Xg/BJDHLUCRM5MNx+wD+Fyj6DlJn2A63Ly4HQZZuYG\n"
			+ "Z48BGku5AoGBAOFmlJQITfp5xtu2oy10bwtDElHV3HBAtcJ7OHgu9ZniNNhgZ7ud\n"
			+ "pw9VC/bL8qZ8iToooKA7AgecSmaDeX0qWPgE9uaheaT76x/6Kn3Ue/ZfV2pSrStu\n"
			+ "YlFc5UxG3Z3klAx/Y+DA7rLb2Y0U6olLx5A7Fg+V/feD8mYxnyZy2r/bAoGBAOoK\n"
			+ "NVIyzcv13S7OqiCo4nhF2CcDWekGTzmsFygwvNwiM1CipzFifQQw6Z+0rl1FwxN0\n"
			+ "uGs3x41iTmkVOyASO78Li9aPQHZWhRWQI82689kYcnChQnDZGLfIv/WVxGy3DgKd\n"
			+ "FdJIOSLagTwhUOnZ4kaR6Mj2Jc4RXYjwpoQUJON9AoGBALOqP75rjDSegvs5boJZ\n"
			+ "7/WLJfwjOw4jFn6KF638yHo7zCG5XpY3CSX4hYvYb3dzhzLblYWC45BLbSafn+Q8\n"
			+ "MCSqWF/n0H3I7FdV4i7gg1sUDirK8gvPdgEiygdt6VLlE3mOxX8ualYZViTVyklc\n"
			+ "JRt7bY9I4OI9w6bf4NsV6/XHAoGAISd5Dj/sL2yQ/MSCDUZfbrJWQJCU+BHQv1bF\n"
			+ "oQfmeTjPFCk2jiRpmWJkdh9eZBAx5luulGG+fyTh/rjnO0/Z7uJv2OFKPHldOQTG\n"
			+ "TaqiSKrR62qswte+TKq/psakoNH9xhkCsltQ3MMfc6k0kSwwhda9p1pXWK3VFkUh\n"
			+ "EazY3PECgYAoo8jvvQTKlXBmnVU1R//16fCklJXqYcEeOAO0CgyPCxHuAULK++M/\n"
			+ "HRHd+6FippoH4ppSACEqQO5TwBTYxgOCwOcYZaRDvYZqEbgPNlf3oZ73kRyoIeAK\n"
			+ "zvaXPNUuUEoW4E9Y2M+9SzF+975TTjqwjBgoCIF3xd+xQfgV9vkB6w==\n" + "-----END RSA PRIVATE KEY-----";

	Clock clock = Clock.fixed(Instant.parse("2007-12-03T10:15:30.00Z"), ZoneId.of("UTC"));

	@BeforeEach
	void before() {

		RestTemplate restTemplate = VaultClients.createRestTemplate();
		restTemplate.setUriTemplateHandler(new VaultClients.PrefixAwareUriBuilderFactory());

		this.mockRest = MockRestServiceServer.createServer(restTemplate);
		this.restTemplate = restTemplate;
	}

	@Test
	void loginShouldObtainToken() {

		PcfAuthenticationOptions options = PcfAuthenticationOptions.builder()
			.instanceCertificate(() -> "foo") //
			.instanceKey(() -> this.instanceKey) //
			.role("dev-role") //
			.clock(this.clock) //
			.build();

		PcfAuthentication authentication = new PcfAuthentication(options, this.restTemplate);

		expectLoginRequest();

		VaultToken login = authentication.login();
		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
	}

	@Test
	void loginWithStepsShouldObtainToken() {

		PcfAuthenticationOptions options = PcfAuthenticationOptions.builder()
			.instanceCertificate(() -> "foo") //
			.instanceKey(() -> this.instanceKey) //
			.role("dev-role") //
			.clock(this.clock) //
			.build();

		expectLoginRequest();

		AuthenticationStepsExecutor authentication = new AuthenticationStepsExecutor(
				PcfAuthentication.createAuthenticationSteps(options), this.restTemplate);

		VaultToken login = authentication.login();
		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
	}

	private void expectLoginRequest() {

		this.mockRest.expect(requestTo("/auth/pcf/login"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.role").value("dev-role"))
			.andExpect(jsonPath("$.signature").exists())
			.andExpect(jsonPath("$.cf_instance_cert").value("foo"))
			.andExpect(jsonPath("$.signing_time").value("2007-12-03T10:15:30Z"))
			.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON)
				.body("{" + "\"auth\":{\"client_token\":\"my-token\"}" + "}"));
	}

}
