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

import org.assertj.core.util.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.CertificateBundle;
import org.springframework.vault.support.VaultMetadataResponse;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Settings;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link VersionedCertificateBundleStore}.
 *
 * @author Mark Paluch
 */
class VersionedCertificateBundleStoreIntegrationTests extends IntegrationTestSupport {

	File workDir = Settings.findWorkDir();

	String cert = Files.contentOf(new File(workDir, "ca/certs/localhost.cert.pem"), StandardCharsets.US_ASCII);

	String key = Files.contentOf(new File(workDir, "ca/private/intermediate.decrypted.key.pem"),
			StandardCharsets.US_ASCII);

	CertificateBundle bundle = CertificateBundle.of("foo", cert, cert, key);

	VaultOperations vaultOperations;

	VaultKeyValueOperations kv;

	@BeforeEach
	void setUp() {
		vaultOperations = prepare().getVaultOperations();
		kv = vaultOperations.opsForKeyValue("versioned", KeyValueBackend.versioned());

		DefaultUriBuilderFactory d = new DefaultUriBuilderFactory();

		kv.list("ssl-bundles").forEach(it -> {
			kv.delete("ssl-bundles/" + it);
		});
		kv.list("my-app").forEach(it -> {
			kv.delete("ssl-bundles/" + it);
		});
		kv.list("").forEach(it -> {
			kv.delete("" + it);
		});
	}

	@Test
	void shouldStoreBundle() {

		VersionedCertificateBundleStore store = new VersionedCertificateBundleStore(vaultOperations, "versioned");

		store.registerBundle("www.example.com", bundle);

		assertThat(kv.list("")).contains("www.example.com");

		CertificateBundle stored = store.getBundle("www.example.com");
		assertThat(stored.getPrivateKey()).isEqualTo(bundle.getPrivateKey());
		assertThat(stored.getCertificate()).isEqualTo(bundle.getCertificate());
		assertThat(stored.getSerialNumber()).isEqualTo(bundle.getSerialNumber());

		VaultVersionedKeyValueOperations versioned = vaultOperations.opsForVersionedKeyValue("versioned");
		VaultMetadataResponse metadata = versioned.opsForKeyValueMetadata().get("www.example.com");
		assertThat(metadata.getDeleteVersionAfter()).isGreaterThan(Duration.ofHours(1));
	}

	@Test
	void shouldApplyPathMapper() {

		VersionedCertificateBundleStore store = new VersionedCertificateBundleStore(vaultOperations,
				"versioned/my-app/ssl-bundles", s -> "cert-" + s);

		store.registerBundle("www.example.com", bundle);
		assertThat(kv.list("my-app/ssl-bundles")).contains("cert-www.example.com");
		assertThat(store.getBundle("www.example.com")).isNotNull();
	}

	@Test
	void shouldNotReturnAbsentBundle() {

		VersionedCertificateBundleStore store = new VersionedCertificateBundleStore(vaultOperations,
				"versioned/ssl-bundles");

		assertThat(store.getBundle("www.example.com")).isNull();
	}

}
