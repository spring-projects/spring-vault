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

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpMethod;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.client.RestClient;

/**
 * Default implementation of {@link VaultLoginClient}.
 *
 * @author Mark Paluch
 * @since 4.1
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
	public LoginBodyRequestSpec loginAt(String authMount) {
		return new DefaultLoginBodySpec(authMount);
	}

	@Override
	public LoginBodyPathSpec login() {
		return new DefaultLoginBodyPathSpec();
	}

	@Override
	public RequestHeadersPathSpec<?> get() {
		return vaultClient.get();
	}

	@Override
	public RequestHeadersBodyPathSpec post() {
		return vaultClient.post();
	}

	@Override
	public RequestHeadersBodyPathSpec put() {
		return vaultClient.put();
	}

	@Override
	public RequestHeadersPathSpec<?> delete() {
		return vaultClient.delete();
	}

	@Override
	public RequestHeadersBodyPathSpec method(HttpMethod method) {
		return vaultClient.method(method);
	}

	@Override
	public VaultClient.Builder mutate() {
		return vaultClient.mutate();
	}

	class DefaultLoginBodySpec implements LoginBodyRequestSpec {

		private final RequestBodySpec spec;

		public DefaultLoginBodySpec(String path) {
			this.spec = vaultClient.post().path(AuthenticationUtil.getLoginPath(path));
		}

		public DefaultLoginBodySpec(RequestBodySpec spec) {
			this.spec = spec;
		}

		@Override
		public LoginBodyRequestSpec using(Object body) {
			spec.body(body);
			return this;
		}

		@Override
		public LoginResponseSpec retrieve() {
			return new DefaultLoginResponseSpec(spec.retrieve());
		}

	}

	class DefaultLoginBodyPathSpec implements LoginBodyPathSpec {

		public DefaultLoginBodyPathSpec() {
		}

		@Override
		public LoginBodyRequestSpec path(String path, @Nullable Object... pathVariables) {
			return new DefaultLoginBodySpec(vaultClient.post().path(path, pathVariables));
		}

		@Override
		public LoginBodyRequestSpec path(String path, Map<String, ?> pathVariables) {
			return new DefaultLoginBodySpec(vaultClient.post().path(path, pathVariables));
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
				LoginToken token = LoginToken.from(response.getAuth());

				if (logger.isDebugEnabled()) {
					logger.debug("Login successful using %s authentication".formatted(authenticationMechanism));
				}

				return token;
			}
			catch (VaultException e) {
				throw VaultLoginException.create(authenticationMechanism, e.getCause());
			}
		}

		@Override
		public VaultResponseSupport<LoginToken> body() {

			try {
				VaultResponse response = spec.requiredBody();
				LoginToken token = LoginToken.from(response.getAuth());

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
			catch (VaultException e) {
				throw VaultLoginException.create(authenticationMechanism, e.getCause());
			}
		}

	}

}
