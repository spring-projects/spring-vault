/*
 * Copyright 2017-present the original author or authors.
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

package org.springframework.vault.core;

import org.jspecify.annotations.Nullable;

import org.springframework.web.client.RestClient;

/**
 * A callback for executing arbitrary operations on {@link RestClient}.
 *
 * @author Mark Paluch
 * @since 4.0
 */
@FunctionalInterface
interface RestClientCallback<T extends @Nullable Object> {

	/**
	 * Callback method.
	 * @param client client to be used.
	 * @return a result object or null if none.
	 */
	T doWithRestClient(RestClient client);

}
