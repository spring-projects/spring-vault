/*
 * Copyright 2017-2020 the original author or authors.
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

import java.time.Duration;

import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.util.Assert;
import org.springframework.vault.authentication.AuthenticationStepsFactory;
import org.springframework.vault.authentication.AuthenticationStepsOperator;
import org.springframework.vault.authentication.CachingVaultTokenSupplier;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.ReactiveLifecycleAwareSessionManager;
import org.springframework.vault.authentication.ReactiveSessionManager;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.authentication.VaultTokenSupplier;
import org.springframework.vault.client.ClientHttpConnectorFactory;
import org.springframework.vault.client.ReactiveVaultClients;
import org.springframework.vault.client.ReactiveVaultEndpointProvider;
import org.springframework.vault.client.VaultEndpointProvider;
import org.springframework.vault.client.WebClientBuilder;
import org.springframework.vault.client.WebClientCustomizer;
import org.springframework.vault.client.WebClientFactory;
import org.springframework.vault.core.ReactiveVaultTemplate;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Base class for Spring Vault configuration using JavaConfig for a reactive
 * infrastructure.
 * <p>
 * Reactive Vault support creates a {@link ReactiveSessionManager} (for the session token)
 * from the configured {@link #clientAuthentication()} via {@link #vaultTokenSupplier()}.
 * The authentication object must implement {@link AuthenticationStepsFactory} exposing
 * {@link org.springframework.vault.authentication.AuthenticationSteps} to obtain
 * authentication using reactive infrastructure.
 * <p>
 * This configuration class provides configuration for imperative and reactive usage.
 * Using this configuration creates an imperative {@link SessionManager} adapter by
 * wrapping {@link #reactiveSessionManager()}.
 * <p>
 * Subclasses may override methods to customize configuration.
 *
 * @author Mark Paluch
 * @since 2.0
 */
@Configuration(proxyBeanMethods = false)
public abstract class AbstractReactiveVaultConfiguration extends AbstractVaultConfiguration {

	/**
	 * @return a {@link ReactiveVaultEndpointProvider} returning the value of
	 * {@link #vaultEndpointProvider()}.
	 *
	 * @see #vaultEndpoint()
	 * @see #vaultEndpointProvider()
	 * @since 2.3
	 */
	public ReactiveVaultEndpointProvider reactiveVaultEndpointProvider() {
		return ReactiveVaultClients.wrap(vaultEndpointProvider());
	}

	/**
	 * Create a {@link WebClientBuilder} initialized with {@link VaultEndpointProvider}
	 * and {@link ClientHttpConnector}. May be overridden by subclasses.
	 * @return the {@link WebClientBuilder}.
	 * @see #reactiveVaultEndpointProvider()
	 * @see #clientHttpConnector()
	 * @since 2.2
	 */
	protected WebClientBuilder webClientBuilder(VaultEndpointProvider endpointProvider,
			ClientHttpConnector httpConnector) {
		return webClientBuilder(ReactiveVaultClients.wrap(endpointProvider), httpConnector);
	}

	/**
	 * Create a {@link WebClientBuilder} initialized with {@link VaultEndpointProvider}
	 * and {@link ClientHttpConnector}. May be overridden by subclasses.
	 * @return the {@link WebClientBuilder}.
	 * @see #reactiveVaultEndpointProvider()
	 * @see #clientHttpConnector()
	 * @since 2.3
	 */
	protected WebClientBuilder webClientBuilder(ReactiveVaultEndpointProvider endpointProvider,
			ClientHttpConnector httpConnector) {

		ObjectProvider<WebClientCustomizer> customizers = getBeanFactory().getBeanProvider(WebClientCustomizer.class);

		WebClientBuilder builder = WebClientBuilder.builder().endpointProvider(endpointProvider)
				.httpConnector(httpConnector);

		builder.customizers(customizers.stream().toArray(WebClientCustomizer[]::new));

		return builder;
	}

	/**
	 * Create a {@link WebClientFactory} bean that is used to produce a {@link WebClient}.
	 * @return the {@link WebClientFactory}.
	 * @see #clientHttpConnector()
	 * @since 2.3
	 */
	@Bean
	public WebClientFactory webClientFactory() {

		ClientHttpConnector httpConnector = clientHttpConnector();

		return new DefaultWebClientFactory(httpConnector, clientHttpConnector -> {
			return webClientBuilder(reactiveVaultEndpointProvider(), clientHttpConnector);
		});
	}

