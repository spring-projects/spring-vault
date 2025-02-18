/*
 * Copyright 2021-2025 the original author or authors.
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
import java.util.Date;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GcpIamCredentialsAuthenticationOptions}.
 *
 * @author Andreas Gebauer
 */
class GcpIamCredentialsAuthenticationOptionsBuilderUnitTests {

	@Test
	void shouldDefaultToCredentialServiceAccountId() {

		ServiceAccountCredentials credentials = createServiceAccountCredentials();

		GcpIamCredentialsAuthenticationOptions options = GcpIamCredentialsAuthenticationOptions.builder()
			.credentials(credentials)
			.role("foo")
			.build();

		assertThat(options.getServiceAccountIdAccessor().getServiceAccountId(credentials)).isEqualTo("hello@world");
	}

	@Test
	void shouldAllowServiceAccountIdOverride() {

		ServiceAccountCredentials credential = createServiceAccountCredentials();

		GcpIamCredentialsAuthenticationOptions options = GcpIamCredentialsAuthenticationOptions.builder()
			.credentials(credential)
			.serviceAccountId("override@foo.com")
			.role("foo")
			.build();

		assertThat(options.getServiceAccountIdAccessor().getServiceAccountId(credential)).isEqualTo("override@foo.com");
	}

	@Test
	void shouldAllowServiceAccountIdProviderOverride() {

		ServiceAccountCredentials credential = createServiceAccountCredentials();

		GcpIamCredentialsAuthenticationOptions options = GcpIamCredentialsAuthenticationOptions.builder()
			.credentials(credential)
			.serviceAccountIdAccessor((GoogleCredentials googleCredential) -> "override@foo.com")
			.role("foo")
			.build();

		assertThat(options.getServiceAccountIdAccessor().getServiceAccountId(credential)).isEqualTo("override@foo.com");
	}

	private static ServiceAccountCredentials createServiceAccountCredentials() {
		return (ServiceAccountCredentials) ServiceAccountCredentials.newBuilder()
			.setClientEmail("hello@world")
			.setProjectId("project-id")
			.setPrivateKey(mock(PrivateKey.class))
			.setPrivateKeyId("key-id")
			.setAccessToken(new AccessToken("foobar", new Date()))
			.build();
	}

}
