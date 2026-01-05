/*
 * Copyright 2020-present the original author or authors.
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

import org.springframework.vault.VaultException;

/**
 * An exception which is used in case that no secret is found from Vault server.
 *
 * @author Younghwan Jang
 * @author Mark Paluch
 * @since 2.3
 */
public class SecretNotFoundException extends VaultException {

	private final String path;


	/**
	 * Create a {@code SecretNotFoundException} with the specified detail message.
	 * @param msg the detail message.
	 * @param path the canonical data path.
	 */
	public SecretNotFoundException(String msg, String path) {
		super(msg);
		this.path = path;
	}

	/**
	 * Create a {@code SecretNotFoundException} with the specified detail message
	 * and nested exception.
	 * @param msg the detail message.
	 * @param cause the nested exception.
	 * @param path the canonical data path.
	 */
	public SecretNotFoundException(String msg, Throwable cause, String path) {
		super(msg, cause);
		this.path = path;
	}


	/**
	 * @return the path to the requested secret.
	 */
	public String getPath() {
		return this.path;
	}

}
