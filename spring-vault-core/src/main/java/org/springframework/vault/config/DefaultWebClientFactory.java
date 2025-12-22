/*
 * Copyright 2020-2025 the original author or authors.
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

import java.util.function.Consumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.vault.client.WebClientBuilder;
import org.springframework.vault.client.WebClientFactory;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Default implementation of {@link WebClientFactory}.
 *
 * @author Mark Paluch
 * @since 2.3
 */
class DefaultWebClientFactory implements WebClientFactory {

	private final ClientHttpConnector connector;

	private final Function<ClientHttpConnector, WebClientBuilder> builderFunction;


	DefaultWebClientFactory(ClientHttpConnector connector,
			Function<ClientHttpConnector, WebClientBuilder> builderFunction) {
		this.connector = connector;
		this.builderFunction = builderFunction;
	}


	@Override
	public WebClient create(@Nullable Consumer<WebClientBuilder> customizer) {
		WebClientBuilder builder = this.builderFunction.apply(this.connector);
		if (customizer != null) {
			customizer.accept(builder);
		}
		return builder.build();
	}

}
