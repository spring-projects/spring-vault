/*
 * Copyright 2016-2018 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import org.springframework.vault.VaultException;
import org.springframework.vault.core.RestOperationsCallback;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestRestTemplateFactory;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AppIdAuthentication}.
 *
 * @author Mark Paluch
 */
public class AppIdAuthenticationIntegrationTests extends IntegrationTestSupport {

	@Before
	public void before() throws Exception {

		if (!prepare().hasAuth("app-id")) {
			prepare().mountAuth("app-id");
		}

		prepare().getVaultOperations().doWithSession(
				new RestOperationsCallback<Object>() {
					@Override
					public Object doWithRestOperations(RestOperations restOperations) {

						Map<String, String> appIdData = new HashMap<String, String>();
						appIdData.put("value", "dummy"); // policy
						appIdData.put("display_name", "this is my test application");

						restOperations.postForEntity("auth/app-id/map/app-id/myapp",
								appIdData, Map.class);

						Map<String, String> userIdData = new HashMap<String, String>();
						userIdData.put("value", "myapp"); // name of the app-id
						userIdData.put("cidr_block", "0.0.0.0/0");

						restOperations.postForEntity(
								"auth/app-id/map/user-id/static-userid-value",
								userIdData, Map.class);

						return null;
					}
				});
	}

	@Test
	public void shouldLoginSuccessfully() throws Exception {

		AppIdAuthenticationOptions options = AppIdAuthenticationOptions.builder()
				.appId("myapp") //
				.userIdMechanism(new StaticUserId("static-userid-value")) //
				.build();

		RestTemplate restTemplate = TestRestTemplateFactory.create(Settings
				.createSslConfiguration());

		AppIdAuthentication authentication = new AppIdAuthentication(options,
				restTemplate);
		VaultToken login = authentication.login();

		assertThat(login.getToken()).isNotEmpty();
	}

	@Test(expected = VaultException.class)
	public void loginShouldFail() throws Exception {

		AppIdAuthenticationOptions options = AppIdAuthenticationOptions.builder()
				.appId("wrong") //
				.userIdMechanism(new StaticUserId("wrong")) //
				.build();

		RestTemplate restTemplate = TestRestTemplateFactory.create(Settings
				.createSslConfiguration());

		new AppIdAuthentication(options, restTemplate).login();
	}
}
