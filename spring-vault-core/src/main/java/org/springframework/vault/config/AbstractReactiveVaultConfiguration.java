/*
 * Copyright 2017 the original author or authors.
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

import reactor.core.publisher.Mono;

import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.util.Assert;
import org.springframework.vault.authentication.AuthenticationStepsFactory;
import org.springframework.vault.authentication.AuthenticationStepsOperator;
import org.springframework.vault.authentication.CachingVaultTokenSupplier;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.authentication.VaultTokenSupplier;
import org.springframework.vault.client.ReactiveVaultClients;
import org.springframework.vault.core.ReactiveVaultTemplate;
import org.springframework.vault.support.ClientOptions;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Base class for Spring Vault configuration using JavaConfig for a reactive
 * infrastructure.
 * <p>
 * Reactive Vault support creates a {@link VaultTokenSupplier} (for the session token)
 * from the configured {@link #clientAuthentication()}. The authentication object must
 * implement {@link AuthenticationStepsFactory} exposing
 * {@link org.springframework.vault.authentication.AuthenticationSteps} to obtain
 * authentication using reactive infrastructure.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public abstract class AbstractReactiveVaultConfiguration extends
		AbstractVaultConfiguration {

	/**
	 * Create a {@link ReactiveVaultTemplate}.
	 *
	 * @return the {@link ReactiveVaultTemplate}.
	 * @see #vaultEndpoint()
	 * @see #clientHttpConnector()
	 * @see #vaultTokenSupplier()
	 */
	@Bean
	public ReactiveVaultTemplate reactiveVaultTemplate() {
		return new ReactiveVaultTemplate(vaultEndpoint(), clientHttpConnector(),
				vaultTokenSupplier());
	}

	/**
	 * Construct a {@link VaultTokenSupplier} using {@link #clientAuthentication()}. This
	 * {@link SessionManager} uses {@link #threadPoolTaskScheduler()}.
	 *
	 * @return the {@link VaultTokenSupplier} for Vault session token management.
	 * @see VaultTokenSupplier
	 * @see #clientAuthentication()
	 */
	@Bean
	public VaultTokenSupplier vaultTokenSupplier() {

		ClientAuthentication clientAuthentication = clientAuthentication();

		Assert.notNull(clientAuthentication, "ClientAuthentication must not be null");

		if (clientAuthentication instanceof TokenAuthentication) {

			TokenAuthentication authentication = (TokenAuthentication) clientAuthentication;
			return () -> Mono.just(authentication.login());
		}

		if (clientAuthentication instanceof AuthenticationStepsFactory) {

			AuthenticationStepsFactory factory = (AuthenticationStepsFactory) clientAuthentication;

			WebClient webClient = ReactiveVaultClients.createWebClient(vaultEndpoint(),
					clientHttpConnector());
			AuthenticationStepsOperator stepsOperator = new AuthenticationStepsOperator(
					factory.getAuthenticationSteps(), webClient);

			return CachingVaultTokenSupplier.of(stepsOperator);
		}

		throw new IllegalStateException(
				String.format(
						"Cannot construct VaultTokenSupplier from %s. "
								+ "ClientAuthentication must implement AuthenticationStepsFactory or be TokenAuthentication",
						clientAuthentication));
	}

	/**
	 * Create a {@link ClientHttpConnector} configured with {@link ClientOptions} and
	 * {@link org.springframework.vault.support.SslConfiguration}.
	 *
	 * @return the {@link ClientHttpConnector} instance.
	 * @see #clientOptions()
	 * @see #sslConfiguration()
	 */
	public ClientHttpConnector clientHttpConnector() {
		return ClientHttpConnectorFactory.create(clientOptions(), sslConfiguration());
	}
}
