/*
 * Copyright 2017-2025 the original author or authors.
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

import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

/**
 * Adapts tokens created by a {@link ClientAuthentication} to a
 * {@link LoginToken}. Allows decoration of a {@link ClientAuthentication}
 * object to perform a self-lookup after token retrieval to obtain the remaining
 * TTL, renewability, accessor and token type.
 * <p>Using this adapter decrements the usage counter for the created token.
 *
 * @author Mark Paluch
 * @since 1.1
 * @see LoginToken
 */
public class LoginTokenAdapter implements ClientAuthentication {

	private final ClientAuthentication delegate;

	private final VaultClient client;


	/**
	 * Create a new {@link LoginTokenAdapter} given {@link ClientAuthentication} to
	 * decorate and {@link RestOperations}.
	 * @param delegate must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 * @deprecated since 4.1, use
	 * {@link #LoginTokenAdapter(ClientAuthentication, VaultClient)} instead.
	 */
	@Deprecated(since = "4.1")
	public LoginTokenAdapter(ClientAuthentication delegate, RestOperations restOperations) {
		Assert.notNull(delegate, "ClientAuthentication delegate must not be null");
		Assert.notNull(restOperations, "RestOperations must not be null");
		this.delegate = delegate;
		this.client = ClientAdapter.from(restOperations).vaultClient();
	}

	/**
	 * Create a new {@link LoginTokenAdapter} given {@link ClientAuthentication} to
	 * decorate and {@link ClientAdapter}.
	 * @param delegate must not be {@literal null}.
	 * @param client must not be {@literal null}.
	 * @since 4.1
	 */
	public LoginTokenAdapter(ClientAuthentication delegate, VaultClient client) {
		Assert.notNull(delegate, "ClientAuthentication delegate must not be null");
		Assert.notNull(client, "ClientAdapter must not be null");
		this.delegate = delegate;
		this.client = client;
	}


	@Override
	public LoginToken login() throws VaultException {
		return augmentWithSelfLookup(this.delegate.login());
	}

	private LoginToken augmentWithSelfLookup(VaultToken token) {
		return augmentWithSelfLookup(this.client, token);
	}

	static LoginToken augmentWithSelfLookup(VaultClient client, VaultToken token) {

		Map<String, Object> data = lookupSelf(client, token);

		return LoginToken.from(token.toCharArray(), data);
	}

	private static Map<String, Object> lookupSelf(VaultClient client, VaultToken token) {
		try {
			VaultResponse response = client.get().path("auth/token/lookup-self").token(token).retrieve().requiredBody();
			return response.getRequiredData();
		} catch (VaultException e) {

			if (e.getCause() instanceof HttpStatusCodeException hse) {
				throw new VaultTokenLookupException("Token self-lookup failed: %s %s".formatted(hse.getStatusCode(),
						VaultResponses.getError(hse.getResponseBodyAsString())), e);
			}

			if (e.getCause() instanceof RestClientException rce) {
				throw new VaultTokenLookupException("Token self-lookup failed", rce);
			}
			throw e;
		} catch (HttpStatusCodeException e) {
			throw new VaultTokenLookupException("Token self-lookup failed: %s %s".formatted(e.getStatusCode(),
					VaultResponses.getError(e.getResponseBodyAsString())), e);
		} catch (RestClientException e) {
			throw new VaultTokenLookupException("Token self-lookup failed", e);
		}
	}

}
