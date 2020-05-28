/*
 * Copyright 2018-2020 the original author or authors.
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
package org.springframework.vault.core;

import java.util.List;

import org.springframework.lang.Nullable;

/**
 * Interface that specifies a basic set of Vault operations using Vault's Key/Value secret
 * backend. Paths used in this operations interface are relative and outgoing requests
 * prepend paths with the according operation-specific prefix.
 * <p/>
 *
 * @author Mark Paluch
 * @since 2.1
 */
public interface VaultKeyValueOperationsSupport {

	/**
	 * Enumerate keys from a Vault path.
	 * @param path must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	@Nullable
	List<String> list(String path);

	/**
	 * Read the secret at {@code path}.
	 * @param path must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	@Nullable
	Object get(String path);

	/**
	 * Delete the secret at {@code path}.
	 * @param path must not be {@literal null}.
	 */
	void delete(String path);

	/**
	 * @return the used API version.
	 */
	KeyValueBackend getApiVersion();

	/**
	 * Enumeration of supported Key/Value backend API versions.
	 */
	enum KeyValueBackend {

		/**
		 * Key/Value backend version 1 (unversioned).
		 */
		KV_1,

		/**
		 * K/V backend version 2 (versioned).
		 */
		KV_2;

		/**
		 * @return the K/V version 1 (unversioned).
		 */
		public static KeyValueBackend unversioned() {
			return KV_1;
		}

		/**
		 * @return the K/V version 2 (versioned).
		 */
		public static KeyValueBackend versioned() {
			return KV_2;
		}

	}

}
