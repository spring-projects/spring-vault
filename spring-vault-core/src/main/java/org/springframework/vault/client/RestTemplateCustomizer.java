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

import org.springframework.web.client.RestTemplate;

/**
 * Callback interface that can be used to customize a {@link RestTemplate}. Beans
 * implementing this interface are applied to
 * {@link org.springframework.vault.client.RestTemplateBuilder}.
 *
 * @author Mark Paluch
 * @since 2.2
 * @see org.springframework.vault.client.RestTemplateBuilder#customizers(RestTemplateCustomizer...)
 */
@FunctionalInterface
public interface RestTemplateCustomizer {

	/**
	 * Callback to customize a {@link RestTemplate} instance.
	 * @param restTemplate the template to customize.
	 */
	void customize(RestTemplate restTemplate);

}
