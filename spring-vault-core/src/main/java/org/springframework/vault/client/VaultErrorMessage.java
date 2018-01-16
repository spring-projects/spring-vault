/*
 * Copyright 2016-2018 the original author or authors.
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

package org.springframework.vault.client;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility to obtain a Vault error message.
 *
 * @author Mark Paluch
 */
class VaultErrorMessage {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/**
	 * Obtain the error message from a JSON response.
	 *
	 * @param json
	 * @return
	 */
	static String getError(String json) {

		if (json.contains("\"errors\":")) {

			try {
				Map<String, Object> map = OBJECT_MAPPER.readValue(json.getBytes(),
						Map.class);
				if (map.containsKey("errors")) {

					Collection<String> errors = (Collection<String>) map.get("errors");
					if (errors.size() == 1) {
						return errors.iterator().next();
					}
					return errors.toString();
				}

			}
			catch (IOException o_O) {
				// ignore
			}
		}
		return json;
	}
}
