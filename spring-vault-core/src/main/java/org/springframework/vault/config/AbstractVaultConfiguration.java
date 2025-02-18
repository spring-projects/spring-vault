/*
 * Copyright 2016-2025 the original author or authors.
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
package org.springframework.vault.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.LifecycleAwareSessionManager;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.authentication.event.AuthenticationEventMulticaster;
import org.springframework.vault.client.ClientHttpRequestFactoryFactory;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.RestTemplateCustomizer;
import org.springframework.vault.client.RestTemplateFactory;
import org.springframework.vault.client.SimpleVaultEndpointProvider;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.VaultEndpointProvider;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/**
 * Base class for Spring Vault configuration using JavaConfig.
 *
 * @author Spencer Gibb
 * @author Mark Paluch
 */
@Configuration(proxyBeanMethods = false)
public abstract class AbstractVaultConfiguration implements ApplicationContextAware {

	private @Nullable ApplicationContext applicationContext;

	/**
	 * @return Vault endpoint coordinates for HTTP/HTTPS communication, must not be
	 * {@literal null}.
	 */
	public abstract VaultEndpoint vaultEndpoint();

	/**
	 * @return a {@link VaultEndpointProvider} returning the value of
	 * {@link #vaultEndpoint()}.
	 * @since 1.1
	 */
	public VaultEndpointProvider vaultEndpointProvider() {
		return SimpleVaultEndpointProvider.of(vaultEndpoint());
	}

	/**
	 * Annotate with {@link Bean} in case you want to expose a
	 * {@link ClientAuthentication} instance to the
	 * {@link org.springframework.context.ApplicationContext}.
	 * @return the {@link ClientAuthentication} to use. Must not be {@literal null}.
	 */
	public abstract ClientAuthentication clientAuthentication();

	/**
	 * Create a {@link RestTemplateBuilder} initialized with {@link VaultEndpointProvider}
	 * and {@link ClientHttpRequestFactory}. May be overridden by subclasses.
	 * @return the {@link RestTemplateBuilder}.
	 * @see #vaultEndpointProvider()
	 * @see #clientHttpRequestFactoryWrapper()
	 * @since 2.3
	 */
	protected RestTemplateBuilder restTemplateBuilder(VaultEndpointProvider endpointProvider,
			ClientHttpRequestFactory requestFactory) {

		ObjectProvider<RestTemplateCustomizer> customizers = getBeanFactory()
			.getBeanProvider(RestTemplateCustomizer.class);

		RestTemplateBuilder builder = RestTemplateBuilder.builder()
			.endpointProvider(endpointProvider)
			.requestFactory(requestFactory);

		builder.customizers(customizers.stream().toArray(RestTemplateCustomizer[]::new));

		return builder;
	}

	/**
	 * Create a {@link RestTemplateFactory} bean that is used to produce
	 * {@link RestTemplate}.
	 * @return the {@link RestTemplateFactory}.
	 * @see #vaultEndpointProvider()
	 * @see #clientHttpRequestFactoryWrapper()
	 * @since 2.3
	 */
	@Bean
	public RestTemplateFactory restTemplateFactory(ClientFactoryWrapper requestFactoryWrapper) {

		return new DefaultRestTemplateFactory(requestFactoryWrapper.getClientHttpRequestFactory(), it -> {
			return restTemplateBuilder(vaultEndpointProvider(), it);
		});
	}

	/**
	 * Create a {@link VaultTemplate}.
	 * @return the {@link VaultTemplate}.
	 * @see #vaultEndpointProvider()
	 * @see #clientHttpRequestFactoryWrapper()
	 * @see #sessionManager()
	 */
	@Bean
	public VaultTemplate vaultTemplate() {
		return new VaultTemplate(
				restTemplateBuilder(vaultEndpointProvider(), getClientFactoryWrapper().getClientHttpRequestFactory()),
				getBeanFactory().getBean("sessionManager", SessionManager.class));
	}

