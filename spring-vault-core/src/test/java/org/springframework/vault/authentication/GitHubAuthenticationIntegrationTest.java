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

import java.io.IOException;
import java.util.Map;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.vault.client.VaultClient;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.TestVaultClient;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link GitHubAuthentication} using
 * {@link AuthenticationStepsExecutor}.
 *
 * @author Nanne Baars
 * @author Mark Paluch
 */
class GitHubAuthenticationIntegrationTest extends IntegrationTestSupport {

	private static final int organizationId = 1;

	private final MockWebServer gitHubMockServer = new MockWebServer();

	@BeforeEach
	void before() throws Exception {

		if (!prepare().hasAuth("github")) {
			prepare().mountAuth("github");
		}

		gitHubMockServer.start();

		prepare().getVaultOperations().doWithSession(restOperations -> {
			Map<String, String> config = Map.of("organization", "foo", "organization_id", "" + organizationId,
					"base_url", "http://localhost:%d".formatted(gitHubMockServer.getPort()));
			return restOperations.postForEntity("auth/github/config", config, Map.class);
		});
	}

	@AfterEach
	void after() throws IOException {
		gitHubMockServer.shutdown();
	}

	@Test
	void shouldLoginSuccessfully() {

		GitHubAuthenticationOptions options = GitHubAuthenticationOptions.builder()
				.tokenSupplier(() -> "TOKEN")
				.build();
		TestVaultClient client = TestVaultClient.create();
		setupGithubMockServer(gitHubUserResponse(), gitHubOrganizationResponse(organizationId),
				gitHubTeamResponse(organizationId));

		GitHubAuthentication authentication = new GitHubAuthentication(options, client);
		VaultToken loginToken = authentication.login();

		assertThat(loginToken.getToken()).isNotNull();
	}

	@Test
	void shouldLoginUsingAuthenticationSteps() {

		GitHubAuthenticationOptions options = GitHubAuthenticationOptions.builder()
				.tokenSupplier(() -> "TOKEN")
				.build();
		TestVaultClient client = TestVaultClient.create();
		setupGithubMockServer(gitHubUserResponse(), gitHubOrganizationResponse(organizationId),
				gitHubTeamResponse(organizationId));

		AuthenticationSteps steps = GitHubAuthentication.createAuthenticationSteps(options);

		AuthenticationStepsExecutor executor = TestAuthenticationStepsExecutor.create(steps, client);
		VaultToken loginToken = executor.login();

		assertThat(loginToken.getToken()).isNotNull();
	}

	@Test
	void shouldFailIfOrganizationIsNotTheSame() {

		VaultClient client = TestVaultClient.create();

		int wrongOrganizationId = organizationId + 1;

		setupGithubMockServer(gitHubUserResponse(), gitHubOrganizationResponse(wrongOrganizationId),
				gitHubTeamResponse(wrongOrganizationId));

		GitHubAuthentication authentication = new GitHubAuthentication(
				GitHubAuthenticationOptions.builder().tokenSupplier(() -> "TOKEN2").build(), client);

		assertThatThrownBy(authentication::login).isInstanceOf(VaultLoginException.class)
				.hasMessageContaining("Cannot login using GitHub: user is not part of required org");
	}

	private String gitHubUserResponse() {
		return """
				{
				  "login": "octocat",
				  "id": 100
				}
				""";
	}

	private String gitHubOrganizationResponse(int organizationId) {
		return """
				[
				  {
				    "login": "Foo bar organization",
				    "id": %d
				  }
				]
				""".formatted(organizationId);
	}

	private String gitHubTeamResponse(int organizationId) {
		return """
				[
				  {
				    "id": 45,
				    "name": "Justice League",
				    "slug": "justice-league",
				    "organization": {
					  "id": %d
					}
				  }
				]
				""".formatted(organizationId);
	}

	private void setupGithubMockServer(String userJson, String orgJson, String teamJson) {
		gitHubMockServer.setDispatcher(new Dispatcher() {

			@Override
			public MockResponse dispatch(RecordedRequest request) {

				return switch (request.getPath()) {
				case "/user" -> new MockResponse().setResponseCode(200).setBody(userJson);
				case "/user/orgs?per_page=100" -> new MockResponse().setResponseCode(200).setBody(orgJson);
				case "/user/teams?per_page=100" -> new MockResponse().setResponseCode(200).setBody(teamJson);
				default -> new MockResponse().setResponseCode(404);
				};
			}

		});
	}

}
