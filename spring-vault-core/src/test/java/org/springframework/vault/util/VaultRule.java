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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.junit.rules.ExternalResource;

import org.springframework.util.Assert;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

/**
 * Vault rule to ensure a running and prepared Vault.
 *
 * @author Mark Paluch
 */
public class VaultRule extends ExternalResource {

	private final VaultEndpoint vaultEndpoint;

	private final PrepareVault prepareVault;

	private VaultToken token;

	/**
	 * Create a new {@link VaultRule} with default SSL configuration and endpoint.
	 *
	 * @see Settings#createSslConfiguration()
	 * @see VaultEndpoint
	 */
	public VaultRule() {
		this(Settings.createSslConfiguration(), new VaultEndpoint());
	}

	/**
	 * Create a new {@link VaultRule} with the given {@link SslConfiguration} and
	 * {@link VaultEndpoint}.
	 *
	 * @param sslConfiguration must not be {@literal null}.
	 * @param vaultEndpoint must not be {@literal null}.
	 */
	public VaultRule(SslConfiguration sslConfiguration, VaultEndpoint vaultEndpoint) {

		Assert.notNull(sslConfiguration, "SslConfiguration must not be null");
		Assert.notNull(vaultEndpoint, "VaultEndpoint must not be null");

		RestTemplate restTemplate = TestRestTemplateFactory.create(sslConfiguration);

		VaultTemplate vaultTemplate = new VaultTemplate(
				TestRestTemplateFactory.TEST_VAULT_ENDPOINT,
				restTemplate.getRequestFactory(), new PreparingSessionManager());

		this.token = Settings.token();
		this.prepareVault = new PrepareVault(
				TestRestTemplateFactory.create(sslConfiguration), vaultTemplate);
		this.vaultEndpoint = vaultEndpoint;
	}

	@Override
	public void before() {

		Socket socket = null;
		try {

			socket = new Socket();

			socket.connect(new InetSocketAddress(InetAddress.getByName("localhost"),
					vaultEndpoint.getPort()));
			socket.close();

		}
		catch (Exception ex) {
			throw new IllegalStateException(
					String.format(
							"Vault is not running on localhost:%d which is required to run a test using @Rule %s",
							vaultEndpoint.getPort(), getClass().getSimpleName()));
		}
		finally {
			if (socket != null) {
				try {
					socket.close();
				}
				catch (IOException e) {
				}
			}
		}

		if (!this.prepareVault.isAvailable()) {
			this.token = prepareVault.initializeVault();
			this.prepareVault.createToken(Settings.token().getToken(), "root");
			this.token = Settings.token();
		}
	}

	public PrepareVault prepare() {
		return prepareVault;
	}

	private class PreparingSessionManager implements SessionManager {

		@Override
		public VaultToken getSessionToken() {
			return token;
		}
	}
}
