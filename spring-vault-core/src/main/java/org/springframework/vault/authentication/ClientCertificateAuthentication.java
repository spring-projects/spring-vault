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

import java.util.Collections;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import static org.springframework.vault.authentication.AuthenticationSteps.HttpRequestBuilder.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

/**
 * TLS Client Certificate {@link ClientAuthentication}.
 *
 * @author Mark Paluch
 * @author Andy Lintner
 */
public class ClientCertificateAuthentication implements ClientAuthentication, AuthenticationStepsFactory {

	private static final Log logger = LogFactory.getLog(ClientCertificateAuthentication.class);

	private final ClientCertificateAuthenticationOptions options;

	private final RestOperations restOperations;

	/**
	 * Create a {@link ClientCertificateAuthentication} using {@link RestOperations}.
	 * @param restOperations must not be {@literal null}.
	 */
	public ClientCertificateAuthentication(RestOperations restOperations) {
		this(ClientCertificateAuthenticationOptions.builder().build(), restOperations);
	}

	/**
	 * Create a {@link ClientCertificateAuthentication} using {@link RestOperations}.
	 * @param options must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 * @since 2.3
	 */
	public ClientCertificateAuthentication(ClientCertificateAuthenticationOptions options,
			RestOperations restOperations) {

		Assert.notNull(options, "ClientCertificateAuthenticationOptions must not be null");
		Assert.notNull(restOperations, "RestOperations must not be null");

		this.restOperations = restOperations;
		this.options = options;
	}

	/**
	 * Creates a {@link AuthenticationSteps} for client certificate authentication.
	 * @return {@link AuthenticationSteps} for client certificate authentication.
	 * @since 2.0
	 */
	public static AuthenticationSteps createAuthenticationSteps() {
		return createAuthenticationSteps(ClientCertificateAuthenticationOptions.builder().build());
	}

	/**
	 * Creates a {@link AuthenticationSteps} for client certificate authentication.
	 * @param options must not be {@literal null}.
	 * @return {@link AuthenticationSteps} for client certificate authentication.
	 * @since 2.3
	 */
	public static AuthenticationSteps createAuthenticationSteps(ClientCertificateAuthenticationOptions options) {
		Assert.notNull(options, "ClientCertificateAuthenticationOptions must not be null");

		Map<String, Object> body = getRequestBody(options);

		return AuthenticationSteps.fromSupplier(() -> body)
			.login(post(AuthenticationUtil.getLoginPath(options.getPath())).as(VaultResponse.class));
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

		try {
			Map<String, Object> request = getRequestBody(this.options);
			VaultResponse response = this.restOperations
				.postForObject(AuthenticationUtil.getLoginPath(this.options.getPath()), request, VaultResponse.class);

			Assert.state(response.getAuth() != null, "Auth field must not be null");

			logger.debug("Login successful using TLS certificates");

			return LoginTokenUtil.from(response.getAuth());
		}
		catch (RestClientException e) {
			throw VaultLoginException.create("TLS Certificates", e);
		}
	}

	private static Map<String, Object> getRequestBody(ClientCertificateAuthenticationOptions options) {
		String name = options.getRole();

		return name != null ? Collections.singletonMap("name", name) : Collections.emptyMap();
	}

}
