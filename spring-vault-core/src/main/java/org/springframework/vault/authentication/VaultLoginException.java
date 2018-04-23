/*
 * Copyright 2018 the original author or authors.
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
package org.springframework.vault.authentication;

import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultResponses;
import org.springframework.web.client.RestClientResponseException;

/**
 * Exception thrown if Vault login fails. The root cause is typically attached as cause.
 *
 * @author Mark Paluch
 * @since 2.1
 */
public class VaultLoginException extends VaultException {

	/**
	 * Create a {@code VaultLoginException} with the specified detail message.
	 *
	 * @param msg the detail message.
	 */
	public VaultLoginException(String msg) {
		super(msg);
	}

	/**
	 * Create a {@code VaultLoginException} with the specified detail message and nested
	 * exception.
	 *
	 * @param msg the detail message.
	 * @param cause the nested exception.
	 */
	public VaultLoginException(String msg, Throwable cause) {
		super(msg, cause);
	}

	/**
	 * Create a {@link VaultLoginException} given {@code authMethod} and a
	 * {@link Throwable cause}.
	 *
	 * @param authMethod must not be {@literal null}.
	 * @param cause must not be {@literal null}.
	 * @return the {@link VaultLoginException}.
	 */
	public static VaultLoginException create(String authMethod, Throwable cause) {

		if (cause instanceof RestClientResponseException) {

			String response = ((RestClientResponseException) cause)
					.getResponseBodyAsString();
			return new VaultLoginException(String.format("Cannot login using %s: %s",
					authMethod, VaultResponses.getError(response)), cause);
		}

		return new VaultLoginException(String.format("Cannot login using %s", cause));
	}
}
