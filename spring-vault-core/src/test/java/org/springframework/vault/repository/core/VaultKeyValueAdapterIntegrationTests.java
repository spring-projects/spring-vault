/*
 * Copyright 2025 the original author or authors.
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

package org.springframework.vault.repository.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultIntegrationTestConfiguration;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.util.KeyValueDelegate;
import org.springframework.vault.domain.Person;
import org.springframework.vault.util.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link VaultKeyValueAdapter}.
 *
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
class VaultKeyValueAdapterIntegrationTests extends IntegrationTestSupport {

	@Autowired
	VaultTemplate template;

	@Test
	void shouldFailOnAbsentKeyspace() {

		VaultKeyValueAdapter adapter = new VaultKeyValueAdapter(template);

		assertThatExceptionOfType(VaultException.class).isThrownBy(() -> adapter.get("some-id", "absent", Person.class))
				.withMessageContaining("Cannot determine MountInfo");
	}

	@Test
	void shouldReturnVersionedMountInfo() {

		KeyValueDelegate delegate = new KeyValueDelegate(template);

		KeyValueDelegate.MountInfo mountInfo = delegate.getMountInfo("versioned/nothing/here");

		assertThat(mountInfo.isAvailable()).isTrue();
		assertThat(mountInfo.getOptions()).containsEntry("version", "2");
	}

	@Test
	void shouldReturnUnversionedMountInfo() {

		KeyValueDelegate delegate = new KeyValueDelegate(template);

		KeyValueDelegate.MountInfo mountInfo = delegate.getMountInfo("secret/nothing/here");

		assertThat(mountInfo.isAvailable()).isTrue();
		assertThat(mountInfo.getOptions()).doesNotContainEntry("version", "2");
	}

}
