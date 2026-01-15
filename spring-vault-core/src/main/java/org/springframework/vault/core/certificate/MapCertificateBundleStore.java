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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import org.springframework.vault.support.CertificateBundle;

/**
 * {@link Map}-based implementation of {@link CertificateBundleStore}.
 *
 * @author Mark Paluch
 * @since 4.1
 */
public class MapCertificateBundleStore implements CertificateBundleStore {

	private final Map<String, CertificateBundle> bundles;


	public MapCertificateBundleStore() {
		this(new ConcurrentHashMap<>());
	}

	public MapCertificateBundleStore(Map<String, CertificateBundle> bundles) {
		this.bundles = bundles;
	}


	@Override
	public void registerBundle(String name, CertificateBundle bundle) {
		bundles.put(name, bundle);
	}

	@Override
	public @Nullable CertificateBundle getBundle(String name) {
		return bundles.get(name);
	}

}
