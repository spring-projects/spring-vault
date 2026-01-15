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

import org.springframework.context.ApplicationEvent;
import org.springframework.vault.core.certificate.domain.RequestedCertificate;

/**
 * Abstract base class for {@link RequestedCertificate}-based events.
 *
 * @author Mark Paluch
 * @since 4.1
 * @see ApplicationEvent
 * @see RequestedCertificate
 */
public abstract class CertificateEvent extends ApplicationEvent {

	@Serial
	private static final long serialVersionUID = 1L;


	/**
	 * Create a new {@code CertificateEvent} given {@link RequestedCertificate}.
	 * @param requestedCertificate must not be {@literal null}.
	 */
	protected CertificateEvent(RequestedCertificate requestedCertificate) {
		super(requestedCertificate);
	}


	@Override
	public RequestedCertificate getSource() {
		return (RequestedCertificate) super.getSource();
	}

}
