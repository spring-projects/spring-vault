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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.RoleId;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.SecretId;
import org.springframework.vault.authentication.AppRoleTokens.AbsentSecretId;
import org.springframework.vault.authentication.AppRoleTokens.Provided;
import org.springframework.vault.authentication.AppRoleTokens.Pull;
import org.springframework.vault.authentication.AppRoleTokens.Wrapped;
import org.springframework.vault.authentication.AuthenticationSteps.Node;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

import static org.springframework.vault.authentication.AuthenticationSteps.HttpRequestBuilder.get;
import static org.springframework.vault.authentication.AuthenticationSteps.HttpRequestBuilder.post;

/**
 * AppRole implementation of {@link ClientAuthentication}. RoleId and SecretId (optional)
 * are sent in the login request to Vault to obtain a {@link VaultToken}.
 * <p>
 * {@link AppRoleAuthentication} can be configured for push and pull mode by setting
 * {@link AppRoleAuthenticationOptions#getSecretId()}.
 *
 * @author Mark Paluch
 * @author Vincent Le Nair
 * @author Christophe Tafani-Dereeper
 * @see AppRoleAuthenticationOptions
 * @see RestOperations
 * @see <a href="https://www.vaultproject.io/docs/auth/approle.html">Auth Backend:
 * AppRole</a>
 */
