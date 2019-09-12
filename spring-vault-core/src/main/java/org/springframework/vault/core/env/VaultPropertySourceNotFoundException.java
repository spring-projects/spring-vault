/*
 * Copyright 2019 the original author or authors.
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
package org.springframework.vault.core.env;

import org.springframework.vault.VaultException;
import org.springframework.vault.annotation.VaultPropertySource;

/**
 * Exception throws when a {@code VaultPropertySource} could not load its properties.
 *
 * @author Mark Paluch
 * @since 2.2
 * @see VaultPropertySource#ignoreSecretNotFound()
 */
public class VaultPropertySourceNotFoundException extends VaultException {

	/**
	 * Create a {@code VaultPropertySourceNotFoundException} with the specified detail
	 * message.
	 *
	 * @param msg the detail message.
	 */
	public VaultPropertySourceNotFoundException(String msg) {
		super(msg);
	}

	/**
	 * Create a {@code VaultPropertySourceNotFoundException} with the specified detail
	 * message and nested exception.
	 *
	 * @param msg the detail message.
	 * @param cause the nested exception.
	 */
	public VaultPropertySourceNotFoundException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
