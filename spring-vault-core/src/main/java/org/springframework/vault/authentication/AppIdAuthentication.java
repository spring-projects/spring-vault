/*
 * Copyright 2016-2020 the original author or authors.
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

/**
 * AppId implementation of {@link ClientAuthentication}. {@link AppIdAuthentication} uses
 * a configured {@link AppIdUserIdMechanism} to obtain or calculate a UserId. AppId and
 * UserId are sent in the login request to Vault to obtain a {@link VaultToken}.
 *
 * @author Mark Paluch
 * @see AppIdAuthenticationOptions
 * @see RestOperations
 * @see <a href="https://www.vaultproject.io/docs/auth/app-id.html">Auth Backend: App
 * ID</a>
 * @deprecated since 2.2. Use {@link AppRoleAuthentication}.
 */
@Deprecated
public class AppIdAuthentication
		implements ClientAuthentication, AuthenticationStepsFactory {

	private static final Log logger = LogFactory.getLog(AppIdAuthentication.class);

	private final AppIdAuthenticationOptions options;

	private final RestOperations restOperations;

	/**
	 * Create a {@link AppIdAuthentication} using {@link AppIdAuthenticationOptions} and
	 * {@link RestOperations}.
	 *
	 * @param options must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 */
	public AppIdAuthentication(AppIdAuthenticationOptions options,
			RestOperations restOperations) {

		Assert.notNull(options, "AppIdAuthenticationOptions must not be null");
		Assert.notNull(restOperations, "RestOperations must not be null");

		this.options = options;
		this.restOperations = restOperations;
	}

	/**
	 * Creates a {@link AuthenticationSteps} for AppId authentication given
	 * {@link AppIdAuthenticationOptions}.
	 *
	 * @param options must not be {@literal null}.
	 * @return {@link AuthenticationSteps} for AppId authentication.
	 * @since 2.0
	 */
	public static AuthenticationSteps createAuthenticationSteps(
			AppIdAuthenticationOptions options) {

		Assert.notNull(options, "AppIdAuthenticationOptions must not be null");

		return AuthenticationSteps
				.fromSupplier(() -> getAppIdLogin(options.getAppId(),
						options.getUserIdMechanism().createUserId())) //
				.login(AuthenticationUtil.getLoginPath(options.getPath()));
	}

	@Override
	public VaultToken login() {
		return createTokenUsingAppId();
	}

	@Override
	public AuthenticationSteps getAuthenticationSteps() {
		return createAuthenticationSteps(options);
	}

	private VaultToken createTokenUsingAppId() {

		Map<String, String> login = getAppIdLogin(options.getAppId(),
				options.getUserIdMechanism().createUserId());

		try {
			VaultResponse response = restOperations.postForObject(AuthenticationUtil.getLoginPath(options.getPath()),
					login, VaultResponse.class);

			Assert.state(response != null && response.getAuth() != null,
					"Auth field must not be null");

			logger.debug("Login successful using AppId authentication");

			return LoginTokenUtil.from(response.getAuth());
		}
		catch (RestClientException e) {
			throw VaultLoginException.create("app-id", e);
		}
	}

	private static Map<String, String> getAppIdLogin(String appId, String userId) {

		Map<String, String> login = new HashMap<>();

		login.put("app_id", appId);
		login.put("user_id", userId);

		return login;
	}
}