	/**
	 * Construct a {@link LifecycleAwareSessionManager} using
	 * {@link #clientAuthentication()}. This {@link SessionManager} uses
	 * {@link #threadPoolTaskScheduler()}.
	 * @return the {@link SessionManager} for Vault session management.
	 * @see SessionManager
	 * @see LifecycleAwareSessionManager
	 * @see #restOperations()
	 * @see #clientAuthentication()
	 * @see #threadPoolTaskScheduler() ()
	 */
	@Bean
	public SessionManager sessionManager() {

		ClientAuthentication clientAuthentication = clientAuthentication();

		Assert.notNull(clientAuthentication, "ClientAuthentication must not be null");

		return new LifecycleAwareSessionManager(clientAuthentication, getVaultThreadPoolTaskScheduler(),
				restOperations());
	}

	/**
	 * Construct a {@link SecretLeaseContainer} using {@link #vaultTemplate()} and
	 * {@link #threadPoolTaskScheduler()}.
	 * @return the {@link SecretLeaseContainer} to allocate, renew and rotate secrets and
	 * their leases.
	 * @see #vaultTemplate()
	 * @see #threadPoolTaskScheduler()
	 */
	@Bean
	public SecretLeaseContainer secretLeaseContainer() throws Exception {

		SecretLeaseContainer secretLeaseContainer = new SecretLeaseContainer(
				getBeanFactory().getBean("vaultTemplate", VaultTemplate.class), getVaultThreadPoolTaskScheduler());
		SessionManager sessionManager = getBeanFactory().getBean("sessionManager", SessionManager.class);

		secretLeaseContainer.afterPropertiesSet();

		if (sessionManager instanceof AuthenticationEventMulticaster multicaster) {
			multicaster.addAuthenticationListener(secretLeaseContainer.getAuthenticationListener());
			multicaster.addErrorListener(secretLeaseContainer.getAuthenticationErrorListener());
		}

		secretLeaseContainer.start();

		return secretLeaseContainer;
	}

	/**
	 * Create a {@link TaskSchedulerWrapper} used by {@link LifecycleAwareSessionManager}
	 * and {@link org.springframework.vault.core.lease.SecretLeaseContainer} wrapping
	 * {@link ThreadPoolTaskScheduler}. Subclasses may override this method to reuse a
	 * different/existing scheduler.
	 * @return the {@link TaskSchedulerWrapper} to use. Must not be {@literal null}.
	 * @see TaskSchedulerWrapper#fromInstance(ThreadPoolTaskScheduler)
	 */
	@Bean("vaultThreadPoolTaskScheduler")
	public TaskSchedulerWrapper threadPoolTaskScheduler() {

		ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();

		threadPoolTaskScheduler.setThreadNamePrefix("spring-vault-ThreadPoolTaskScheduler-");
		threadPoolTaskScheduler.setDaemon(true);

		return new TaskSchedulerWrapper(threadPoolTaskScheduler);
	}

	/**
	 * Construct a {@link RestOperations} object configured for Vault session management
	 * and authentication usage. Can be customized by providing a
	 * {@link RestTemplateFactory} bean.
	 * @return the {@link RestOperations} to be used for Vault access.
	 * @see #restTemplateFactory(ClientFactoryWrapper)
	 */
	public RestOperations restOperations() {
		return getRestTemplateFactory().create();
	}

	/**
	 * Create a {@link ClientFactoryWrapper} containing a {@link ClientHttpRequestFactory}
	 * . {@link ClientHttpRequestFactory} is not exposed as root bean because
	 * {@link ClientHttpRequestFactory} is configured with {@link ClientOptions} and
	 * {@link SslConfiguration} which are not necessarily applicable for the whole
	 * application.
	 * @return the {@link ClientFactoryWrapper} to wrap a {@link ClientHttpRequestFactory}
	 * instance.
	 * @see #clientOptions()
	 * @see #sslConfiguration()
	 */
	@Bean
	public ClientFactoryWrapper clientHttpRequestFactoryWrapper() {
		return new ClientFactoryWrapper(ClientHttpRequestFactoryFactory.create(clientOptions(), sslConfiguration()));
	}

	/**
	 * @return {@link ClientOptions} to configure communication parameters.
	 * @see ClientOptions
	 */
	public ClientOptions clientOptions() {
		return new ClientOptions();
	}

