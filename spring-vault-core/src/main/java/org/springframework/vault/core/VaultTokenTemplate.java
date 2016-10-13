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
package org.springframework.vault.core;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.client.VaultException;
import org.springframework.vault.client.VaultResponseEntity;
import org.springframework.vault.core.VaultOperations.SessionCallback;
import org.springframework.vault.core.VaultOperations.VaultSession;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.VaultTokenRequest;
import org.springframework.vault.support.VaultTokenResponse;

/**
 * Default implementation of {@link VaultTokenOperations}.
 * 
 * @author Mark Paluch
 */
public class VaultTokenTemplate implements VaultTokenOperations {

	private final VaultOperations vaultOperations;

	/**
	 * Creates a new {@link VaultTokenTemplate} with the given {@link VaultOperations}.
	 *
	 * @param vaultOperations must not be {@literal null}.
	 */
	public VaultTokenTemplate(VaultOperations vaultOperations) {

		Assert.notNull(vaultOperations);

		this.vaultOperations = vaultOperations;
	}

	@Override
	public VaultTokenResponse create() {
		return create(VaultTokenRequest.builder().build());
	}

	@Override
	public VaultTokenResponse create(VaultTokenRequest request) {

		Assert.notNull(request, "VaultTokenRequest must not be null");

		return vaultOperations.doWithVault(new CreateToken("auth/token/create", request));
	}

	@Override
	public VaultTokenResponse createOrphan() {
		return createOrphan(VaultTokenRequest.builder().build());
	}

	@Override
	public VaultTokenResponse createOrphan(VaultTokenRequest request) {

		Assert.notNull(request, "VaultTokenRequest must not be null");

		return vaultOperations.doWithVault(new CreateToken("auth/token/create-orphan",
				request));
	}

	@Override
	public VaultTokenResponse renew(VaultToken vaultToken) {

		Assert.notNull(vaultToken, "VaultToken must not be null");

		return vaultOperations.doWithVault(new RenewToken(String.format(
				"auth/token/renew/%s", vaultToken.getToken())));
	}

	@Override
	public void revoke(VaultToken vaultToken) {

		Assert.notNull(vaultToken, "VaultToken must not be null");

		vaultOperations.doWithVault(new RevokeToken(String.format("auth/token/revoke/%s",
				vaultToken.getToken())));
	}

	@Override
	public void revokeOrphan(VaultToken vaultToken) {

		Assert.notNull(vaultToken, "VaultToken must not be null");

		vaultOperations.doWithVault(new RevokeToken(String.format(
				"auth/token/revoke-orphan/%s", vaultToken.getToken())));
	}

	private static String buildExceptionMessage(VaultResponseEntity<?> response) {

		if (StringUtils.hasText(response.getMessage())) {
			return String.format("Status %s URI %s: %s", response.getStatusCode(),
					response.getUri(), response.getMessage());
		}

		return String.format("Status %s URI %s", response.getStatusCode(),
				response.getUri());
	}

	private static class CreateToken implements SessionCallback<VaultTokenResponse> {

		private final String path;

		private final VaultTokenRequest request;

		public CreateToken(String path, VaultTokenRequest request) {
			this.path = path;
			this.request = request;
		}

		@Override
		public VaultTokenResponse doWithVault(VaultSession session) {

			VaultResponseEntity<VaultTokenResponse> response = session.postForEntity(
					path, request, VaultTokenResponse.class);

			if (response.isSuccessful() && response.hasBody()) {
				return response.getBody();
			}

			throw new VaultException(buildExceptionMessage(response));
		}
	}

	private static class RevokeToken implements SessionCallback<Void> {

		private final String path;

		RevokeToken(String path) {
			this.path = path;
		}

		@Override
		public Void doWithVault(VaultSession session) {

			VaultResponseEntity<VaultTokenResponse> response = session.postForEntity(
					path, null, VaultTokenResponse.class);

			if (response.isSuccessful()) {
				return null;
			}

			throw new VaultException(buildExceptionMessage(response));
		}
	}

	private static class RenewToken implements SessionCallback<VaultTokenResponse> {

		private final String path;

		RenewToken(String path) {
			this.path = path;
		}

		@Override
		public VaultTokenResponse doWithVault(VaultSession session) {

			VaultResponseEntity<VaultTokenResponse> response = session.postForEntity(
					path, null, VaultTokenResponse.class);

			if (response.isSuccessful() && response.hasBody()) {
				return response.getBody();
			}

			throw new VaultException(buildExceptionMessage(response));
		}
	}
}
