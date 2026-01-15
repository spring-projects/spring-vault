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

/**
 * Listener for Vault exceptional {@link CertificateEvent}s.
 * <p>Error events can occur during certificate issuance and rotation.
 *
 * @author Mark Paluch
 * @since 4.1
 */
@FunctionalInterface
public interface CertificateErrorListener {

	/**
	 * Callback for a {@link CertificateEvent}.
	 * @param certificateEvent the event object, must not be {@literal null}.
	 * @param exception the thrown {@link Exception}.
	 */
	void onCertificateError(CertificateEvent certificateEvent, Exception exception);

}
