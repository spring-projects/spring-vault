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

package org.springframework.vault.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.DefaultSessionManager;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.DefaultVaultClientFactory;
import org.springframework.vault.core.VaultClientFactory;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.web.client.RestTemplate;

/**
 * Base class for Spring Vault configuration using JavaConfig.
 *
 * @author Spencer Gibb
 * @author Mark Paluch
 */
@Configuration
public abstract class AbstractVaultConfiguration {

	/**
	 * @return Vault endpoint coordinates for HTTP/HTTPS communication, must not be {@literal null}.
	 */
	public abstract VaultEndpoint vaultEndpoint();

	/**
	 * Annotate with {@link Bean} in case you want to expose a {@link ClientAuthentication} instance to the
	 * {@link org.springframework.context.ApplicationContext}.
	 * 
	 * @return the {@link ClientAuthentication} to use. Must not be {@literal null}.
	 */
	public abstract ClientAuthentication clientAuthentication();

	/**
	 * Annotate with {@link Bean} in case you want to expose a {@link SessionManager} instance to the
	 * {@link org.springframework.context.ApplicationContext}.
	 * 
	 * @return the {@link SessionManager} for Vault session management.
	 * @see SessionManager
	 * @see DefaultSessionManager
	 */
	public SessionManager sessionManager() {

		ClientAuthentication clientAuthentication = clientAuthentication();
		Assert.notNull(clientAuthentication, "ClientAuthentication must not be null");

		return new DefaultSessionManager(clientAuthentication);
	}

	/**
	 * @return {@link ClientOptions} to configure communication parameters.
	 * @see ClientOptions
	 */
	public ClientOptions clientOptions() {
		return new ClientOptions();
	}

	/**
	 * @return SSL configuration options. Defaults to {@link SslConfiguration#NONE}.
	 * @see SslConfiguration
	 * @see SslConfiguration#NONE
	 */
	public SslConfiguration sslConfiguration() {
		return SslConfiguration.NONE;
	}

	/**
	 * Creates a {@link ClientFactoryWrapper} containing a {@link ClientHttpRequestFactory}.
	 * {@link ClientHttpRequestFactory} is not exposed as root bean because {@link ClientHttpRequestFactory} is configured
	 * with {@link ClientOptions} and {@link SslConfiguration} which are not necessarily applicable for the whole
	 * application.
	 * 
	 * @return the {@link ClientFactoryWrapper} to wrap a {@link ClientHttpRequestFactory} instance.
	 * @see #clientOptions()
	 * @see #sslConfiguration()
	 */
	@Bean
	public ClientFactoryWrapper clientHttpRequestFactoryWrapper() {
		return new ClientFactoryWrapper(ClientHttpRequestFactoryFactory.create(clientOptions(), sslConfiguration()));
	}

	/**
	 * @return the {@link VaultClient}
	 * @see #clientHttpRequestFactoryWrapper()
	 * @see #vaultEndpoint()
	 */
	@Bean
	public VaultClient vaultClient() {

		RestTemplate restTemplate = new RestTemplate(clientHttpRequestFactoryWrapper().getClientHttpRequestFactory());

		return new VaultClient(restTemplate, vaultEndpoint());
	}

	/**
	 * Creates the {@link VaultClientFactory} to be used with {@link VaultTemplate}. Uses by default
	 * {@link DefaultVaultClientFactory} with the configured {@link #vaultClient()} instance.
	 * 
	 * @return
	 */
	@Bean
	public VaultClientFactory vaultClientFactory() {
		return new DefaultVaultClientFactory(vaultClient());
	}

	/**
	 * Creates a {@link VaultTemplate}.
	 * 
	 * @return
	 * @see #vaultClientFactory()
	 * @see #sessionManager()
	 */
	@Bean
	public VaultTemplate vaultTemplate() {
		return new VaultTemplate(vaultClientFactory(), sessionManager());
	}

	/**
	 * Wrapper for {@link ClientHttpRequestFactory} to not expose the bean globally.
	 */
	public static class ClientFactoryWrapper implements InitializingBean, DisposableBean {

		private final ClientHttpRequestFactory clientHttpRequestFactory;

		public ClientFactoryWrapper(ClientHttpRequestFactory clientHttpRequestFactory) {
			this.clientHttpRequestFactory = clientHttpRequestFactory;
		}

		@Override
		public void destroy() throws Exception {
			if (clientHttpRequestFactory instanceof DisposableBean) {
				((DisposableBean) clientHttpRequestFactory).destroy();
			}
		}

		@Override
		public void afterPropertiesSet() throws Exception {

			if (clientHttpRequestFactory instanceof InitializingBean) {
				((InitializingBean) clientHttpRequestFactory).afterPropertiesSet();
			}
		}

		public ClientHttpRequestFactory getClientHttpRequestFactory() {
			return clientHttpRequestFactory;
		}
	}
}
