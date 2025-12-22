/*
 * Copyright 2019-2025 the original author or authors.
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

import org.springframework.web.client.RestClient;

/**
 * Callback interface that can be used to customize a
 * {@link RestClient.Builder}. Beans implementing this interface are applied to
 * {@link RestClientBuilder}.
 *
 * @author Mark Paluch
 * @since 4.0
 * @see RestClientBuilder#customizers(RestClientCustomizer...)
 */
@FunctionalInterface
public interface RestClientCustomizer {

	/**
	 * Callback to customize a {@link RestClient.Builder} instance.
	 * @param builder the template to customize.
	 */
	void customize(RestClient.Builder builder);

}
