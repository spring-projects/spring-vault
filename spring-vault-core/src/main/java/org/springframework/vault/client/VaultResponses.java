/*
 * Copyright 2017-2024 the original author or authors.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Utility methods to unwrap Vault responses and build {@link VaultException}.
 *
 * @author Mark Paluch
 */
public abstract class VaultResponses {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(
			OBJECT_MAPPER);

	/**
	 * Build a {@link VaultException} given {@link HttpStatusCodeException}.
	 * @param e must not be {@literal null}.
	 * @return the {@link VaultException}.
	 */
	public static VaultException buildException(HttpStatusCodeException e) {

		Assert.notNull(e, "HttpStatusCodeException must not be null");

		String message = VaultResponses.getError(e.getResponseBodyAsString());

		if (StringUtils.hasText(message)) {
			return new VaultException(
					String.format("Status %s %s: %s", renderStatus(e.getStatusCode()), e.getStatusText(), message), e);
		}

		return new VaultException(String.format("Status %s %s", renderStatus(e.getStatusCode()), e.getStatusText()), e);
	}

	/**
	 * Build a {@link VaultException} given {@link HttpStatusCodeException} and request
	 * {@code path}.
	 * @param e must not be {@literal null}.
	 * @param path must not be {@literal null}.
	 * @return the {@link VaultException}.
	 */
	public static VaultException buildException(HttpStatusCodeException e, String path) {

		Assert.notNull(e, "HttpStatusCodeException must not be null");

		String message = VaultResponses.getError(e.getResponseBodyAsString());

		if (StringUtils.hasText(message)) {
			return new VaultException(String.format("Status %s %s [%s]: %s", renderStatus(e.getStatusCode()),
					e.getStatusText(), path, message), e);
		}

		return new VaultException(
				String.format("Status %s %s [%s]", renderStatus(e.getStatusCode()), e.getStatusText(), path), e);
	}

	public static VaultException buildException(HttpStatusCode statusCode, String path, String message) {

		if (StringUtils.hasText(message)) {
			return new VaultException(String.format("Status %s [%s]: %s", renderStatus(statusCode), path, message));
		}

		return new VaultException(String.format("Status %s [%s]", renderStatus(statusCode), path));
	}

	/**
	 * Create a {@link ParameterizedTypeReference} for {@code responseType}.
	 * @param responseType must not be {@literal null}.
	 * @return the {@link ParameterizedTypeReference} for {@code responseType}.
	 */
	public static <T> ParameterizedTypeReference<VaultResponseSupport<T>> getTypeReference(
			final Class<T> responseType) {

		Assert.notNull(responseType, "Response type must not be null");

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
	 * @param json must not be {@literal null}.
	 * @return extracted error string.
	 */
	@SuppressWarnings("unchecked")
	public static String getError(String json) {

		Assert.notNull(json, "Error JSON must not be null");

		if (json.contains("\"errors\":")) {

			try {
				Map<String, Object> map = OBJECT_MAPPER.readValue(json.getBytes(), Map.class);
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

	/**
	 * Unwrap a wrapped response created by Vault Response Wrapping
	 * @param wrappedResponse the wrapped response , must not be empty or {@literal null}.
	 * @param responseType the type of the return value.
	 * @return the unwrapped response.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T unwrap(final String wrappedResponse, Class<T> responseType) {

		Assert.hasText(wrappedResponse, "Wrapped response must not be empty");

		try {
			return (T) converter.read(responseType, new HttpInputMessage() {
				@Override
				public InputStream getBody() throws IOException {
					return new ByteArrayInputStream(wrappedResponse.getBytes());
				}

				@Override
				public HttpHeaders getHeaders() {
					return new HttpHeaders();
				}
			});
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static int renderStatus(HttpStatusCode s) {
		return s.value();
	}

}
