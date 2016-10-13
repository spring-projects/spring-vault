/*
 * Copyright 2016 the original author or authors.
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

import org.springframework.http.HttpStatus;

/**
 * Encapsulates the client response used in {@link VaultAccessor}. Consists of the body,
 * status code the location and a message. The {@code body} is empty for all
 * non-successful results. This class is immutable.
 *
 * @author Mark Paluch
 */
public class VaultResponseEntity<T> {

	private final T body;

	private final HttpStatus statusCode;

	private final URI uri;

	private final String message;

	protected VaultResponseEntity(T body, HttpStatus statusCode, URI uri, String message) {
		this.body = body;
		this.statusCode = statusCode;
		this.uri = uri;
		this.message = message;
	}

	/**
	 * @return {@literal true} if the request was completed successfully.
	 */
	public boolean isSuccessful() {
		return statusCode.is2xxSuccessful();
	}

	/**
	 * @return {@literal true} if the request returned a body.
	 */
	public boolean hasBody() {
		return body != null;
	}

	/**
	 * @return the body of this entity.
	 */
	public T getBody() {
		return body;
	}

	/**
	 * @return the {@link HttpStatus} of this entity.
	 */
	public HttpStatus getStatusCode() {
		return statusCode;
	}

	/**
	 * @return the request {@link URI} of this entity.
	 */
	public URI getUri() {
		return uri;
	}

	/**
	 * @return the message of this entity. {@literal null} for successful responses but
	 * provided usually when the response yielded an error.
	 */
	public String getMessage() {
		return message;
	}
}
