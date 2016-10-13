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
package org.springframework.vault.core;

import org.springframework.util.Assert;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.web.client.RestTemplate;

/**
 * Default implementation of {@link VaultClientFactory}. Returns the provided
 * {@link VaultClient}.
 *
 * @author Mark Paluch
 */
public class DefaultVaultClientFactory implements VaultClientFactory {

	private final VaultClient vaultClient;

	/**
	 * Creates a new {@link DefaultVaultClientFactory} returning always the same
	 * {@link VaultClient}.
	 * 
	 * @param vaultClient must not be {@literal null}.
	 */
	public DefaultVaultClientFactory(VaultClient vaultClient) {

		Assert.notNull(vaultClient, "VaultClient must not be null");

		this.vaultClient = vaultClient;
	}

	/**
	 * Creates a new {@link DefaultVaultClientFactory} using a default {@link VaultClient}
	 * and {@link VaultEndpoint}. Will use Vault at {@code https://localhost:8200} .
	 * 
	 * @see VaultClient
	 * @see VaultEndpoint
	 */
	public DefaultVaultClientFactory() {
		this(new VaultClient(new RestTemplate(), new VaultEndpoint()));
	}

	@Override
	public VaultClient getVaultClient() {
		return vaultClient;
	}
}
