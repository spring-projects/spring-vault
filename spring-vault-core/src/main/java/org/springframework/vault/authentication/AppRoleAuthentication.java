/*
 * Copyright 2016-2024 the original author or authors.
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

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.RoleId;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.SecretId;
import org.springframework.vault.authentication.AppRoleTokens.AbsentSecretId;
import org.springframework.vault.authentication.AppRoleTokens.Provided;
import org.springframework.vault.authentication.AppRoleTokens.Pull;
import org.springframework.vault.authentication.AppRoleTokens.Wrapped;
import org.springframework.vault.authentication.AuthenticationSteps.Node;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import static org.springframework.vault.authentication.AuthenticationSteps.HttpRequestBuilder.*;
import static org.springframework.vault.authentication.AuthenticationUtil.*;

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
public class AppRoleAuthentication implements ClientAuthentication, AuthenticationStepsFactory {

	private static final Log logger = LogFactory.getLog(AppRoleAuthentication.class);

	private final AppRoleAuthenticationOptions options;

	private final RestOperations restOperations;

	/**
	 * Create a {@link AppRoleAuthentication} using {@link AppRoleAuthenticationOptions}
	 * and {@link RestOperations}.
	 * @param options must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 */
	public AppRoleAuthentication(AppRoleAuthenticationOptions options, RestOperations restOperations) {

		Assert.notNull(options, "AppRoleAuthenticationOptions must not be null");
		Assert.notNull(restOperations, "RestOperations must not be null");

		this.options = options;
		this.restOperations = restOperations;
	}

	/**
	 * Creates a {@link AuthenticationSteps} for AppRole authentication given
	 * {@link AppRoleAuthenticationOptions}.
	 * @param options must not be {@literal null}.
	 * @return {@link AuthenticationSteps} for AppRole authentication.
	 * @since 2.0
	 */
	public static AuthenticationSteps createAuthenticationSteps(AppRoleAuthenticationOptions options) {

		Assert.notNull(options, "AppRoleAuthenticationOptions must not be null");

		RoleId roleId = options.getRoleId();
		SecretId secretId = options.getSecretId();

		return getAuthenticationSteps(options, roleId, secretId).login(getLoginPath(options.getPath()));
	}

	private static Node<Map<String, String>> getAuthenticationSteps(AppRoleAuthenticationOptions options, RoleId roleId,
			SecretId secretId) {

		Node<String> roleIdSteps = getRoleIdSteps(options, roleId);

		if (!hasSecretId(options.getSecretId())) {
			return roleIdSteps.map(it -> getAppRoleLoginBody(it, null));
		}

		Node<String> secretIdSteps = getSecretIdSteps(options, secretId);

		return roleIdSteps.zipWith(secretIdSteps).map(it -> getAppRoleLoginBody(it.getLeft(), it.getRight()));
	}

	private static Node<String> getRoleIdSteps(AppRoleAuthenticationOptions options, RoleId roleId) {

		if (roleId instanceof Provided) {
			return AuthenticationSteps.fromValue(((Provided) roleId).getValue());
		}

		if (roleId instanceof Pull) {

			HttpHeaders headers = createHttpHeaders(((Pull) roleId).getInitialToken());

			return AuthenticationSteps
				.fromHttpRequest(get(getRoleIdIdPath(options)).with(headers).as(VaultResponse.class))
				.map(vaultResponse -> (String) vaultResponse.getRequiredData().get("role_id"));
		}

		if (roleId instanceof Wrapped) {
			return unwrapResponse(options.getUnwrappingEndpoints(), ((Wrapped) roleId).getInitialToken())
				.map(vaultResponse -> (String) vaultResponse.getRequiredData().get("role_id"));
		}

		throw new IllegalArgumentException("Unknown RoleId configuration: " + roleId);
	}

	private static Node<String> getSecretIdSteps(AppRoleAuthenticationOptions options, SecretId secretId) {

		if (secretId instanceof Provided) {
			return AuthenticationSteps.fromValue(((Provided) secretId).getValue());
		}

		if (secretId instanceof Pull) {
			HttpHeaders headers = createHttpHeaders(((Pull) secretId).getInitialToken());

			return AuthenticationSteps
				.fromHttpRequest(post(getSecretIdPath(options)).with(headers).as(VaultResponse.class))
				.map(vaultResponse -> (String) vaultResponse.getRequiredData().get("secret_id"));
		}

		if (secretId instanceof Wrapped) {

			return unwrapResponse(options.getUnwrappingEndpoints(), ((Wrapped) secretId).getInitialToken())
				.map(vaultResponse -> (String) vaultResponse.getRequiredData().get("secret_id"));
		}

		throw new IllegalArgumentException("Unknown SecretId configuration: " + secretId);

	}

	private static Node<VaultResponse> unwrapResponse(UnwrappingEndpoints unwrappingEndpoints, VaultToken token) {

		return AuthenticationSteps
			.fromHttpRequest(method(unwrappingEndpoints.getUnwrapRequestMethod(), unwrappingEndpoints.getPath())
				.with(createHttpHeaders(token))
				.as(VaultResponse.class))
			.map(unwrappingEndpoints::unwrap);
	}

	@Override
	public VaultToken login() {
		return createTokenUsingAppRole();
	}

	@Override
	public AuthenticationSteps getAuthenticationSteps() {
		return createAuthenticationSteps(this.options);
	}

	private VaultToken createTokenUsingAppRole() {

		Map<String, String> login = getAppRoleLoginBody(this.options.getRoleId(), this.options.getSecretId());

		try {
			VaultResponse response = this.restOperations.postForObject(getLoginPath(this.options.getPath()), login,
					VaultResponse.class);

			Assert.state(response != null && response.getAuth() != null, "Auth field must not be null");

			logger.debug("Login successful using AppRole authentication");

			return LoginTokenUtil.from(response.getAuth());
		}
		catch (RestClientException e) {
			throw VaultLoginException.create("AppRole", e);
		}
	}

	private String getRoleId(RoleId roleId) throws VaultLoginException {

		if (roleId instanceof Provided) {
			return ((Provided) roleId).getValue();
		}

		if (roleId instanceof Pull) {

			VaultToken token = ((Pull) roleId).getInitialToken();

			try {

				ResponseEntity<VaultResponse> entity = this.restOperations.exchange(getRoleIdIdPath(this.options),
						HttpMethod.GET, createHttpEntity(token), VaultResponse.class);
				return (String) entity.getBody().getRequiredData().get("role_id");
			}
			catch (HttpStatusCodeException e) {
				throw new VaultLoginException(String.format("Cannot get Role id using AppRole: %s",
						VaultResponses.getError(e.getResponseBodyAsString())), e);
			}
		}

		if (roleId instanceof Wrapped) {

			VaultToken token = ((Wrapped) roleId).getInitialToken();

			try {
				UnwrappingEndpoints unwrappingEndpoints = this.options.getUnwrappingEndpoints();
				ResponseEntity<VaultResponse> entity = this.restOperations.exchange(unwrappingEndpoints.getPath(),
						unwrappingEndpoints.getUnwrapRequestMethod(), createHttpEntity(token), VaultResponse.class);

				VaultResponse response = unwrappingEndpoints.unwrap(entity.getBody());

				return (String) response.getRequiredData().get("role_id");
			}
			catch (HttpStatusCodeException e) {
				throw new VaultLoginException(String.format("Cannot unwrap Role id using AppRole: %s",
						VaultResponses.getError(e.getResponseBodyAsString())), e);
			}
		}

		throw new IllegalArgumentException("Unknown RoleId configuration: " + roleId);
	}

	private String getSecretId(SecretId secretId) throws VaultLoginException {

		if (secretId instanceof Provided) {
			return ((Provided) secretId).getValue();
		}

		if (secretId instanceof Pull) {

			VaultToken token = ((Pull) secretId).getInitialToken();

			try {
				VaultResponse response = this.restOperations.postForObject(getSecretIdPath(this.options),
						createHttpEntity(token), VaultResponse.class);
				return (String) response.getRequiredData().get("secret_id");
			}
			catch (HttpStatusCodeException e) {
				throw new VaultLoginException(String.format("Cannot get Secret id using AppRole: %s",
						VaultResponses.getError(e.getResponseBodyAsString())), e);
			}
		}

		if (secretId instanceof Wrapped) {

			VaultToken token = ((Wrapped) secretId).getInitialToken();

			try {

				UnwrappingEndpoints unwrappingEndpoints = this.options.getUnwrappingEndpoints();
				ResponseEntity<VaultResponse> entity = this.restOperations.exchange(unwrappingEndpoints.getPath(),
						unwrappingEndpoints.getUnwrapRequestMethod(), createHttpEntity(token), VaultResponse.class);

				VaultResponse response = unwrappingEndpoints.unwrap(entity.getBody());

				return (String) response.getRequiredData().get("secret_id");
			}
			catch (HttpStatusCodeException e) {
				throw new VaultLoginException(String.format("Cannot unwrap Role id using AppRole: %s",
						VaultResponses.getError(e.getResponseBodyAsString())), e);
			}
		}

		throw new IllegalArgumentException("Unknown SecretId configuration: " + secretId);
	}

	private static HttpHeaders createHttpHeaders(VaultToken token) {

		HttpHeaders headers = new HttpHeaders();
		headers.set(VaultHttpHeaders.VAULT_TOKEN, token.getToken());

		return headers;
	}

	private static HttpEntity<String> createHttpEntity(VaultToken token) {
		return new HttpEntity<>(null, createHttpHeaders(token));
	}

	private Map<String, String> getAppRoleLoginBody(RoleId roleId, SecretId secretId) {

		Map<String, String> login = new HashMap<>();

		login.put("role_id", getRoleId(roleId));

		if (hasSecretId(secretId)) {
			login.put("secret_id", getSecretId(secretId));
		}

		return login;
	}

	private static boolean hasSecretId(SecretId secretId) {
		return !ClassUtils.isAssignableValue(AbsentSecretId.class, secretId);
	}

	private static Map<String, String> getAppRoleLoginBody(String roleId, @Nullable String secretId) {

		Map<String, String> login = new HashMap<>();

		login.put("role_id", roleId);

		if (secretId != null) {
			login.put("secret_id", secretId);
		}

		return login;
	}

	private static String getSecretIdPath(AppRoleAuthenticationOptions options) {
		return String.format("auth/%s/role/%s/secret-id", options.getPath(), options.getAppRole());
	}

	private static String getRoleIdIdPath(AppRoleAuthenticationOptions options) {
		return String.format("auth/%s/role/%s/role-id", options.getPath(), options.getAppRole());
	}

}
