/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.vault.core;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.StringUtils;
import org.springframework.vault.client.VaultException;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * @author Mark Paluch
 */
public abstract class VaultResponses {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public static VaultException buildException(HttpStatusCodeException e, String path) {

		String message = VaultResponses.getError(e.getResponseBodyAsString());

		if (StringUtils.hasText(message)) {
			return new VaultException(String.format("Status %s %s: %s",
					e.getStatusCode(), path, message));
		}

		return new VaultException(String.format("Status %s %s", e.getStatusCode(), path));
	}

	public static VaultException buildException(HttpStatusCodeException e) {

		String message = VaultResponses.getError(e.getResponseBodyAsString());

		if (StringUtils.hasText(message)) {
			return new VaultException(String.format("Status %s: %s", e.getStatusCode(),
					message));
		}

		return new VaultException(String.format("Status %s", e.getStatusCode()));
	}

	public static <T> ParameterizedTypeReference<VaultResponseSupport<T>> getTypeReference(
			final Class<T> responseType) {
		final Type supportType = new ParameterizedType() {

			@Override
			public Type[] getActualTypeArguments() {
				return new Type[] { responseType };
			}

			@Override
			public Type getRawType() {
				return VaultResponseSupport.class;
			}

			@Override
			public Type getOwnerType() {
				return VaultResponseSupport.class;
			}
		};

		return new ParameterizedTypeReference<VaultResponseSupport<T>>() {
			@Override
			public Type getType() {
				return supportType;
			}
		};
	}

	/**
	 * Obtain the error message from a JSON response.
	 * 
	 * @param json
	 * @return
	 */
	public static String getError(String json) {

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
