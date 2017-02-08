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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.LifecycleAwareSessionManager;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.web.client.RestOperations;

/**
 * Base class for Spring Vault configuration using JavaConfig.
 *
 * @author Spencer Gibb
 * @author Mark Paluch
 */
@Configuration
public abstract class AbstractVaultConfiguration implements ApplicationContextAware {

	private ApplicationContext applicationContext;

	/**
	 * @return Vault endpoint coordinates for HTTP/HTTPS communication, must not be
	 * {@literal null}.
	 */
	public abstract VaultEndpoint vaultEndpoint();

	/**
	 * Annotate with {@link Bean} in case you want to expose a
	 * {@link ClientAuthentication} instance to the
	 * {@link org.springframework.context.ApplicationContext}.
	 * 
	 * @return the {@link ClientAuthentication} to use. Must not be {@literal null}.
	 */
	public abstract ClientAuthentication clientAuthentication();

	/**
	 * Create a {@link AsyncTaskExecutor} used by {@link LifecycleAwareSessionManager}.
	 * Annotate with {@link Bean} in case you want to expose a {@link AsyncTaskExecutor}
	 * instance to the {@link org.springframework.context.ApplicationContext}. This might
	 * be useful to supply managed executor instances or {@link AsyncTaskExecutor}s using
	 * a queue/pooled threads.
	 * 
	 * @return the {@link AsyncTaskExecutor} to use. Must not be {@literal null}.
	 * @see AsyncTaskExecutor
	 */
	public AsyncTaskExecutor asyncTaskExecutor() {
		return new SimpleAsyncTaskExecutor("spring-vault-SimpleAsyncTaskExecutor-");
	}

	/**
	 * Construct a {@link LifecycleAwareSessionManager} using
	 * {@link #clientAuthentication()}. This {@link SessionManager} uses
	 * {@link #asyncTaskExecutor()}.
	 * 
	 * @return the {@link SessionManager} for Vault session management.
	 * @see SessionManager
	 * @see LifecycleAwareSessionManager
	 * @see #vaultEndpoint()
	 * @see #clientHttpRequestFactoryWrapper()
	 * @see #clientAuthentication()
	 * @see #asyncTaskExecutor() ()
	 */
	@Bean
	public SessionManager sessionManager() {

		ClientAuthentication clientAuthentication = clientAuthentication();
		Assert.notNull(clientAuthentication, "ClientAuthentication must not be null");

		RestOperations restOperations = VaultClients.createRestTemplate(vaultEndpoint(),
				clientHttpRequestFactoryWrapper().getClientHttpRequestFactory());

		return new LifecycleAwareSessionManager(clientAuthentication,
				asyncTaskExecutor(), restOperations);
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
	 * Create a {@link ClientFactoryWrapper} containing a {@link ClientHttpRequestFactory}
	 * . {@link ClientHttpRequestFactory} is not exposed as root bean because
	 * {@link ClientHttpRequestFactory} is configured with {@link ClientOptions} and
	 * {@link SslConfiguration} which are not necessarily applicable for the whole
	 * application.
	 * 
	 * @return the {@link ClientFactoryWrapper} to wrap a {@link ClientHttpRequestFactory}
	 * instance.
	 * @see #clientOptions()
	 * @see #sslConfiguration()
	 */
	@Bean
	public ClientFactoryWrapper clientHttpRequestFactoryWrapper() {
		return new ClientFactoryWrapper(ClientHttpRequestFactoryFactory.create(
				clientOptions(), sslConfiguration()));
	}

	/**
	 * Create a {@link VaultTemplate}.
	 * 
	 * @return the {@link VaultTemplate}.
	 * @see #vaultEndpoint()
	 * @see #clientHttpRequestFactoryWrapper()
	 * @see #sessionManager()
	 */
	@Bean
	public VaultTemplate vaultTemplate() {
		return new VaultTemplate(vaultEndpoint(), clientHttpRequestFactoryWrapper()
				.getClientHttpRequestFactory(), sessionManager());
	}

	/**
	 * Return the {@link Environment} to access property sources during Spring Vault
	 * bootstrapping. Requires {@link #setApplicationContext(ApplicationContext)
	 * ApplicationContext} to be set.
	 * 
	 * @return the {@link Environment} to access property sources during Spring Vault
	 * bootstrapping.
	 */
	protected Environment getEnvironment() {

		Assert.state(applicationContext != null,
				"ApplicationContext must be set before accessing getEnvironment()");

		return applicationContext.getEnvironment();
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.applicationContext = applicationContext;
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
