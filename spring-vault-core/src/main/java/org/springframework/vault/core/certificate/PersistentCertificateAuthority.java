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
import java.time.Instant;

import org.springframework.vault.support.Certificate;
import org.springframework.vault.support.CertificateBundle;
import org.springframework.vault.support.VaultCertificateRequest;

/**
 * {@link CertificateAuthority} implementation that uses
 * {@link CertificateBundleStore} to store certificate bundles.
 *
 * @author Mark Paluch
 * @since 5.1
 */
public class PersistentCertificateAuthority implements CertificateAuthority {

	private final CertificateBundleStore store;

	private final CertificateAuthority delegate;

	private final Duration expiryThreshold;


	public PersistentCertificateAuthority(CertificateBundleStore store, CertificateAuthority delegate,
			Duration expiryThreshold) {
		this.store = store;
		this.delegate = delegate;
		this.expiryThreshold = expiryThreshold;
	}


	@Override
	public CertificateBundle issueCertificate(String bundleName, String role, VaultCertificateRequest request) {
		CertificateBundle bundle = store.getBundle(bundleName);
		if (bundle != null && !isExpired(bundle.getX509Certificate().getNotAfter().toInstant())) {
			return bundle;
		}
		bundle = delegate.issueCertificate(bundleName, role, request);
		store.registerBundle(bundleName, bundle);
		return bundle;
	}

	@Override
	public Certificate getIssuerCertificate(String bundleName, String issuer) {
		return delegate.getIssuerCertificate(bundleName, issuer);
	}

	boolean isExpired(Instant notAfter) {
		Duration duration = Duration.between(Instant.now(), notAfter);
		if (duration.isZero() || duration.isNegative()) {
			return true;
		}
		return duration.compareTo(this.expiryThreshold) <= 0;
	}

}
