/*
 * Copyright 2017-2020 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;

import org.springframework.vault.util.IntegrationTestSupport;

/**
 * Integration test base class for {@link AppIdAuthentication} tests.
 *
 * @author Mark Paluch
 */
public abstract class AppIdAuthenticationIntegrationTestBase extends
		IntegrationTestSupport {

	@Before
	public void before() {

		if (!prepare().hasAuth("app-id")) {
			prepare().mountAuth("app-id");
		}

		prepare().getVaultOperations().doWithSession(restOperations -> {

			Map<String, String> appIdData = new HashMap<String, String>();
			appIdData.put("value", "dummy"); // policy
				appIdData.put("display_name", "this is my test application");

				restOperations.postForEntity("auth/app-id/map/app-id/myapp", appIdData,
						Map.class);

				Map<String, String> userIdData = new HashMap<String, String>();
				userIdData.put("value", "myapp"); // name of the app-id
				userIdData.put("cidr_block", "0.0.0.0/0");

				restOperations.postForEntity(
						"auth/app-id/map/user-id/static-userid-value", userIdData,
						Map.class);

				return null;
			});
	}

}
