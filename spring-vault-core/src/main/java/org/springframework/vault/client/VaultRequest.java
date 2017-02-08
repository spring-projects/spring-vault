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
package org.springframework.vault.client;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/**
 * @author Mark Paluch
 */
public interface VaultRequest<T> {

	/**
	 * @return the HTTP method of this request..
	 */
	HttpMethod method();

	/**
	 * @return the request URI.
	 */
	URI url();

	/**
	 * @return the headers of this request.
	 */
	HttpHeaders headers();

	/**
	 * @return the {@link VaultRequestBody} of this request.
	 */
	VaultRequestBody<T> body();

	/**
	 * Defines a builder for a request.
	 */
	interface Builder {

		/**
		 * Add the given, single header value under the given name.
		 * @param headerName the header name, must not be {@literal null}.
		 * @param headerValues the header value(s), must not be {@literal null}.
		 * @return {@code this} builder.
		 * @see HttpHeaders#add(String, String)
		 */
		Builder header(String headerName, String... headerValues);

		/**
		 * Copy the given headers into the entity's headers map.
		 *
		 * @param headers the existing HttpHeaders to copy from, must not be
		 * {@literal null}.
		 * @return {@code this} builder.
		 */
		Builder headers(HttpHeaders headers);

		/**
		 * Builds the request entity with no body.
		 * 
		 * @return the request entity.
		 */
		VaultRequest<Void> build();

		/**
		 * Set the body of the request to the given {@link VaultRequestBody} and return
		 * it.
		 * 
		 * @param body the {@link VaultRequestBody} that writes to the request, must not
		 * be {@literal null}.
		 * @param <T> the type contained in the body
		 * @return the request entity.
		 */
		<T> VaultRequest<T> body(VaultRequestBody<T> body);
	}
}
