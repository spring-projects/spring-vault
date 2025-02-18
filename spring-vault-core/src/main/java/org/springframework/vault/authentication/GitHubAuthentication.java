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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

/**
 * GitHub's authentication method can be used to authenticate with Vault using a GitHub
 * personal access token.
 *
 * @author Nanne Baars
 * @author Mark Paluch
 * @since 3.2
 * @see GitHubAuthentication
 * @see RestOperations
 * @see <a href="https://www.vaultproject.io/api-docs/auth/github">GitHub Auth Backend</a>
 */
public class GitHubAuthentication implements ClientAuthentication, AuthenticationStepsFactory {

	private static final Log logger = LogFactory.getLog(GitHubAuthentication.class);

	private final GitHubAuthenticationOptions options;

	private final RestOperations restOperations;

	/**
	 * Create a {@link GitHubAuthentication} using {@link GitHubAuthenticationOptions} and
	 * {@link RestOperations}.
	 * @param options must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 */
	public GitHubAuthentication(GitHubAuthenticationOptions options, RestOperations restOperations) {

		Assert.notNull(options, "GithubAuthenticationOptions must not be null");
		Assert.notNull(restOperations, "RestOperations must not be null");

		this.options = options;
		this.restOperations = restOperations;
	}

	/**
	 * Creates a {@link AuthenticationSteps} for GitHub authentication given
	 * {@link GitHubAuthenticationOptions}.
	 * @param options must not be {@literal null}.
	 * @return {@link AuthenticationSteps} for github authentication.
	 */
	public static AuthenticationSteps createAuthenticationSteps(GitHubAuthenticationOptions options) {

		Assert.notNull(options, "GitHubAuthentication must not be null");

		return AuthenticationSteps.fromSupplier(options.getTokenSupplier())
			.map(GitHubAuthentication::getGitHubLogin)
			.login(AuthenticationUtil.getLoginPath(options.getPath()));
	}

	@Override
	public AuthenticationSteps getAuthenticationSteps() {
		return createAuthenticationSteps(options);
	}

	@Override
	public VaultToken login() throws VaultException {

		Map<String, String> login = getGitHubLogin(this.options.getTokenSupplier().get());

		try {

			VaultResponse response = this.restOperations
				.postForObject(AuthenticationUtil.getLoginPath(this.options.getPath()), login, VaultResponse.class);
			Assert.state(response != null && response.getAuth() != null, "Auth field must not be null");

			logger.debug("Login successful using GitHub authentication");

			return LoginTokenUtil.from(response.getAuth());
		}
		catch (RestClientException e) {
			throw VaultLoginException.create("GitHub", e);
		}
	}

	private static Map<String, String> getGitHubLogin(String token) {
		return Map.of("token", token);
	}

}
