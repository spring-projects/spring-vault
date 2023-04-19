/*
 * Copyright 2017-2022 the original author or authors.
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

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;

import org.springframework.vault.support.Policy;
import org.springframework.vault.util.IntegrationTestSupport;

import static java.util.Collections.*;
import static org.springframework.vault.authentication.UsernamePasswordAuthenticationOptions.*;
import static org.springframework.vault.support.Policy.BuiltinCapabilities.*;

/**
 * Integration test base class for {@link UsernamePasswordAuthentication} tests.
 *
 * @author Mikhael Sokolov
 */
public abstract class UsernamePasswordAuthenticationIntegrationTestBase extends IntegrationTestSupport {

	static final Policy POLICY = Policy.of(Policy.Rule.builder().path("/*").capabilities(READ, CREATE, UPDATE).build());

	protected final String username = "admin";

	protected final String password = "qwerty";

	@BeforeEach
	public void before() {

		if (!prepare().hasAuth(DEFAULT_USERPASS_AUTHENTICATION_PATH)) {
			prepare().mountAuth(DEFAULT_USERPASS_AUTHENTICATION_PATH);
		}

		prepare().getVaultOperations().opsForSys().createOrUpdatePolicy(DEFAULT_USERPASS_AUTHENTICATION_PATH, POLICY);
		prepare().getVaultOperations()
			.doWithSession(restOperations -> restOperations.postForEntity(
					String.format("auth/%s/users/%s", DEFAULT_USERPASS_AUTHENTICATION_PATH, username),
					singletonMap("password", password), Map.class));
	}

}
