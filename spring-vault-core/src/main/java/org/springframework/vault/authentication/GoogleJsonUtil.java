/*
 * Copyright 2022-2025 the original author or authors.
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

package org.springframework.vault.authentication;

import com.google.api.client.json.JsonFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.util.ClassUtils;

/**
 * Utility to provide JSON-functionality for Google integrations.
 *
 * @author Mark Paluch
 * @since 2.4
 */
class GoogleJsonUtil {

	static final JsonFactory JSON_FACTORY;

	static final String JACKSON = "com.google.api.client.json.jackson2.JacksonFactory";

	static final String GSON = "com.google.api.client.json.gson.GsonFactory";

	static {
		try {
			if (ClassUtils.isPresent(JACKSON, null)) {
				JSON_FACTORY = instantiate(JACKSON);
			} else {
				JSON_FACTORY = instantiate(GSON);
			}
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(
					"No com.google.api.client.json.JsonFactory implementation available. Make sure to include either %s or %s on your classpath."
							.formatted(JACKSON, GSON),
					e);
		}
	}

	private static JsonFactory instantiate(String name) throws ClassNotFoundException {
		return (JsonFactory) BeanUtils.instantiateClass(ClassUtils.forName(name, null));
	}

}
