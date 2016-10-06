/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.vault.util;

import java.util.Collections;

import org.springframework.util.Assert;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultSysOperations;
import org.springframework.vault.support.VaultInitializationRequest;
import org.springframework.vault.support.VaultInitializationResponse;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.VaultTokenRequest;
import org.springframework.vault.support.VaultTokenResponse;
import org.springframework.vault.support.VaultUnsealStatus;

/**
 * Vault preparation utility class. This class allows preparing Vault for integration tests.
 * 
 * @author Mark Paluch
 */
public class PrepareVault {

	private final VaultClient vaultClient;

	private final VaultOperations vaultOperations;

	private final VaultSysOperations adminOperations;

	public PrepareVault(VaultClient vaultClient, VaultOperations vaultOperations) {

		this.vaultClient = vaultClient;
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

		VaultInitializationResponse initialized = vaultOperations.opsForSys()
				.initialize(VaultInitializationRequest.create(createKeys, requiredKeys));

		for (int i = 0; i < requiredKeys; i++) {

			VaultUnsealStatus unsealStatus = vaultOperations.opsForSys().unseal(initialized.getKeys().get(i));

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

		VaultTokenRequest tokenRequest = new VaultTokenRequest();

		tokenRequest.setId(tokenId);
		if (policy != null) {
			tokenRequest.setPolicies(Collections.singletonList(policy));
		}

		VaultTokenResponse vaultTokenResponse = vaultOperations.opsForToken().create(tokenRequest);
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

	public VaultOperations getVaultOperations() {
		return vaultOperations;
	}

	public VaultClient getVaultClient() {
		return vaultClient;
	}
}
