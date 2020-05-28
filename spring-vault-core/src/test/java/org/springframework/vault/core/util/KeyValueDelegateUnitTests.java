/*
 * Copyright 2019-2020 the original author or authors.
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
package org.springframework.vault.core.util;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.vault.core.VaultKeyValueOperationsSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.vault.core.util.KeyValueDelegate.MountInfo;
import static org.springframework.vault.core.util.KeyValueDelegate.getKeyValue2Path;

/**
 * Unit tests for {@link KeyValueDelegate}.
 *
 * @author Mark Paluch
 */
class KeyValueDelegateUnitTests {

	@Test
	void getKeyValue2PathShouldConstructKeyValue2BackendPath() {

		String path = getKeyValue2Path("foo/bar/versioned/", "foo/bar/versioned/my/key");

		assertThat(path).isEqualTo("foo/bar/versioned/data/my/key");
	}

	@Test
	void getKeyValue2PathShouldIgnoreNotMatchingPath() {

		String path = getKeyValue2Path("unknown/", "foo/bar/versioned/my/key");

		assertThat(path).isEqualTo("foo/bar/versioned/my/key");
	}

	@Test
	void shouldConsiderKeyValueVersion() {

		assertThat(MountInfo.from("foo", Collections.singletonMap("version", "1"))
				.isKeyValue(VaultKeyValueOperationsSupport.KeyValueBackend.KV_1)).isTrue();

		assertThat(MountInfo.from("foo", Collections.singletonMap("version", 1))
				.isKeyValue(VaultKeyValueOperationsSupport.KeyValueBackend.KV_1)).isTrue();

		assertThat(MountInfo.from("foo", Collections.singletonMap("version", "2"))
				.isKeyValue(VaultKeyValueOperationsSupport.KeyValueBackend.KV_2)).isTrue();

		assertThat(MountInfo.from("foo", Collections.singletonMap("version", 2))
				.isKeyValue(VaultKeyValueOperationsSupport.KeyValueBackend.KV_1)).isFalse();

		assertThat(MountInfo.from("foo", Collections.singletonMap("version", "2"))
				.isKeyValue(VaultKeyValueOperationsSupport.KeyValueBackend.KV_1)).isFalse();

		assertThat(MountInfo.from("foo", Collections.emptyMap())
				.isKeyValue(VaultKeyValueOperationsSupport.KeyValueBackend.KV_1)).isFalse();
	}

}
