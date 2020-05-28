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
package org.springframework.vault.core;

import java.util.Collections;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.VaultTokenRequest;
import org.springframework.vault.support.VaultTokenResponse;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Default implementation of {@link VaultTokenOperations}.
 *
 * @author Mark Paluch
 */
public class VaultTokenTemplate implements VaultTokenOperations {

	private final VaultOperations vaultOperations;

	/**
	 * Create a new {@link VaultTokenTemplate} with the given {@link VaultOperations}.
	 * @param vaultOperations must not be {@literal null}.
	 */
	public VaultTokenTemplate(VaultOperations vaultOperations) {

		Assert.notNull(vaultOperations, "VaultOperations must not be null");

		this.vaultOperations = vaultOperations;
	}

	@Override
	public VaultTokenResponse create() {
		return create(VaultTokenRequest.builder().build());
	}

	@Override
	public VaultTokenResponse create(VaultTokenRequest request) {

		Assert.notNull(request, "VaultTokenRequest must not be null");

		return writeAndReturn("auth/token/create", request, VaultTokenResponse.class);
	}

	@Override
	public VaultTokenResponse createOrphan() {
		return createOrphan(VaultTokenRequest.builder().build());
	}

	@Override
	public VaultTokenResponse createOrphan(VaultTokenRequest request) {

		Assert.notNull(request, "VaultTokenRequest must not be null");

		return writeAndReturn("auth/token/create-orphan", request, VaultTokenResponse.class);
	}

	@Override
	public VaultTokenResponse renew(VaultToken vaultToken) {

		Assert.notNull(vaultToken, "VaultToken must not be null");

		return writeAndReturn("auth/token/renew", vaultToken, VaultTokenResponse.class);
	}

	@Override
	public void revoke(VaultToken vaultToken) {

		Assert.notNull(vaultToken, "VaultToken must not be null");

		writeToken("auth/token/revoke", vaultToken, VaultTokenResponse.class);
	}

	@Override
	public void revokeOrphan(VaultToken vaultToken) {

		Assert.notNull(vaultToken, "VaultToken must not be null");

		writeToken("auth/token/revoke-orphan", vaultToken, VaultTokenResponse.class);
	}

	private <T extends VaultResponseSupport<?>> T writeAndReturn(String path, @Nullable Object body,
			Class<T> responseType) {

		Assert.hasText(path, "Path must not be empty");

		T response = this.vaultOperations.doWithSession(restOperations -> {
			try {
				ResponseEntity<T> exchange = restOperations.exchange(path, HttpMethod.POST,
						body == null ? HttpEntity.EMPTY : new HttpEntity<>(body), responseType);

				return exchange.getBody();
			}
			catch (HttpStatusCodeException e) {
				throw VaultResponses.buildException(e, path);
			}
		});

		Assert.state(response != null, "Response must not be null");

		return response;
	}

	@Nullable
	private void writeToken(String path, VaultToken token, Class<?> responseType) {

		Assert.hasText(path, "Path must not be empty");

		this.vaultOperations.doWithSession(restOperations -> {

			try {
				restOperations.exchange(path, HttpMethod.POST,
						new HttpEntity<>(Collections.singletonMap("token", token.getToken())), responseType);

				return null;
			}
			catch (HttpStatusCodeException e) {
				throw VaultResponses.buildException(e, path);
			}
		});
	}

}
