/*
 * Copyright 2025 the original author or authors.
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.client.RestClientException;

/**
 * @author Mark Paluch
 */
class DefaultVaultLoginClient implements VaultLoginClient {

	private static final Log logger = LogFactory.getLog(DefaultVaultLoginClient.class);

	private final VaultClient vaultClient;

	private final String authenticationMechanism;

	DefaultVaultLoginClient(VaultClient vaultClient, String authenticationMechanism) {
		this.vaultClient = vaultClient;
		this.authenticationMechanism = authenticationMechanism;
	}

	@Override
	public LoginBodyPathSpec loginAt(String path) {
		return new DefaultLoginBodyPathSpec(path);
	}

	@Override
	public RequestHeadersPathSpec<?> get() {
		return vaultClient.get();
	}

	@Override
	public RequestBodyPathSpec post() {
		return vaultClient.post();
	}

	@Override
	public RequestBodyPathSpec put() {
		return vaultClient.put();
	}

	@Override
	public RequestHeadersPathSpec<?> delete() {
		return vaultClient.delete();
	}

	@Override
	public RequestBodyPathSpec method(HttpMethod method) {
		return vaultClient.method(method);
	}

	@Override
	public VaultClient.Builder mutate() {
		return vaultClient.mutate();
	}

	class DefaultLoginBodyPathSpec implements LoginBodyPathSpec {

		private final String path;
		private final RequestBodySpec spec;

		public DefaultLoginBodyPathSpec(String path) {
			this.path = path;
			this.spec = vaultClient.post().path(AuthenticationUtil.getLoginPath(path));
		}

		@Override
		public LoginBodySpec using(Object body) {
			spec.body(body);
			return this;
		}

		@Override
		public LoginResponseSpec retrieve() {
			return new DefaultLoginResponseSpec(spec.retrieve());
		}

	}

	class DefaultLoginResponseSpec implements LoginResponseSpec {

		private final ResponseSpec spec;

		DefaultLoginResponseSpec(ResponseSpec spec) {
			this.spec = spec;
		}

		@Override
		public LoginToken loginToken() {

			try {
				VaultResponse response = spec.requiredBody();
				LoginToken token = LoginTokenUtil.from(response.getRequiredData());

				if (logger.isDebugEnabled()) {
					logger.debug("Login successful using %s authentication".formatted(authenticationMechanism));
				}

				return token;
			}
			catch (RestClientException e) {
				throw VaultLoginException.create(authenticationMechanism, e);
			}
		}

		@Override
		public VaultResponseSupport<LoginToken> body() {

			try {
				VaultResponse response = spec.body();

				Assert.state(response != null && response.getAuth() != null, "Auth field must not be null");
				LoginToken token = LoginTokenUtil.from(response.getRequiredData());

				VaultResponseSupport<LoginToken> tokenResponse = new VaultResponseSupport<>();
				tokenResponse.setAuth(response.getAuth());
				tokenResponse.setLeaseDuration(response.getLeaseDuration());
				tokenResponse.setRenewable(response.isRenewable());
				tokenResponse.setMetadata(response.getMetadata());
				tokenResponse.setWarnings(response.getWarnings());
				tokenResponse.setWrapInfo(response.getWrapInfo());
				tokenResponse.setRequestId(response.getRequestId());
				tokenResponse.setData(token);

				if (logger.isDebugEnabled()) {
					logger.debug("Login successful using %s authentication".formatted(authenticationMechanism));
				}

				return tokenResponse;
			}
			catch (RestClientException e) {
				throw VaultLoginException.create(authenticationMechanism, e);
			}
		}
	}

}
