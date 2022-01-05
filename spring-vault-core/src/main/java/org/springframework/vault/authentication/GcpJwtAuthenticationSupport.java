/*
 * Copyright 2018-2022 the original author or authors.
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
 * Base class for GCP JWT-based authentication. Used by framework components.
 *
 * @author Mark Paluch
 * @since 2.1
 */
public abstract class GcpJwtAuthenticationSupport {

	private static final Log logger = LogFactory.getLog(GcpJwtAuthenticationSupport.class);

	private final RestOperations restOperations;

	GcpJwtAuthenticationSupport(RestOperations restOperations) {

		Assert.notNull(restOperations, "Vault RestOperations must not be null");

		this.restOperations = restOperations;
	}

	/**
	 * Perform the actual Vault login given {@code signedJwt}.
	 * @param authenticationName authentication name for logging.
	 * @param signedJwt the JSON web token.
	 * @param path GCP authentication mount path.
	 * @param role Vault role.
	 * @return the {@link VaultToken}.
	 */
	VaultToken doLogin(String authenticationName, String signedJwt, String path, String role) {

		Map<String, String> login = createRequestBody(role, signedJwt);

		try {

			VaultResponse response = this.restOperations.postForObject(AuthenticationUtil.getLoginPath(path), login,
					VaultResponse.class);

			Assert.state(response != null && response.getAuth() != null, "Auth field must not be null");

			if (logger.isDebugEnabled()) {

				if (response.getAuth().get("metadata") instanceof Map) {

					Map<Object, Object> metadata = (Map<Object, Object>) response.getAuth().get("metadata");
					logger.debug(String.format("Login successful using %s authentication for user id %s",
							authenticationName, metadata.get("service_account_email")));
				}
				else {
					logger.debug("Login successful using " + authenticationName + " authentication");
				}
			}

			return LoginTokenUtil.from(response.getAuth());
		}
		catch (RestClientException e) {
			throw VaultLoginException.create(authenticationName, e);
		}
	}

	static Map<String, String> createRequestBody(String role, String signedJwt) {

		Map<String, String> login = new HashMap<>();

		login.put("role", role);
		login.put("jwt", signedJwt);

		return login;
	}

}
