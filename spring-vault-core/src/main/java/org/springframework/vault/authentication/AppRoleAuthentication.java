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
 * AppRole implementation of {@link ClientAuthentication}. RoleId and SecretId (optional) are sent in the login request
 * to Vault to obtain a {@link VaultToken}.
 * <p>
 * {@link AppRoleAuthentication} can be configured for push and pull mode by setting
 * {@link AppRoleAuthenticationOptions#getSecretId()}.
 *
 * @author Mark Paluch
 * @see AppRoleAuthenticationOptions
 * @see VaultClient
 * @see <a href="https://www.vaultproject.io/docs/auth/approle.html">Auth Backend: AppRole</a>
 */
public class AppRoleAuthentication implements ClientAuthentication {

	private final static Logger logger = LoggerFactory.getLogger(AppRoleAuthentication.class);

	private final AppRoleAuthenticationOptions options;

	private final VaultClient vaultClient;

	/**
	 * Creates a {@link AppRoleAuthentication} using {@link AppRoleAuthenticationOptions} and {@link VaultClient}.
	 *
	 * @param options must not be {@literal null}.
	 * @param vaultClient must not be {@literal null}.
	 */
	public AppRoleAuthentication(AppRoleAuthenticationOptions options, VaultClient vaultClient) {

		Assert.notNull(options, "AppRoleAuthenticationOptions must not be null");
		Assert.notNull(vaultClient, "VaultClient must not be null");

		this.options = options;
		this.vaultClient = vaultClient;
	}

	@Override
	public VaultToken login() {
		return createTokenUsingAppRole();
	}

	private VaultToken createTokenUsingAppRole() {

		Map<String, String> login = getAppRoleLogin(options.getRoleId(), options.getSecretId());

		VaultResponseEntity<VaultResponse> entity = vaultClient
				.postForEntity(String.format("auth/%s/login", options.getPath()), login, VaultResponse.class);

		if (!entity.isSuccessful()) {
			throw new VaultException(String.format("Cannot login using AppRole: %s", entity.getMessage()));
		}

		logger.debug("Login successful using AppRole authentication");

		return LoginTokenUtil.from(entity.getBody().getAuth());
	}

	private Map<String, String> getAppRoleLogin(String roleId, String secretId) {

		Map<String, String> login = new HashMap<String, String>();
		login.put("role_id", roleId);
		if (secretId != null) {
			login.put("secret_id", secretId);
		}
		return login;
	}
}
