/*
 * Copyright 2016-2019 the original author or authors.
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
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Vault rule to ensure a running and prepared Vault. Prepared means unsealed, having a
 * non-versioning key-value backend mounted at {@code secret/} and a {@link VaultToken}
 * with {@code root} privileges.
 *
 * @author Mark Paluch
 * @see Settings#token()
 */
public class VaultRule extends ExternalResource {

	public static final Version VERSIONING_INTRODUCED_WITH = Version.parse("0.10.0");

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
		WebClient webClient = TestWebClientFactory.create(sslConfiguration);

		VaultTemplate vaultTemplate = new VaultTemplate(
				TestRestTemplateFactory.TEST_VAULT_ENDPOINT,
				restTemplate.getRequestFactory(), new PreparingSessionManager());

		this.token = Settings.token();

		this.prepareVault = new PrepareVault(webClient,
				TestRestTemplateFactory.create(sslConfiguration), vaultTemplate);
		this.vaultEndpoint = vaultEndpoint;
	}

	@Override
	public void before() {

		assertRunningVault();

		if (!this.prepareVault.isAvailable()) {
			this.token = prepareVault.initializeVault();
			this.prepareVault.createToken(Settings.token().getToken(), "root");

			if (this.prepareVault.getVersion().isGreaterThanOrEqualTo(
					VERSIONING_INTRODUCED_WITH)) {
				this.prepareVault.disableGenericVersioning();
				this.prepareVault.mountVersionedKvBackend();
			}

			this.token = Settings.token();
		}
	}

	private void assertRunningVault() {

		try (Socket socket = new Socket()) {

			socket.connect(new InetSocketAddress(InetAddress.getByName("localhost"),
					vaultEndpoint.getPort()));
		}
		catch (Exception ex) {
			throw new IllegalStateException(
					String.format(
							"Vault is not running on localhost:%d which is required to run a test using @Rule %s",
							vaultEndpoint.getPort(), getClass().getSimpleName()));
		}
	}

	/**
	 * @return the {@link PrepareVault} object.
	 */
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
