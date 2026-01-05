/*
 * Copyright 2017-present the original author or authors.
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
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestOperations;

/**
 * GitHub's authentication method can be used to authenticate with Vault using a
 * GitHub personal access token.
 *
 * @author Nanne Baars
 * @author Mark Paluch
 * @since 3.2
 * @see GitHubAuthentication
 * @see VaultClient
 * @see <a href="https://www.vaultproject.io/api-docs/auth/github">GitHub Auth
 * Method</a>
 */
public class GitHubAuthentication implements ClientAuthentication, AuthenticationStepsFactory {

	private final GitHubAuthenticationOptions options;

	private final VaultLoginClient loginClient;


	/**
	 * Create a {@link GitHubAuthentication} using
	 * {@link GitHubAuthenticationOptions} and {@link RestOperations}.
	 * @param options must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 * @deprecated since 4.1, use
	 * {@link #GitHubAuthentication(GitHubAuthenticationOptions, VaultClient)}
	 * instead.
	 */
	@Deprecated(since = "4.1")
	public GitHubAuthentication(GitHubAuthenticationOptions options, RestOperations restOperations) {
		this(options, ClientAdapter.from(restOperations).vaultClient());
	}

	/**
	 * Create a {@link GitHubAuthentication} using
	 * {@link GitHubAuthenticationOptions} and {@link RestOperations}.
	 * @param options must not be {@literal null}.
	 * @param client must not be {@literal null}.
	 * @since 4.0
	 */
	public GitHubAuthentication(GitHubAuthenticationOptions options, RestClient client) {
		this(options, ClientAdapter.from(client).vaultClient());
	}

	/**
	 * Create a {@link GitHubAuthentication} using
	 * {@link GitHubAuthenticationOptions} and {@link VaultClient}.
	 * @param options must not be {@literal null}.
	 * @param client must not be {@literal null}.
	 * @since 4.1
	 */
	public GitHubAuthentication(GitHubAuthenticationOptions options, VaultClient client) {
		Assert.notNull(options, "GithubAuthenticationOptions must not be null");
		Assert.notNull(client, "VaultClient must not be null");
		this.options = options;
		this.loginClient = VaultLoginClient.create(client, "GitHub");
	}


	/**
	 * Create {@link AuthenticationSteps} for GitHub authentication given
	 * {@link GitHubAuthenticationOptions}.
	 * @param options must not be {@literal null}.
	 * @return {@link AuthenticationSteps} for github authentication.
	 */
	public static AuthenticationSteps createAuthenticationSteps(GitHubAuthenticationOptions options) {
		Assert.notNull(options, "GitHubAuthentication must not be null");
		return AuthenticationSteps.fromSupplier(options.getTokenSupplier())
				.map(GitHubAuthentication::getGitHubLogin)
				.loginAt(options.getPath());
	}


	@Override
	public AuthenticationSteps getAuthenticationSteps() {
		return createAuthenticationSteps(options);
	}

	@Override
	public VaultToken login() throws VaultException {
		Map<String, String> login = getGitHubLogin(this.options.getTokenSupplier().get());
		return this.loginClient.loginAt(this.options.getPath()).using(login).retrieve().loginToken();
	}

	private static Map<String, String> getGitHubLogin(String token) {
		return Map.of("token", token);
	}

}
