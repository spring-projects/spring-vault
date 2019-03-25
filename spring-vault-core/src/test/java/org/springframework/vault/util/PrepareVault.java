/*
 * Copyright 2016-2018 the original author or authors.
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
package org.springframework.vault.util;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultSysOperations;
import org.springframework.vault.support.VaultHealth;
import org.springframework.vault.support.VaultInitializationRequest;
import org.springframework.vault.support.VaultInitializationResponse;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.VaultTokenRequest;
import org.springframework.vault.support.VaultTokenResponse;
import org.springframework.vault.support.VaultUnsealStatus;
import org.springframework.vault.support.VaultTokenRequest.VaultTokenRequestBuilder;
import org.springframework.web.client.RestTemplate;

/**
 * Vault preparation utility class. This class allows preparing Vault for integration
 * tests.
 *
 * @author Mark Paluch
 */
public class PrepareVault {

	private final RestTemplate restTemplate;

	private final VaultOperations vaultOperations;

	private final VaultSysOperations adminOperations;

	public PrepareVault(RestTemplate restTemplate, VaultOperations vaultOperations) {

		this.restTemplate = restTemplate;
		this.vaultOperations = vaultOperations;
		this.adminOperations = vaultOperations.opsForSys();
	}

	/**
	 * Initialize Vault and unseal the vault.
	 *
	 * @return the root token.
	 */
	public VaultToken initializeVault() {

		int createKeys = 2;
		int requiredKeys = 2;

		VaultInitializationResponse initialized = vaultOperations.opsForSys().initialize(
				VaultInitializationRequest.create(createKeys, requiredKeys));

		for (int i = 0; i < requiredKeys; i++) {

			VaultUnsealStatus unsealStatus = vaultOperations.opsForSys().unseal(
					initialized.getKeys().get(i));

			if (!unsealStatus.isSealed()) {
				break;
			}
		}

		return initialized.getRootToken();
	}

	/**
	 * Create a token for the given {@code tokenId} and {@code policy}.
	 *
	 * @param tokenId
	 * @param policy
	 * @return
	 */
	public VaultToken createToken(String tokenId, String policy) {

		VaultTokenRequestBuilder builder = VaultTokenRequest.builder().id(tokenId);

		if (StringUtils.hasText(policy)) {
			builder.withPolicy(policy);
		}

		VaultTokenResponse vaultTokenResponse = vaultOperations.opsForToken().create(
				builder.build());
		return vaultTokenResponse.getToken();
	}

	/**
	 * Check whether Vault is available (vault created and unsealed).
	 *
	 * @return
	 */
	public boolean isAvailable() {
		return adminOperations.isInitialized() && !adminOperations.health().isSealed();
	}

	/**
	 * Mount an auth backend.
	 *
	 * @param authBackend
	 */
	public void mountAuth(String authBackend) {

		Assert.hasText(authBackend, "AuthBackend must not be empty");

		adminOperations.authMount(authBackend, VaultMount.create(authBackend));
	}

	/**
	 * Check whether a auth-backend is enabled.
	 *
	 * @param authBackend
	 * @return
	 */
	public boolean hasAuth(String authBackend) {

		Assert.hasText(authBackend, "AuthBackend must not be empty");

		return adminOperations.getAuthMounts().containsKey(authBackend + "/");
	}

	/**
	 * Mount an secret backend.
	 *
	 * @param secretBackend
	 */
	public void mountSecret(String secretBackend) {

		Assert.hasText(secretBackend, "SecretBackend must not be empty");

		adminOperations.mount(secretBackend, VaultMount.create(secretBackend));
	}

	/**
	 * Check whether a auth-backend is enabled.
	 *
	 * @param secretBackend
	 * @return
	 */
	public boolean hasSecret(String secretBackend) {

		Assert.hasText(secretBackend, "SecretBackend must not be empty");
		return adminOperations.getMounts().containsKey(secretBackend + "/");
	}

	/**
	 * @return Vault version from the health check. Versions beginning from Vault 0.6.2
	 * will expose a version number.
	 */
	public Version getVersion() {

		VaultHealth health = getVaultOperations().opsForSys().health();

		if (StringUtils.hasText(health.getVersion())) {

			String version = health.getVersion();

			// Migration code for Vault 0.6.1
			if (version.startsWith("Vault v")) {
				version = version.substring(7);
			}

			return Version.parse(version);
		}

		return Version.parse("0.0.0");
	}

	public VaultOperations getVaultOperations() {
		return vaultOperations;
	}

	public RestTemplate getRestTemplate() {
		return restTemplate;
	}
}
