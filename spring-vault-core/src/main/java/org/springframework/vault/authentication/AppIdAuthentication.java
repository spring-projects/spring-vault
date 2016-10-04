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

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultException;
import org.springframework.vault.client.VaultResponseEntity;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;

/**
 * AppId implementation of {@link ClientAuthentication}. {@link AppIdAuthentication} uses a configured
 * {@link AppIdUserIdMechanism} to obtain or calculate a UserId. AppId and UserId are sent in the login request to Vault
 * to obtain a {@link VaultToken}.
 *
 * @author Mark Paluch
 * @see AppIdAuthenticationOptions
 * @see VaultClient
 * @see <a href="https://www.vaultproject.io/docs/auth/app-id.html">Auth Backend: App ID</a>
 */
public class AppIdAuthentication implements ClientAuthentication {

	private final static Logger logger = LoggerFactory.getLogger(AppIdAuthentication.class);

	private final AppIdAuthenticationOptions options;

	private final VaultClient vaultClient;

	/**
	 * Creates a {@link AppIdAuthentication} using {@link AppIdAuthenticationOptions} and {@link VaultClient}.
	 *
	 * @param options must not be {@literal null}.
	 * @param vaultClient must not be {@literal null}.
	 */
	public AppIdAuthentication(AppIdAuthenticationOptions options, VaultClient vaultClient) {

		Assert.notNull(options, "AppIdAuthenticationOptions must not be null");
		Assert.notNull(vaultClient, "VaultClient must not be null");

		this.options = options;
		this.vaultClient = vaultClient;
	}

	@Override
	public VaultToken login() {
		return createTokenUsingAppId();
	}

	private VaultToken createTokenUsingAppId() {

		Map<String, String> login = getAppIdLogin(options.getAppId(), options.getUserIdMechanism().createUserId());

		VaultResponseEntity<VaultResponse> entity = vaultClient
				.postForEntity(String.format("auth/%s/login", options.getPath()), login, VaultResponse.class);

		if (!entity.isSuccessful()) {
			throw new VaultException(String.format("Cannot login using app-id: %s", entity.getMessage()));
		}

		logger.debug("Login successful using AppId authentication");

		return LoginTokenUtil.from(entity.getBody().getAuth());
	}

	private Map<String, String> getAppIdLogin(String appId, String userId) {

		Map<String, String> login = new HashMap<String, String>();
		login.put("app_id", appId);
		login.put("user_id", userId);
		return login;
	}
}
