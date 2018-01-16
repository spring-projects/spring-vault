/*
 * Copyright 2017-2018 the original author or authors.
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
package org.springframework.vault.support;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;

/**
 * Holds the response from decryption operation and provides methods to access the result.
 *
 * @author Praveendra Singh
 * @author Mark Paluch
 * @since 1.1
 */
public class VaultDecryptionResult extends AbstractResult<Plaintext> {

	private final @Nullable Plaintext plaintext;

	/**
	 * Create {@link VaultDecryptionResult} for a successfully decrypted {@link Plaintext}
	 * .
	 *
	 * @param plaintext must not be {@literal null}.
	 */
	public VaultDecryptionResult(Plaintext plaintext) {

		Assert.notNull(plaintext, "Plaintext must not be null");

		this.plaintext = plaintext;
	}

	/**
	 * Create {@link VaultDecryptionResult} for an error during decryption.
	 *
	 * @param exception must not be {@literal null}.
	 */
	public VaultDecryptionResult(VaultException exception) {

		super(exception);
		this.plaintext = null;
	}

	@Nullable
	@Override
	protected Plaintext get0() {
		return plaintext;
	}

	/**
	 * Return the result as {@link String} or throw a {@link VaultException} if the
	 * operation completed with an error. Use {@link #isSuccessful()} to verify the
	 * success status of this result without throwing an exception.
	 *
	 * @return the result value.
	 * @throws VaultException if the operation completed with an error.
	 */
	@Nullable
	public String getAsString() {

		Plaintext plaintext = get();
		return plaintext == null ? null : plaintext.asString();
	}
}
