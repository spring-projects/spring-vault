/*
 * Copyright 2019-2021 the original author or authors.
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.lang.Nullable;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;

/**
 * Key-Value utility to retrieve secrets from a versioned key-value backend. For internal
 * use within the framework.
 * <p/>
 * Uses Vault's internal API {@code sys/internal/ui/mounts} to determine mount
 * information.
 *
 * @author Mark Paluch
 * @since 2.2
 */
public class KeyValueDelegate {

	private final Map<String, MountInfo> mountInfo;

	private final VaultOperations operations;

	public KeyValueDelegate(VaultOperations operations) {
		this(operations, ConcurrentReferenceHashMap::new);
	}

	@SuppressWarnings("unchecked")
	public KeyValueDelegate(VaultOperations operations, Supplier<Map<String, ?>> cacheSupplier) {
		this.operations = operations;
		this.mountInfo = (Map) cacheSupplier.get();
	}

	/**
	 * Determine whether the {@code path} belongs to a versioned Key-Value mount.
	 * @param path the path to inspect.
	 * @return {@literal true} if the {@code path} belongs to a versioned Key-Value mount.
	 */
	public boolean isVersioned(String path) {
		return getMountInfo(path).isKeyValue(KeyValueBackend.versioned());
	}

	/**
	 * Read a secret from a key-value backend. Considers the backend type and whether the
	 * backend is a versioned key-value backend.
	 * @param path the path to fetch the secret from.
	 * @return the secret, can be {@literal null}.
	 */
	@Nullable
	public VaultResponse getSecret(String path) {

		MountInfo mountInfo = this.mountInfo.get(path);

		if (!mountInfo.isKeyValue(KeyValueBackend.versioned())) {
			return this.operations.read(path);
		}

		VaultResponse response = this.operations.read(getKeyValue2Path(mountInfo.getPath(), path));
		unwrapDataResponse(response);

		return response;
	}

	static String getKeyValue2Path(String mountPath, String requestedSecret) {

		if (!requestedSecret.startsWith(mountPath)) {
			return requestedSecret;
		}

		String keyPath = requestedSecret.substring(mountPath.length());

		return String.format("%sdata/%s", mountPath, keyPath);
	}

	@SuppressWarnings("unchecked")
	private static void unwrapDataResponse(@Nullable VaultResponse response) {

		if (response == null || response.getData() == null || !response.getData().containsKey("data")) {
			return;
		}

		Map<String, Object> nested = new LinkedHashMap<>((Map) response.getRequiredData().get("data"));
		response.setData(nested);
	}

	@SuppressWarnings("unchecked")
	private MountInfo doGetMountInfo(String path) {

		VaultResponse response = this.operations.read(String.format("sys/internal/ui/mounts/%s", path));

		if (response == null || response.getData() == null) {
			return MountInfo.unavailable();
		}

		Map<String, Object> data = response.getData();
		return MountInfo.from((String) data.get("path"), (Map) data.get("options"));
	}

	private MountInfo getMountInfo(String path) {

		MountInfo mountInfo = this.mountInfo.get(path);

		if (mountInfo == null) {
			try {

				mountInfo = doGetMountInfo(path);
			}
			catch (RuntimeException e) {
				mountInfo = MountInfo.unavailable();
			}

			this.mountInfo.put(path, mountInfo);
		}
		return mountInfo;
	}

	static class MountInfo {

		static final MountInfo UNAVAILABLE = new MountInfo("", Collections.emptyMap(), false);

		final String path;

		final @Nullable Map<String, Object> options;

		final boolean available;

		private MountInfo(String path, @Nullable Map<String, Object> options, boolean available) {
			this.path = path;
			this.options = options;
			this.available = available;
		}

		/**
		 * Creates a new {@link MountInfo} representing an absent {@link MountInfo}.
		 * @return a new {@link MountInfo} representing an absent {@link MountInfo}.
		 */
		static MountInfo unavailable() {
			return UNAVAILABLE;
		}

		/**
		 * Creates a new {@link MountInfo} given {@code path} and {@link Map options map}.
		 * @param path
		 * @param options
		 * @return a new {@link MountInfo} for {@code path} and {@link Map options map}.
		 */
		static MountInfo from(String path, @Nullable Map<String, Object> options) {
			return new MountInfo(path, options, true);
		}

		boolean isKeyValue(KeyValueBackend versioned) {

			if (!isAvailable() || !StringUtils.hasText(this.path) || this.options == null) {
				return false;
			}

			Object version = this.options.get("version");

			if (version != null) {

				if (version.toString().equals("1") && versioned == KeyValueBackend.KV_1) {
					return true;
				}

				if (version.toString().equals("2") && versioned == KeyValueBackend.KV_2) {
					return true;
				}
			}

			return false;
		}

		public String getPath() {
			return this.path;
		}

		@Nullable
		public Map<String, Object> getOptions() {
			return this.options;
		}

		public boolean isAvailable() {
			return this.available;
		}

	}

}
