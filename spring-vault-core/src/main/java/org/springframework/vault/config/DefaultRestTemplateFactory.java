/*
 * Copyright 2020-2024 the original author or authors.
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

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.RestTemplateFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Default {@link RestTemplateFactory} implementation.
 *
 * @author Mark Paluch
 */
class DefaultRestTemplateFactory implements RestTemplateFactory {

	private final ClientHttpRequestFactory requestFactory;

	private final Function<ClientHttpRequestFactory, RestTemplateBuilder> builderFunction;

	DefaultRestTemplateFactory(ClientHttpRequestFactory requestFactory,
			Function<ClientHttpRequestFactory, RestTemplateBuilder> builderFunction) {
		this.requestFactory = requestFactory;
		this.builderFunction = builderFunction;
	}

	@Override
	public RestTemplate create(@Nullable Consumer<RestTemplateBuilder> customizer) {

		RestTemplateBuilder builder = this.builderFunction.apply(this.requestFactory);

		if (customizer != null) {
			customizer.accept(builder);
		}

		return builder.build();
	}

}
