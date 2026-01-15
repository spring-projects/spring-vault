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

import javax.security.auth.x500.X500Principal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.vault.core.VaultPkiOperations;
import org.springframework.vault.core.certificate.domain.RequestedCertificate;
import org.springframework.vault.core.certificate.domain.RequestedCertificateBundle;
import org.springframework.vault.core.certificate.domain.RequestedTrustAnchor;
import org.springframework.vault.core.certificate.event.CertificateBundleIssuedEvent;
import org.springframework.vault.core.certificate.event.CertificateObtainedEvent;
import org.springframework.vault.support.Certificate;
import org.springframework.vault.support.VaultCertificateRequest;
import org.springframework.vault.util.PkiIntegrationTestSupport;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CertificateContainer}.
 *
 * @author Mark Paluch
 */
class CertificateContainerIntegrationTests extends PkiIntegrationTestSupport {

	RequestedCertificateBundle request = RequestedCertificateBundle.issue("www.example.com", "testrole",
			VaultCertificateRequest.builder().commonName("www.example.com").ttl(Duration.ofSeconds(80)).build());

	RequestedTrustAnchor caChain = RequestedCertificate.trustAnchor("trust-store");

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
	void startedContainerShouldRequestCertificate() {
		container.start();
		AtomicReference<Certificate> bundleRef = new AtomicReference<>();
		container.register(request, it -> {

			if (it instanceof CertificateBundleIssuedEvent ceb) {
				bundleRef.set(ceb.getCertificate());
			}
		});
		container.stop();

		assertThat(bundleRef.get()).isNotNull();
		assertThat(bundleRef).hasValueSatisfying(actual -> {
			assertThat(actual.getX509Certificate().getSubjectX500Principal().getName(X500Principal.CANONICAL))
					.contains("cn=www.example.com");
		});
	}

	@Test
	void shouldRequestCertificate() {
		AtomicReference<Certificate> bundleRef = new AtomicReference<>();
		container.register(request, it -> {

			if (it instanceof CertificateBundleIssuedEvent ceb) {
				bundleRef.set(ceb.getCertificate());
			}
		});

		assertThat(bundleRef).hasValue(null);

		container.start();
		container.stop();

		assertThat(bundleRef.get()).isNotNull();
		assertThat(bundleRef).hasValueSatisfying(actual -> {
			assertThat(actual.getX509Certificate().getSubjectX500Principal().getName(X500Principal.CANONICAL))
					.contains("cn=www.example.com");
		});
	}

	@Test
	void shouldRequestIssuerCertificate() {
		AtomicReference<Certificate> certificateRef = new AtomicReference<>();
		container.register(caChain, it -> {

			if (it instanceof CertificateObtainedEvent ceb) {
				certificateRef.set(ceb.getCertificate());
			}
		});

		assertThat(certificateRef).hasValue(null);

		container.start();
		container.stop();

		assertThat(certificateRef.get()).isNotNull();
		assertThat(certificateRef).hasValueSatisfying(actual -> {
			assertThat(actual.getX509Certificate().getSubjectX500Principal().getName(X500Principal.CANONICAL))
					.contains("cn=intermediate ca certificate");
		});
	}

	@Test
	void shouldRotateCertificate() {
		container.start();
		AtomicReference<Certificate> bundleRef = new AtomicReference<>();
		container.register(request, it -> {

			if (it instanceof CertificateBundleIssuedEvent ceb) {
				bundleRef.set(ceb.getCertificate());
			}
		});

		assertThat(bundleRef.get()).isNotNull();
		Certificate initial = bundleRef.get();
		container.rotate(request);
		Certificate rotated = bundleRef.get();
		container.stop();

		assertThat(initial).isNotEqualTo(rotated);
		assertThat(initial.getX509Certificate().getSerialNumber())
				.isNotEqualTo(rotated.getX509Certificate().getSerialNumber());
	}

}
