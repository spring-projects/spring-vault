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
 * Unit tests for {@link ClientCertificateAuthentication}.
 * 
 * @author Mark Paluch
 */
public class ClientCertificateAuthenticationUnitTests {

	private VaultClient vaultClient;
	private MockRestServiceServer mockRest;

	@Before
	public void before() throws Exception {

		RestTemplate restTemplate = new RestTemplate();
		mockRest = MockRestServiceServer.createServer(restTemplate);
		vaultClient = new VaultClient(restTemplate, new VaultEndpoint());
	}

	@Test
	public void loginShouldObtainToken() throws Exception {

		mockRest.expect(requestTo("https://localhost:8200/v1/auth/cert/login")) //
				.andExpect(method(HttpMethod.POST)) //
				.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON)
						.body("{" + "\"auth\":{\"client_token\":\"my-token\", \"renewable\": true, \"lease_duration\": 10}" + "}"));

		ClientCertificateAuthentication sut = new ClientCertificateAuthentication(vaultClient);

		VaultToken login = sut.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");
		assertThat(((LoginToken) login).getLeaseDuration()).isEqualTo(10);
		assertThat(((LoginToken) login).isRenewable()).isTrue();
	}

	@Test(expected = VaultException.class)
	public void loginShouldFail() throws Exception {

		mockRest.expect(requestTo("https://localhost:8200/v1/auth/cert/login")) //
				.andRespond(withServerError());

		new ClientCertificateAuthentication(vaultClient).login();
	}
}
