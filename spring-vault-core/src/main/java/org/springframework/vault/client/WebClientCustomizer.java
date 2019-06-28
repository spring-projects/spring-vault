/*
 * Copyright 2019 the original author or authors.
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

import org.springframework.web.reactive.function.client.WebClient;

/**
 * Callback interface that can be used to customize a {@link WebClient.Builder}.
 *
 * @author Mark Paluch
 * @since 2.2
 * @see WebClientBuilder
 */
@FunctionalInterface
public interface WebClientCustomizer {

	/**
	 * Callback to customize a {@link WebClient.Builder} instance.
	 *
	 * @param webClientBuilder the client builder to customize.
	 */
	void customize(WebClient.Builder webClientBuilder);
}