public class AppRoleAuthentication implements ClientAuthentication,
		AuthenticationStepsFactory {

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

	/**
	 * Creates a {@link AuthenticationSteps} for AppRole authentication given
	 * {@link AppRoleAuthenticationOptions}.
	 *
	 * @param options must not be {@literal null}.
	 * @return {@link AuthenticationSteps} for AppRole authentication.
	 * @since 2.0
	 */
	public static AuthenticationSteps createAuthenticationSteps(
			AppRoleAuthenticationOptions options) {

		Assert.notNull(options, "AppRoleAuthenticationOptions must not be null");

		RoleId roleId = options.getRoleId();
		SecretId secretId = options.getSecretId();

		if ((roleId instanceof Wrapped || roleId instanceof Pull)
				&& (secretId instanceof Wrapped || secretId instanceof Pull)) {

			throw new IllegalArgumentException(
					"RoleId and SecretId are both configured to obtain their values from initial Vault request. AuthenticationSteps supports currently only fetching of a single element.");
		}

		return getAuthenticationSteps(options, roleId, secretId).login(
				"auth/{mount}/login", options.getPath());
	}

	private static Node<?> getAuthenticationSteps(AppRoleAuthenticationOptions options,
			RoleId roleId, SecretId secretId) {

		if (roleId instanceof Pull || roleId instanceof Wrapped) {

			Node<VaultResponse> steps;

			if (roleId instanceof Pull) {

				HttpHeaders headers = createHttpHeaders(((Pull) roleId).getInitialToken());

				steps = AuthenticationSteps.fromHttpRequest(get(
						"auth/{mount}/role/{role}/role-id", options.getPath(),
						options.getAppRole()).with(headers).as(VaultResponse.class));
			}
			else {
				steps = unwrapResponse(((Wrapped) roleId).getInitialToken());
			}

			return steps.map(
					vaultResponse -> (String) vaultResponse.getRequiredData().get(
							"role_id")).map(
					roleIdToken -> {

						return getAppRoleLoginBody(
								roleIdToken,
								secretId instanceof Provided ? ((Provided) secretId)
										.getValue() : null);
					});
		}

		if (secretId instanceof Pull || secretId instanceof Wrapped) {

			Node<VaultResponse> steps;

			if (secretId instanceof Pull) {
				HttpHeaders headers = createHttpHeaders(((Pull) secretId)
						.getInitialToken());

				steps = AuthenticationSteps.fromHttpRequest(post(
						"auth/{mount}/role/{role}/secret-id", options.getPath(),
						options.getAppRole()).with(headers).as(VaultResponse.class));
			}
			else {
				steps = unwrapResponse(((Wrapped) secretId).getInitialToken());
			}

			return steps.map(
					vaultResponse -> (String) vaultResponse.getRequiredData().get(
							"secret_id")).map(
					secretIdToken -> {

						return getAppRoleLoginBody(
								roleId instanceof Provided ? ((Provided) roleId)
										.getValue() : null, secretIdToken);
					});
		}

		if (roleId instanceof Provided) {

			return AuthenticationSteps.fromSupplier(() -> {

				return getAppRoleLoginBody(((Provided) roleId).getValue(),
						secretId instanceof Provided ? ((Provided) secretId).getValue()
								: null);
			});
		}

		throw new IllegalArgumentException(String.format(
				"Provided RoleId/SecretId setup not supported. RoleId: %s, SecretId: %s",
				roleId, secretId));
	}

	private static Node<VaultResponse> unwrapResponse(VaultToken token) {

		return AuthenticationSteps.fromHttpRequest(
				get("cubbyhole/response").with(createHttpHeaders(token)).as(
						VaultResponse.class)).map(
				vaultResponse -> {

					Map<String, Object> data = vaultResponse.getRequiredData();
					return VaultResponses.unwrap((String) data.get("response"),
							VaultResponse.class);
				});
	}

	@Override
	public VaultToken login() {
		return createTokenUsingAppRole();
	}

	@Override
	public AuthenticationSteps getAuthenticationSteps() {
		return createAuthenticationSteps(options);
	}

	private VaultToken createTokenUsingAppRole() {

		Map<String, String> login = getAppRoleLoginBody(options.getRoleId(),
				options.getSecretId());

		try {
			VaultResponse response = restOperations.postForObject("auth/{mount}/login",
					login, VaultResponse.class, options.getPath());

			Assert.state(response != null && response.getAuth() != null,
					"Auth field must not be null");

			logger.debug("Login successful using AppRole authentication");

			return LoginTokenUtil.from(response.getAuth());
		}
		catch (HttpStatusCodeException e) {
			throw new VaultException(String.format("Cannot login using AppRole: %s",
					VaultResponses.getError(e.getResponseBodyAsString())));
		}
	}

	private String getRoleId(RoleId roleId) {

		if (roleId instanceof Provided) {
			return ((Provided) roleId).getValue();
		}

		if (roleId instanceof Pull) {

			VaultToken token = ((Pull) roleId).getInitialToken();

			try {

				ResponseEntity<VaultResponse> entity = restOperations.exchange(
						"auth/{mount}/role/{role}/role-id", HttpMethod.GET,
						createHttpEntity(token), VaultResponse.class, options.getPath(),
						options.getAppRole());
				return (String) entity.getBody().getRequiredData().get("role_id");
			}
			catch (HttpStatusCodeException e) {
				throw new VaultException(String.format(
						"Cannot get Role id using AppRole: %s",
						VaultResponses.getError(e.getResponseBodyAsString())));
			}
		}

		if (roleId instanceof Wrapped) {

			VaultToken token = ((Wrapped) roleId).getInitialToken();

			try {

				ResponseEntity<VaultResponse> entity = restOperations.exchange(
						"cubbyhole/response", HttpMethod.GET, createHttpEntity(token),
						VaultResponse.class);

				Map<String, Object> data = entity.getBody().getRequiredData();
				VaultResponse response = VaultResponses.unwrap(
						(String) data.get("response"), VaultResponse.class);

				return (String) response.getRequiredData().get("role_id");
			}
			catch (HttpStatusCodeException e) {
				throw new VaultException(String.format(
						"Cannot unwrap Role id using AppRole: %s",
						VaultResponses.getError(e.getResponseBodyAsString())));
			}
		}

		throw new IllegalArgumentException("Unknown RoleId configuration: " + roleId);
	}

	private String getSecretId(SecretId secretId) {

		if (secretId instanceof Provided) {
			return ((Provided) secretId).getValue();
		}

		if (secretId instanceof Pull) {

			VaultToken token = ((Pull) secretId).getInitialToken();

			try {
				VaultResponse response = restOperations.postForObject(
						"auth/{mount}/role/{role}/secret-id", createHttpEntity(token),
						VaultResponse.class, options.getPath(), options.getAppRole());
				return (String) response.getRequiredData().get("secret_id");
			}
			catch (HttpStatusCodeException e) {
				throw new VaultException(String.format(
						"Cannot get Secret id using AppRole: %s",
						VaultResponses.getError(e.getResponseBodyAsString())));
			}
		}

		if (secretId instanceof Wrapped) {

			VaultToken token = ((Wrapped) secretId).getInitialToken();

			try {

				ResponseEntity<VaultResponse> entity = restOperations.exchange(
						"cubbyhole/response", HttpMethod.GET, createHttpEntity(token),
						VaultResponse.class);

				Map<String, Object> data = entity.getBody().getRequiredData();
				VaultResponse response = VaultResponses.unwrap(
						(String) data.get("response"), VaultResponse.class);

				return (String) response.getRequiredData().get("secret_id");
			}
			catch (HttpStatusCodeException e) {
				throw new VaultException(String.format(
						"Cannot unwrap Role id using AppRole: %s",
						VaultResponses.getError(e.getResponseBodyAsString())));
			}
		}

		throw new IllegalArgumentException("Unknown SecretId configuration: " + secretId);
	}

	private static HttpHeaders createHttpHeaders(VaultToken token) {

		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Vault-Token", token.getToken());

		return headers;
	}

	private static HttpEntity createHttpEntity(VaultToken token) {
		return new HttpEntity<String>(null, createHttpHeaders(token));
	}

	private Map<String, String> getAppRoleLoginBody(RoleId roleId, SecretId secretId) {

		Map<String, String> login = new HashMap<>();

		login.put("role_id", getRoleId(roleId));

		if (!ClassUtils.isAssignableValue(AbsentSecretId.class, secretId)) {
			login.put("secret_id", getSecretId(secretId));
		}

		return login;
	}

	private static Map<String, String> getAppRoleLoginBody(String roleId,
			@Nullable String secretId) {

		Map<String, String> login = new HashMap<>();

		login.put("role_id", roleId);

		if (secretId != null) {
			login.put("secret_id", secretId);
		}

		return login;
	}
}
