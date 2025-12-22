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

import org.springframework.web.reactive.function.client.WebClient;

/**
 * Factory interface that produces a {@link WebClient} object. Factory
 * implementations are expected to create a new {@link WebClient} object when
 * calling {@link #create()}.
 *
 * @author Mark Paluch
 * @since 2.3
 * @see WebClientBuilder
 * @see WebClient
 */
@FunctionalInterface
public interface WebClientFactory {

	/**
	 * Create a {@link WebClient} instance.
	 * @return a {@link WebClient} instance.
	 */
	default WebClient create() {
		return create(builder -> {
		});
	}

	/**
	 * Create a {@link WebClient} instance by applying {@code customizer} to the
	 * underlying {@link WebClientBuilder}.
	 * @param customizer builder customizer.
	 * @return a {@link WebClient} instance.
	 */
	WebClient create(Consumer<WebClientBuilder> customizer);

}
