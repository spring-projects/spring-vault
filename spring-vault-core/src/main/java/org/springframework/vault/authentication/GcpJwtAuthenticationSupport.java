/*
 * Copyright 2018-2025 the original author or authors.
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
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestClientException;

/**
 * Base class for GCP JWT-based authentication. Used by framework components.
 *
 * @author Mark Paluch
 * @since 2.1
 */
public abstract class GcpJwtAuthenticationSupport {

	private static final Log logger = LogFactory.getLog(GcpJwtAuthenticationSupport.class);


	private final VaultLoginClient loginClient;

	GcpJwtAuthenticationSupport(VaultLoginClient loginClient) {
		this.loginClient = loginClient;
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
		VaultResponseSupport<LoginToken> response = this.loginClient.loginAt(path).using(login).retrieve().body();

		if (logger.isDebugEnabled()) {

			if (response.getAuth().get("metadata") instanceof Map) {

				Map<Object, Object> metadata = (Map<Object, Object>) response.getAuth().get("metadata");
				logger.debug("Using %s authentication for user id %s".formatted(authenticationName,
						metadata.get("service_account_email")));
			}
		}

		return response.getRequiredData();
	}

	static Map<String, String> createRequestBody(String role, String signedJwt) {
		Map<String, String> login = new HashMap<>();
		login.put("role", role);
		login.put("jwt", signedJwt);
		return login;
	}

}
