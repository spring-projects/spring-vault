/*
 * Copyright 2018-2021 the original author or authors.
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

import java.security.PrivateKey;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link GcpIamAuthenticationOptions}.
 *
 * @author Magnus Jungsbluth
 */
class GcpIamAuthenticationOptionsBuilderUnitTests {

	@Test
	void shouldDefaultToCredentialServiceAccountId() {

		GoogleCredential credential = createGoogleCredential();

		GcpIamAuthenticationOptions options = GcpIamAuthenticationOptions.builder().credential(credential).role("foo")
				.build();

		assertThat(options.getServiceAccountIdAccessor().getServiceAccountId(credential)).isEqualTo("hello@world");
	}

	@Test
	void shouldAllowServiceAccountIdOverride() {

		GoogleCredential credential = createGoogleCredential();

		GcpIamAuthenticationOptions options = GcpIamAuthenticationOptions.builder().credential(credential)
				.serviceAccountId("override@foo.com").role("foo").build();

		assertThat(options.getServiceAccountIdAccessor().getServiceAccountId(credential)).isEqualTo("override@foo.com");
	}

	@Test
	void shouldAllowServiceAccountIdProviderOverride() {

		GoogleCredential credential = createGoogleCredential();

		GcpIamAuthenticationOptions options = GcpIamAuthenticationOptions.builder().credential(credential)
				.serviceAccountIdAccessor((GoogleCredential googleCredential) -> "override@foo.com").role("foo")
				.build();

		assertThat(options.getServiceAccountIdAccessor().getServiceAccountId(credential)).isEqualTo("override@foo.com");
	}

	@Test
	void shouldDefaultToCredentialProjectId() {

		GoogleCredential credential = createGoogleCredential();

		GcpIamAuthenticationOptions options = GcpIamAuthenticationOptions.builder().credential(credential).role("foo")
				.build();

		assertThat(options.getProjectIdAccessor().getProjectId(credential)).isEqualTo("project-id");
	}

	@Test
	void shouldAllowProjectIdOverride() {

		GoogleCredential credential = createGoogleCredential();

		GcpIamAuthenticationOptions options = GcpIamAuthenticationOptions.builder().credential(credential)
				.projectId("my-project").role("foo").build();

		assertThat(options.getProjectIdAccessor().getProjectId(credential)).isEqualTo("my-project");
	}

	@Test
	void shouldAllowProjectIdProviderOverride() {

		GoogleCredential credential = createGoogleCredential();

		GcpIamAuthenticationOptions options = GcpIamAuthenticationOptions.builder().credential(credential)
				.projectIdAccessor((GoogleCredential googleCredential) -> "my-project").role("foo").build();

		assertThat(options.getProjectIdAccessor().getProjectId(credential)).isEqualTo("my-project");
	}

	private static GoogleCredential createGoogleCredential() {

		GoogleCredential credential = new GoogleCredential.Builder().setServiceAccountId("hello@world")
				.setServiceAccountProjectId("project-id").setServiceAccountPrivateKey(mock(PrivateKey.class))
				.setServiceAccountPrivateKeyId("key-id").build();

		credential.setAccessToken("foobar");

		return credential;
	}

}
