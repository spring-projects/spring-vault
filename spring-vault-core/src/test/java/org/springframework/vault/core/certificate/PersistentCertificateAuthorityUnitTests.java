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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.assertj.core.util.Files;
import org.junit.jupiter.api.Test;

import org.springframework.vault.support.Certificate;
import org.springframework.vault.support.CertificateBundle;
import org.springframework.vault.support.VaultCertificateRequest;
import org.springframework.vault.util.Settings;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PersistentCertificateAuthority}.
 *
 * @author Mark Paluch
 */
class PersistentCertificateAuthorityUnitTests {

	File workDir = Settings.findWorkDir();

	String cert = Files.contentOf(new File(workDir, "ca/certs/localhost.cert.pem"), StandardCharsets.US_ASCII);

	String key = Files.contentOf(new File(workDir, "ca/private/intermediate.decrypted.key.pem"),
			StandardCharsets.US_ASCII);

	CertificateBundle bundle = CertificateBundle.of("foo", cert, cert, key);

	@Test
	void shouldIssueBundle() {

		MapCertificateBundleStore store = new MapCertificateBundleStore();
		PersistentCertificateAuthority sut = new PersistentCertificateAuthority(store, new CertificateAuthority() {

			@Override
			public CertificateBundle issueCertificate(String bundleName, String roleName,
					VaultCertificateRequest request) {
				return bundle;
			}

			@Override
			public Certificate getIssuerCertificate(String bundleName, String issuer) {
				return null;
			}

		}, Duration.ofHours(1));

		assertThat(sut.issueCertificate("", "", null)).isEqualTo(bundle);
	}

	@Test
	void shouldReturnCachedBundle() {

		MapCertificateBundleStore store = new MapCertificateBundleStore();
		store.registerBundle("bundle", bundle);
		PersistentCertificateAuthority sut = new PersistentCertificateAuthority(store, new CertificateAuthority() {

			@Override
			public CertificateBundle issueCertificate(String bundleName, String roleName,
					VaultCertificateRequest request) {
				throw new IllegalStateException("Should not be called");
			}

			@Override
			public Certificate getIssuerCertificate(String bundleName, String issuer) {
				return null;
			}

		}, Duration.ofHours(1));

		assertThat(sut.issueCertificate("bundle", "", null)).isEqualTo(bundle);
	}

	@Test
	void shouldNotReturnExpiredBundle() {

		CertificateBundle newOne = CertificateBundle.of("foo", cert, cert, key);

		MapCertificateBundleStore store = new MapCertificateBundleStore();
		store.registerBundle("bundle", bundle);
		Duration afterExpiry = Duration.between(Instant.now(), bundle.getX509Certificate().getNotAfter().toInstant())
				.plusDays(10);
		PersistentCertificateAuthority sut = new PersistentCertificateAuthority(store, new CertificateAuthority() {

			@Override
			public CertificateBundle issueCertificate(String bundleName, String roleName,
					VaultCertificateRequest request) {
				return newOne;
			}

			@Override
			public Certificate getIssuerCertificate(String bundleName, String issuer) {
				return null;
			}

		}, afterExpiry);

		assertThat(sut.issueCertificate("bundle", "", null)).isSameAs(newOne).isNotSameAs(bundle);
	}

}
