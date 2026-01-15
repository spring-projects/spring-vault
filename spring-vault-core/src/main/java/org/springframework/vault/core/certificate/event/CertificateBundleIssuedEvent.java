/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.vault.core.certificate.event;

import java.io.Serial;

import org.springframework.vault.core.certificate.domain.RequestedCertificate;
import org.springframework.vault.support.CertificateBundle;

/**
 * Event published after issuing a certificate.
 *
 * @author Mark Paluch
 * @since 4.1
 */
public class CertificateBundleIssuedEvent extends CertificateObtainedEvent {

	@Serial
	private static final long serialVersionUID = 1L;


	private final CertificateBundle certificateBundle;


	/**
	 * Create a new {@code CertificateBundleIssuedEvent} given
	 * {@link RequestedCertificate} and {@link CertificateBundle}.
	 * @param requestedCertificate must not be {@literal null}.
	 * @param certificateBundle must not be {@literal null}.
	 */
	public CertificateBundleIssuedEvent(RequestedCertificate requestedCertificate,
			CertificateBundle certificateBundle) {
		super(requestedCertificate, certificateBundle);
		this.certificateBundle = certificateBundle;
	}


	public CertificateBundle getCertificate() {
		return this.certificateBundle;
	}

}
