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
package org.springframework.vault.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Represents an Http Call to Vault that resulted in a failure (usually non 200/204) response.
 * Typical causes are a Vault server error, authorization/authentication failures, bad requests.
 *
 * @author Michael Bell
 */
@SuppressWarnings("serial")
public class VaultHttpException extends VaultRemoteException {
	private final HttpStatus httpStatus;

	/**
	 * Create a {@code VaultException} with the specified detail message.
	 *
	 * @param msg the detail message.
	 */
	public VaultHttpException(String msg, HttpStatus httpStatus) {
		super(msg);
		this.httpStatus = httpStatus;
	}

	/**
	 * Create a {@code VaultException} with the specified detail message and nested
	 * exception.
	 *
	 * @param msg the detail message.
	 * @param cause the nested exception.
	 */
	public VaultHttpException(String msg, Throwable cause, HttpStatus httpStatus) {
		super(msg, cause);
		this.httpStatus = httpStatus;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}
}
