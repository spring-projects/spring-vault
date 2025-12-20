/*
 * Copyright 2016-2025 the original author or authors.
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

import java.util.Collections;
import java.util.Map;

import org.springframework.util.Assert;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestOperations;

/**
 * TLS Client Certificate {@link ClientAuthentication}.
 *
 * @author Mark Paluch
 * @author Andy Lintner
 * @see VaultClient
 */
public class ClientCertificateAuthentication implements ClientAuthentication, AuthenticationStepsFactory {

	private final ClientCertificateAuthenticationOptions options;

	private final VaultLoginClient loginClient;


	/**
	 * Create a {@link ClientCertificateAuthentication} using
	 * {@link RestOperations}.
	 * @param restOperations must not be {@literal null}.
	 * @deprecated since 4.1, use
	 * {@link #ClientCertificateAuthentication(VaultClient)} instead.
	 */
	@Deprecated(since = "4.1")
	public ClientCertificateAuthentication(RestOperations restOperations) {
		this(ClientCertificateAuthenticationOptions.builder().build(), restOperations);
	}

	/**
	 * Create a {@link ClientCertificateAuthentication} using
	 * {@link RestOperations}.
	 * @param options must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 * @since 2.3
	 * @deprecated since 4.1, use
	 * {@link #ClientCertificateAuthentication(ClientCertificateAuthenticationOptions, VaultClient)}
	 * instead.
	 */
	@Deprecated(since = "4.1")
	public ClientCertificateAuthentication(ClientCertificateAuthenticationOptions options,
			RestOperations restOperations) {
		this(options, ClientAdapter.from(restOperations).vaultClient());
	}

	/**
	 * Create a {@link ClientCertificateAuthentication} using {@link RestClient}.
	 * @param client must not be {@literal null}.
	 * @since 4.0
	 */
	public ClientCertificateAuthentication(RestClient client) {
		this(ClientCertificateAuthenticationOptions.builder().build(), client);
	}

	/**
	 * Create a {@link ClientCertificateAuthentication} using
	 * {@link RestOperations}.
	 * @param options must not be {@literal null}.
	 * @param client must not be {@literal null}.
	 * @since 4.0
	 */
	public ClientCertificateAuthentication(ClientCertificateAuthenticationOptions options, RestClient client) {
		this(options, ClientAdapter.from(client).vaultClient());
	}

	/**
	 * Create a {@link ClientCertificateAuthentication} using {@link VaultClient}.
	 * @param client must not be {@literal null}.
	 * @since 4.1
	 */
	public ClientCertificateAuthentication(VaultClient client) {
		this(ClientCertificateAuthenticationOptions.builder().build(), client);
	}

	/**
	 * Create a {@link ClientCertificateAuthentication} using {@link VaultClient}.
	 * @param options must not be {@literal null}.
	 * @param client must not be {@literal null}.
	 * @since 4.1
	 */
	public ClientCertificateAuthentication(ClientCertificateAuthenticationOptions options, VaultClient client) {

		Assert.notNull(options, "ClientCertificateAuthenticationOptions must not be null");
		Assert.notNull(client, "RestOperations must not be null");
		this.options = options;
		this.loginClient = VaultLoginClient.create(client, "TLS Certificates");
	}


	/**
	 * Create {@link AuthenticationSteps} for client certificate authentication.
	 * @return {@link AuthenticationSteps} for client certificate authentication.
	 * @since 2.0
	 */
	public static AuthenticationSteps createAuthenticationSteps() {
		return createAuthenticationSteps(ClientCertificateAuthenticationOptions.builder().build());
	}

	/**
	 * Create {@link AuthenticationSteps} for client certificate authentication.
	 * @param options must not be {@literal null}.
	 * @return {@link AuthenticationSteps} for client certificate authentication.
	 * @since 2.3
	 */
	public static AuthenticationSteps createAuthenticationSteps(ClientCertificateAuthenticationOptions options) {
		Assert.notNull(options, "ClientCertificateAuthenticationOptions must not be null");
		Map<String, Object> body = getRequestBody(options);

		return AuthenticationSteps.fromSupplier(() -> body).loginAt(options.getPath());
	}


	@Override
	public VaultToken login() {
		return createTokenUsingTlsCertAuthentication();
	}

	@Override
	public AuthenticationSteps getAuthenticationSteps() {
		return createAuthenticationSteps(this.options);
	}

	private VaultToken createTokenUsingTlsCertAuthentication() {
		Map<String, Object> request = getRequestBody(this.options);
		return this.loginClient.loginAt(this.options.getPath()).using(request).retrieve().loginToken();
	}

	private static Map<String, Object> getRequestBody(ClientCertificateAuthenticationOptions options) {
		String name = options.getRole();
		return name != null ? Collections.singletonMap("name", name) : Collections.emptyMap();
	}

}
