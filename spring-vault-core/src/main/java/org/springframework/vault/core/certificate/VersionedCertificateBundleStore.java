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
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.core.util.KeyValueDelegate;
import org.springframework.vault.core.util.KeyValueDelegate.MountInfo;
import org.springframework.vault.support.CertificateBundle;
import org.springframework.vault.support.Versioned;

/**
 * Versioned implementation of {@link CertificateBundleStore} using Vault's
 * Key-Value (KV) secret backend v2.
 *
 * @author Mark Paluch
 * @since 4.1
 */
public class VersionedCertificateBundleStore implements CertificateBundleStore {

	private final VaultOperations operations;

	private final VaultVersionedKeyValueOperations kv;

	private final MountInfo mountInfo;

	private final String dataPath;

	private final Function<String, String> pathMapper;


	/**
	 * Create a {@code VersionedCertificateBundlesStore} using the given
	 * {@link VaultOperations} storing certificate bundles using the given
	 * {@code backendPath}.
	 * <p>Certificate bundle names are used as-is as paths in the KV2 backend.
	 * @param operations the Vault operations.
	 * @param backendPath the kv2 backend mount path.
	 */
	public VersionedCertificateBundleStore(VaultOperations operations, String backendPath) {
		this(operations, backendPath, Function.identity());
	}

	/**
	 * Create a {@code VersionedCertificateBundlesStore} using the given
	 * {@link VaultOperations} storing certificate bundles using the given
	 * {@code backendPath}.
	 * <p>The Certificate bundle names are derived from the given {@code pathMapper}
	 * that is called with the bundle name and returns the secret path.
	 * @param operations the Vault operations.
	 * @param backendPath the kv2 backend mount path.
	 * @param pathMapper path mapper to derive the secret paths from bundle names.
	 */
	public VersionedCertificateBundleStore(VaultOperations operations, String backendPath,
			Function<String, String> pathMapper) {
		String normalizedBackendPath = backendPath.endsWith("/") ? backendPath : backendPath + "/";
		MountInfo mountInfo = new KeyValueDelegate(operations).getMountInfo(normalizedBackendPath);
		this.operations = operations;
		this.kv = operations.opsForVersionedKeyValue(mountInfo.getPath());
		this.mountInfo = mountInfo;
		this.dataPath = getDataPath(normalizedBackendPath);
		this.pathMapper = pathMapper;
	}


	@Override
	public void registerBundle(String name, CertificateBundle bundle) {

		Instant now = Instant.now();
		Instant expiry = bundle.getX509Certificate().getNotAfter().toInstant();
		Duration expiryAfter = Duration.between(now, expiry);
		String key = pathMapper.apply(name);

		Map<String, Object> data = Map.of("ca_chain", bundle.getCaChain(), "certificate", bundle.getCertificate(),
				"issuing_ca", bundle.getIssuingCaCertificate(), "private_key", bundle.getPrivateKey(),
				"private_key_type", bundle.getPrivateKeyType(), "serial_number", bundle.getSerialNumber(), "expiration",
				expiry.getEpochSecond());

		operations.write(mountInfo.getPath() + "metadata/" + dataPath + key,
				Map.of("delete_version_after", expiryAfter.toSeconds()));
		kv.put(dataPath + key, data);
	}

	@Override
	public @Nullable CertificateBundle getBundle(String name) {

		String key = pathMapper.apply(name);
		Versioned<CertificateBundle> versioned = kv.get(dataPath + key, CertificateBundle.class);

		if (versioned == null || !versioned.hasData()) {
			return null;
		}

		return versioned.getRequiredData();
	}

	private String getDataPath(String requestedSecret) {

		if (mountInfo.getPath().equals(requestedSecret + "/")) {
			return "";
		}

		if (!requestedSecret.startsWith(mountInfo.getPath())) {
			return requestedSecret + "/";
		}

		return requestedSecret.substring(mountInfo.getPath().length());
	}

}
