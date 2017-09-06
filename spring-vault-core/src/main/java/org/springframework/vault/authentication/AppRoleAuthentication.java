/*
 * Copyright 2016-2017 the original author or authors.
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

/**
 * AppRole implementation of {@link ClientAuthentication}. RoleId and SecretId (optional)
 * are sent in the login request to Vault to obtain a {@link VaultToken}.
 * <p>
 * {@link AppRoleAuthentication} can be configured for push and pull mode by setting
 * {@link AppRoleAuthenticationOptions#getSecretId()}.
 *
 * @author Mark Paluch
 * @see AppRoleAuthenticationOptions
 * @see RestOperations
 * @see <a href="https://www.vaultproject.io/docs/auth/approle.html">Auth Backend:
 * AppRole</a>
 */
public class AppRoleAuthentication implements ClientAuthentication {

	private static final Log logger = LogFactory.getLog(AppRoleAuthentication.class);

	private final AppRoleAuthenticationOptions options;

	private final RestOperations restOperations;

	/**
	 * Create a {@link AppRoleAuthentication} using {@link AppRoleAuthenticationOptions}
	 * and {@link RestOperations}.
	 *
	 * @param options must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 */
	public AppRoleAuthentication(AppRoleAuthenticationOptions options,
			RestOperations restOperations) {

		Assert.notNull(options, "AppRoleAuthenticationOptions must not be null");
		Assert.notNull(restOperations, "RestOperations must not be null");

		this.options = options;
		this.restOperations = restOperations;
	}

	@Override public VaultToken login() {
		return createTokenUsingAppRole();
	}

	private VaultToken createTokenUsingAppRole() {

		String roleId = getRoleId();
		String secretId = getSecretId();

		Map<String, String> login = getAppRoleLogin(roleId, secretId);

		try {
			VaultResponse response = restOperations
				.postForObject("auth/{mount}/login", login, VaultResponse.class,
					options.getPath());

			logger.debug("Login successful using AppRole authentication");

			return LoginTokenUtil.from(response.getAuth());
		}
		catch (HttpStatusCodeException e) {
			throw new VaultException(String.format("Cannot login using AppRole: %s",
				VaultResponses.getError(e.getResponseBodyAsString())));
		}
	}

	private String getRoleId() {
		String roleId = options.getRoleId();
		if (StringUtils.isEmpty(roleId) && !StringUtils.isEmpty(options.getAppRole())) {
			try {
				ResponseEntity<VaultResponse> response = restOperations
					.exchange("auth/approle/role/{role}/role-id", HttpMethod.GET,
						createHttpEntityWithToken(), VaultResponse.class,
						options.getAppRole());
				roleId = (String) response.getBody().getData().get("role_id");
			}
			catch (HttpStatusCodeException e) {
				throw new VaultException(String
					.format("Cannot get Role id using AppRole: %s",
						VaultResponses.getError(e.getResponseBodyAsString())));
			}
		}
		return roleId;

	}

	private String getSecretId() {
		String secretId = options.getSecretId();
		if (StringUtils.isEmpty(secretId) && !StringUtils.isEmpty(options.getAppRole())) {
			try {
				VaultResponse response = restOperations
					.postForObject("auth/approle/role/{role}/secret-id",
						createHttpEntityWithToken(), VaultResponse.class,
						options.getAppRole());
				secretId = (String) response.getData().get("secret_id");
			}
			catch (HttpStatusCodeException e) {
				throw new VaultException(String
					.format("Cannot get Secret id using AppRole: %s",
						VaultResponses.getError(e.getResponseBodyAsString())));
			}
		}
		return secretId;

	}

	private HttpEntity createHttpEntityWithToken() {
		HttpHeaders headers = new HttpHeaders();
		if (options.getInitialToken() != null) {
			headers.set("X-Vault-Token", options.getInitialToken());
		}
		return new HttpEntity<String>(null, headers);
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
