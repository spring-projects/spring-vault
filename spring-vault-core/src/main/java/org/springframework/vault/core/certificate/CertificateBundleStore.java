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

package org.springframework.vault.core.certificate;

import org.jspecify.annotations.Nullable;

import org.springframework.vault.support.CertificateBundle;

/**
 * Storage interface for storing {@link CertificateBundle} instances with their
 * private and public keys.
 *
 * @author Mark Paluch
 * @since 4.1
 */
public interface CertificateBundleStore {

	/**
	 * Register and store a {@link CertificateBundle} under its given {@code name}.
	 * @param name name of the certificate bundle.
	 * @param bundle the certificate bundle.
	 */
	void registerBundle(String name, CertificateBundle bundle);

	/**
	 * Retrieve the {@link CertificateBundle} registered under the given
	 * {@code name}. Returns {@code null} if the bundle does not exist.
	 * @param name name of the certificate bundle.
	 * @return the certificate bundle or {@code null} if not found.
	 */
	@Nullable
	CertificateBundle getBundle(String name);

}
