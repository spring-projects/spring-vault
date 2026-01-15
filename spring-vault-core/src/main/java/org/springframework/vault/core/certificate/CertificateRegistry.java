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

import org.springframework.vault.core.certificate.domain.RequestedCertificate;
import org.springframework.vault.core.certificate.event.CertificateListener;
import org.springframework.vault.core.lease.event.LeaseListener;

/**
 * Registry to manage {@link RequestedCertificate}s (request and rotation).
 *
 * @author Mark Paluch
 * @since 4.1
 */
public interface CertificateRegistry {

	/**
	 * Register a {@link RequestedCertificate} with the registry.
	 * @param certificate the certificate to be managed.
	 */
	void register(RequestedCertificate certificate);

	/**
	 * Register a {@link RequestedCertificate} with the registry with an associated
	 * {@link LeaseListener}.
	 * @param certificate the requested certificate to be managed.
	 * @param listener listener to associate with the requested certificate. The
	 * listener will be notified only with events concerning the requested
	 * certificate.
	 */
	void register(RequestedCertificate certificate, CertificateListener listener);

	/**
	 * Unregister the {@link RequestedCertificate} from the registry. Removing the
	 * certificate stops rotations, and it removes listener registrations that were
	 * {@link #register(RequestedCertificate, CertificateListener) associated with
	 * the certificate registration}.
	 * @param certificate the certificate to be deregistered.
	 * @return {@literal true} if the certificate was registered before and has been
	 * removed; {@literal false} otherwise.
	 */
	boolean unregister(RequestedCertificate certificate);

}
