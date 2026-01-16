/*
 * Copyright 2026 the original author or authors.
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
package org.springframework.vault.core.certificate;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.vault.core.certificate.domain.RequestedCertificate;
import org.springframework.vault.core.certificate.domain.RequestedCertificateBundle;
import org.springframework.vault.core.certificate.domain.RequestedTrustAnchor;
import org.springframework.vault.core.certificate.event.CertificateListener;
import org.springframework.vault.support.VaultCertificateRequest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ManagedCertificate}.
 *
 * @author Mark Paluch
 */
@MockitoSettings(strictness = Strictness.LENIENT)
class ManagedCertificateUnitTests {

	@Captor
	ArgumentCaptor<RequestedCertificate> certCaptor;

	@Captor
	ArgumentCaptor<CertificateListener> listenerCaptor;

	@Mock
	CertificateRegistry registryMock;

	@Test
	void createsIssuedCertificate() {
		ManagedCertificate managed = ManagedCertificate.issue("my-cert", "some-role", VaultCertificateRequest.builder()
				.commonName("www.example.com").build(), certificateBundle -> {
				});
		managed.registerCertificate(registryMock);


		verify(registryMock).register(certCaptor.capture(), listenerCaptor.capture());
		assertThat((RequestedCertificateBundle) certCaptor.getValue()).hasFieldOrPropertyWithValue("name", "my-cert");
		assertThat((RequestedCertificateBundle) certCaptor.getValue()).hasFieldOrPropertyWithValue("role", "some-role");
	}

	@Test
	void createsTrustAnchor() {
		ManagedCertificate managed = ManagedCertificate.trust("my-cert", "my-issuer", certificate -> {
		});
		managed.registerCertificate(registryMock);

		verify(registryMock).register(certCaptor.capture(), listenerCaptor.capture());
		assertThat((RequestedTrustAnchor) certCaptor.getValue()).hasFieldOrPropertyWithValue("name", "my-cert");
		assertThat((RequestedTrustAnchor) certCaptor.getValue()).hasFieldOrPropertyWithValue("issuer", "my-issuer");
	}

}
