/*
 * Copyright 2025-present the original author or authors.
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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.vault.core.VaultPkiOperations;
import org.springframework.vault.core.certificate.domain.RequestedCertificate;
import org.springframework.vault.core.certificate.domain.RequestedCertificateBundle;
import org.springframework.vault.support.Certificate;
import org.springframework.vault.support.CertificateBundle;
import org.springframework.vault.support.VaultCertificateRequest;
import org.springframework.vault.util.PkiIntegrationTestSupport;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ManagedCertificate}.
 *
 * @author Mark Paluch
 */
class ManagedCertificateIntegrationTests extends PkiIntegrationTestSupport {

	RequestedCertificateBundle request = RequestedCertificateBundle.issue("www.example.com", "testrole",
			VaultCertificateRequest.builder().commonName("www.example.com").ttl(Duration.ofSeconds(80)).build());

	CertificateContainer container;

	@BeforeEach
	public void before() {
		super.before();

		VaultPkiOperations pki = template.opsForPki();
		container = new CertificateContainer(new VaultCertificateAuthority(pki));
		container.afterPropertiesSet();
	}

	@AfterEach
	void tearDown() throws Exception {
		container.destroy();
	}

	@Test
	void shouldRequestManagedCertificate() {
		AtomicReference<CertificateBundle> bundleRef = new AtomicReference<>();
		AtomicReference<Throwable> errorRef = new AtomicReference<>();
		ManagedCertificate managedCertificate = ManagedCertificate.issue(request, bundleRef::set, errorRef::set);

		managedCertificate.registerCertificate(container);
		container.start();

		assertThat(bundleRef).doesNotHaveNullValue();
		assertThat(errorRef).hasNullValue();
	}

	@Test
	void shouldRequestTrustAnchor() {
		AtomicReference<Certificate> certificateRef = new AtomicReference<>();
		AtomicReference<Throwable> errorRef = new AtomicReference<>();
		ManagedCertificate managedCertificate = ManagedCertificate.trust(RequestedCertificate.trustAnchor("foo"),
				certificateRef::set, errorRef::set);

		managedCertificate.registerCertificate(container);
		container.start();

		assertThat(certificateRef).doesNotHaveNullValue();
		assertThat(errorRef).hasNullValue();

		assertThat(certificateRef.get()).isExactlyInstanceOf(Certificate.class)
				.isNotInstanceOf(CertificateBundle.class);
		assertThat(certificateRef.get().getX509Certificate().getSubjectX500Principal()
				.toString()).contains("Intermediate CA Certificate");
	}

	@Test
	void requestManagedCertificateShouldFail() {
		AtomicReference<CertificateBundle> bundleRef = new AtomicReference<>();
		AtomicReference<Throwable> errorRef = new AtomicReference<>();
		ManagedCertificate managedCertificate = ManagedCertificate.issue("foo", "unknown",
				VaultCertificateRequest.builder().commonName("invalid").build(), bundleRef::set, errorRef::set);

		managedCertificate.registerCertificate(container);
		container.start();

		assertThat(bundleRef).hasNullValue();
		assertThat(errorRef).doesNotHaveNullValue();
		assertThat(errorRef.get().getMessage()).contains("unknown role");
	}

}
