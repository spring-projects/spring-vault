/*
 * Copyright 2017-2018 the original author or authors.
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
package org.springframework.vault.support;

import org.springframework.vault.VaultException;

/**
 * Holds the response from encryption operation and provides methods to access the result.
 *
 * @author Praveendra Singh
 * @author Mark Paluch
 * @since 1.1
 */
public class VaultEncryptionResult extends AbstractResult<Ciphertext> {

	private final Ciphertext cipherText;

	/**
	 * Create {@link VaultEncryptionResult} for a successfully encrypted
	 * {@link Ciphertext} .
	 *
	 * @param cipherText must not be {@literal null}.
	 */
	public VaultEncryptionResult(Ciphertext cipherText) {
		this.cipherText = cipherText;
	}

	/**
	 * Create {@link VaultEncryptionResult} for an error during encryption.
	 *
	 * @param exception must not be {@literal null}.
	 */
	public VaultEncryptionResult(VaultException exception) {

		super(exception);
		this.cipherText = null;
	}

	@Override
	protected Ciphertext get0() {
		return cipherText;
	}
}
