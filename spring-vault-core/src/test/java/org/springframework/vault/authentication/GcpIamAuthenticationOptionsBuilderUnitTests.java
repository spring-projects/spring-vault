package org.springframework.vault.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.security.PrivateKey;

import org.junit.Test;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;

/**
 * Unit tests for {@link GcpIamAuthenticationOptions.GcpIamAuthenticationOptionsBuilder}
 */
public class GcpIamAuthenticationOptionsBuilderUnitTests {

	private GoogleCredential createGoogleCredential() {
		GoogleCredential credential = new GoogleCredential.Builder().setServiceAccountId("hello@world")
				.setServiceAccountProjectId("foobar")
				.setServiceAccountPrivateKey(mock(PrivateKey.class))
				.setServiceAccountPrivateKeyId("key-id").build();
		credential.setAccessToken("foobar");
		return credential;
	}

	@Test
	public void shouldDefaultToCredentialServiceAccountId() {
		GoogleCredential credential = createGoogleCredential();
		GcpIamAuthenticationOptions options = GcpIamAuthenticationOptions.builder()
                .credential(credential)
                .role("foo")
                .build();

		assertThat(options.getServiceAccountIdProvider().getServiceAccountId(credential)).isEqualTo("hello@world");
	}

	@Test
	public void shouldAllowServiceAccountIdOverride() {
        GoogleCredential credential = createGoogleCredential();
		GcpIamAuthenticationOptions options = GcpIamAuthenticationOptions.builder()
                .credential(credential)
                .serviceAccountId("override@foo.com")
                .role("foo")
				.build();

		assertThat(options.getServiceAccountIdProvider().getServiceAccountId(credential)).isEqualTo("override@foo.com");
	}

    @Test
    public void shouldAllowServiceAccountIdProviderOverride() {
        GoogleCredential credential = createGoogleCredential();
        GcpIamAuthenticationOptions options = GcpIamAuthenticationOptions.builder()
                .credential(credential)
                .serviceAccountIdProvider((GoogleCredential googleCredential) -> "override@foo.com")
                .role("foo")
                .build();

        assertThat(options.getServiceAccountIdProvider().getServiceAccountId(credential)).isEqualTo("override@foo.com");
    }

    @Test
    public void shouldDefaultToCredentialProjectId() {
        GoogleCredential credential = createGoogleCredential();
        GcpIamAuthenticationOptions options = GcpIamAuthenticationOptions.builder()
                .credential(credential)
                .role("foo")
                .build();

        assertThat(options.getProjectIdProvider().getProjectId(credential)).isEqualTo("foobar");
    }

    @Test
    public void shouldAllowProjectIdOverride() {
        GoogleCredential credential = createGoogleCredential();
        GcpIamAuthenticationOptions options = GcpIamAuthenticationOptions.builder()
                .credential(credential)
                .projectId("my-project")
                .role("foo")
                .build();

        assertThat(options.getProjectIdProvider().getProjectId(credential)).isEqualTo("my-project");
    }

    @Test
    public void shouldAllowProjectIdProviderOverride() {
        GoogleCredential credential = createGoogleCredential();
        GcpIamAuthenticationOptions options = GcpIamAuthenticationOptions.builder()
                .credential(credential)
                .projectIdProvider((GoogleCredential googleCredential) -> "my-project")
                .role("foo")
                .build();

        assertThat(options.getProjectIdProvider().getProjectId(credential)).isEqualTo("my-project");
    }

}
