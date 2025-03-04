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
package org.springframework.vault.client;

import java.util.function.Consumer;

import org.springframework.web.client.RestTemplate;

/**
 * Factory interface that produces a {@link RestTemplate} object. Factory implementations
 * are expected to create a new {@link RestTemplate} object when calling
 * {@link #create()}.
 *
 * @author Mark Paluch
 * @since 2.3
 * @see RestTemplateBuilder
 * @see RestTemplate
 */
@FunctionalInterface
public interface RestTemplateFactory {

	/**
	 * Create a {@link RestTemplate} instance.
	 * @return a {@link RestTemplate} instance.
	 */
	default RestTemplate create() {
		return create(builder -> {
		});
	}

	/**
	 * Create a {@link RestTemplate} instance by applying {@code customizer} to the
	 * underlying {@link RestTemplateBuilder}.
	 * @param customizer builder customizer.
	 * @return a {@link RestTemplate} instance.
	 */
	RestTemplate create(Consumer<RestTemplateBuilder> customizer);

}