	/**
	 * @return SSL configuration options. Defaults to
	 * {@link SslConfiguration#unconfigured()}.
	 * @see SslConfiguration
	 * @see SslConfiguration#unconfigured()
	 */
	public SslConfiguration sslConfiguration() {
		return SslConfiguration.unconfigured();
	}

	/**
	 * Return the {@link Environment} to access property sources during Spring Vault
	 * bootstrapping. Requires {@link #setApplicationContext(ApplicationContext)
	 * ApplicationContext} to be set.
	 * @return the {@link Environment} to access property sources during Spring Vault
	 * bootstrapping.
	 */
	protected Environment getEnvironment() {

		Assert.state(this.applicationContext != null,
				"ApplicationContext must be set before accessing getEnvironment()");

		return this.applicationContext.getEnvironment();
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	/**
	 * Return the {@link RestTemplateFactory}.
	 * @return the {@link RestTemplateFactory} bean.
	 * @since 2.3
	 */
	protected RestTemplateFactory getRestTemplateFactory() {
		return getBeanFactory().getBean(RestTemplateFactory.class);
	}

	protected ThreadPoolTaskScheduler getVaultThreadPoolTaskScheduler() {
		return getBeanFactory().getBean("vaultThreadPoolTaskScheduler", TaskSchedulerWrapper.class).getTaskScheduler();
	}

	protected BeanFactory getBeanFactory() {

		Assert.state(this.applicationContext != null,
				"ApplicationContext must be set before accessing getBeanFactory()");

		return this.applicationContext;
	}

	private ClientFactoryWrapper getClientFactoryWrapper() {
		return getBeanFactory().getBean("clientHttpRequestFactoryWrapper", ClientFactoryWrapper.class);
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
			if (this.clientHttpRequestFactory instanceof DisposableBean) {
				((DisposableBean) this.clientHttpRequestFactory).destroy();
			}
		}

		@Override
		public void afterPropertiesSet() throws Exception {

			if (this.clientHttpRequestFactory instanceof InitializingBean) {
				((InitializingBean) this.clientHttpRequestFactory).afterPropertiesSet();
			}
		}

		public ClientHttpRequestFactory getClientHttpRequestFactory() {
			return this.clientHttpRequestFactory;
		}

	}

	/**
	 * Wrapper to keep {@link ThreadPoolTaskScheduler} local to Spring Vault and to not
	 * expose the bean globally.
	 *
	 * @since 2.3.1
	 */
	public static class TaskSchedulerWrapper implements InitializingBean, DisposableBean {

		private final ThreadPoolTaskScheduler taskScheduler;

		private final boolean acceptAfterPropertiesSet;

		private final boolean acceptDestroy;

		public TaskSchedulerWrapper(ThreadPoolTaskScheduler taskScheduler) {
			this(taskScheduler, true, true);
		}

		protected TaskSchedulerWrapper(ThreadPoolTaskScheduler taskScheduler, boolean acceptAfterPropertiesSet,
				boolean acceptDestroy) {

			Assert.notNull(taskScheduler, "ThreadPoolTaskScheduler must not be null");

			this.taskScheduler = taskScheduler;
			this.acceptAfterPropertiesSet = acceptAfterPropertiesSet;
			this.acceptDestroy = acceptDestroy;
		}

		/**
		 * Factory method to adapt an existing {@link ThreadPoolTaskScheduler} bean
		 * without calling lifecycle methods.
		 * @param scheduler the actual {@code ThreadPoolTaskScheduler}.
		 * @return the wrapper for the given {@link ThreadPoolTaskScheduler}.
		 * @see #afterPropertiesSet()
		 * @see #destroy()
		 */
		public static TaskSchedulerWrapper fromInstance(ThreadPoolTaskScheduler scheduler) {
			return new TaskSchedulerWrapper(scheduler, false, false);
		}

		ThreadPoolTaskScheduler getTaskScheduler() {
			return this.taskScheduler;
		}

		@Override
		public void destroy() {
			if (acceptDestroy) {
				this.taskScheduler.destroy();
			}
		}

		@Override
		public void afterPropertiesSet() {

			if (this.acceptAfterPropertiesSet) {
				this.taskScheduler.afterPropertiesSet();
			}
		}

	}

}
