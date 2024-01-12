/*
 * Copyright 2017-2024 the original author or authors.
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
package org.springframework.vault.client;

import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.vault.support.VaultToken;

/**
 * Class providing utility methods to create Vault HTTP headers.
 *
 * @author Mark Paluch
 */
public abstract class VaultHttpHeaders {

	/**
	 * The HTTP {@code X-Vault-Token} header field name.
	 */
	public static final String VAULT_TOKEN = "X-Vault-Token";

	/**
	 * The HTTP {@code X-Vault-Namespace} header field name.
	 *
	 * @since 2.2
	 */
	public static final String VAULT_NAMESPACE = "X-Vault-Namespace";

	private VaultHttpHeaders() {
	}

	/**
	 * Create {@link HttpHeaders} given {@link VaultToken}. The resulting object can be
	 * used to authenticate HTTP requests.
	 * @param vaultToken must not be {@literal null}.
	 * @return {@link HttpHeaders} containing the {@link VaultToken}.
	 */
	public static HttpHeaders from(VaultToken vaultToken) {

		Assert.notNull(vaultToken, "VaultToken must not be null");

		HttpHeaders headers = new HttpHeaders();
		headers.add(VAULT_TOKEN, vaultToken.getToken());

		return headers;
	}

}