	/**
	 * Create a {@link ReactiveVaultTemplate}.
	 * @return the {@link ReactiveVaultTemplate}.
	 * @see #vaultEndpoint()
	 * @see #reactiveVaultEndpointProvider()
	 * @see #clientHttpConnector()
	 * @see #reactiveSessionManager()
	 */
	@Bean
	public ReactiveVaultTemplate reactiveVaultTemplate() {

		return new ReactiveVaultTemplate(webClientBuilder(reactiveVaultEndpointProvider(), clientHttpConnector()),
				getReactiveSessionManager());
	}

	/**
	 * Construct a session manager adapter wrapping {@link #reactiveSessionManager()} and
	 * exposing imperative {@link SessionManager} on top of a reactive API.
	 * @return the {@link SessionManager} adapter.
	 */
	@Bean
	@Override
	public SessionManager sessionManager() {
		return new ReactiveSessionManagerAdapter(getReactiveSessionManager());
	}

	/**
	 * Construct a {@link ReactiveSessionManager} using {@link #vaultTokenSupplier()}.
	 * This {@link org.springframework.vault.authentication.ReactiveSessionManager} uses
	 * {@link #threadPoolTaskScheduler()}.
	 * @return the {@link VaultTokenSupplier} for Vault session token management.
	 * @see VaultTokenSupplier
	 * @see #clientAuthentication()
	 */
	@Bean
	public ReactiveSessionManager reactiveSessionManager() {

		WebClient webClient = getWebClientFactory().create();

		return new ReactiveLifecycleAwareSessionManager(vaultTokenSupplier(), getVaultThreadPoolTaskScheduler(),
				webClient);
	}

	/**
	 * Construct a {@link VaultTokenSupplier} using {@link #clientAuthentication()}.
	 * @return the {@link VaultTokenSupplier} for Vault session token management.
	 * @see VaultTokenSupplier
	 * @see #clientAuthentication()
	 */
	protected VaultTokenSupplier vaultTokenSupplier() {

		ClientAuthentication clientAuthentication = clientAuthentication();

		Assert.notNull(clientAuthentication, "ClientAuthentication must not be null");

		if (clientAuthentication instanceof TokenAuthentication) {

			TokenAuthentication authentication = (TokenAuthentication) clientAuthentication;
			return () -> Mono.just(authentication.login());
		}

		if (clientAuthentication instanceof AuthenticationStepsFactory) {

			AuthenticationStepsFactory factory = (AuthenticationStepsFactory) clientAuthentication;

			WebClient webClient = getWebClientFactory().create();
			AuthenticationStepsOperator stepsOperator = new AuthenticationStepsOperator(
					factory.getAuthenticationSteps(), webClient);

			return CachingVaultTokenSupplier.of(stepsOperator);
		}

		throw new IllegalStateException(String.format(
				"Cannot construct VaultTokenSupplier from %s. "
						+ "ClientAuthentication must implement AuthenticationStepsFactory or be TokenAuthentication",
				clientAuthentication));
	}

	/**
	 * Create a {@link ClientHttpConnector} configured with {@link ClientOptions} and
	 * {@link org.springframework.vault.support.SslConfiguration}.
	 * @return the {@link ClientHttpConnector} instance.
	 * @see #clientOptions()
	 * @see #sslConfiguration()
	 */
	protected ClientHttpConnector clientHttpConnector() {
		return ClientHttpConnectorFactory.create(clientOptions(), sslConfiguration());
	}

	/**
	 * Return the {@link WebClientFactory}.
	 * @return the {@link WebClientFactory} bean.
	 * @since 2.3
	 */
	protected WebClientFactory getWebClientFactory() {
		return getBeanFactory().getBean(WebClientFactory.class);
	}

	private ReactiveSessionManager getReactiveSessionManager() {
		return getBeanFactory().getBean("reactiveSessionManager", ReactiveSessionManager.class);
	}

	/**
	 * Simple {@link SessionManager} adapter using a {@link ReactiveSessionManager} to
	 * obtain tokens.
	 */
	static class ReactiveSessionManagerAdapter implements SessionManager {

		private final ReactiveSessionManager sessionManager;

		public ReactiveSessionManagerAdapter(ReactiveSessionManager sessionManager) {
			this.sessionManager = sessionManager;
		}

		@Override
		public VaultToken getSessionToken() {
			return this.sessionManager.getSessionToken().block(Duration.ofSeconds(30));
		}

	}

}
