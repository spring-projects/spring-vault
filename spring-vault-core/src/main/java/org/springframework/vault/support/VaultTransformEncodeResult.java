/*
 * Copyright 2020-2022 the original author or authors.
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

import org.springframework.lang.Nullable;
import org.springframework.vault.VaultException;

/**
 * Holds the response from encryption operation and provides methods to access the result.
 *
 * @author Lauren Voswinkel
 * @since 2.3
 */
public class VaultTransformEncodeResult extends AbstractResult<TransformCiphertext> {

	private final @Nullable TransformCiphertext cipherText;

	/**
	 * Create {@link VaultTransformEncodeResult} for a successfully encrypted
	 * {@link TransformCiphertext} .
	 * @param cipherText must not be {@literal null}.
	 */
	public VaultTransformEncodeResult(TransformCiphertext cipherText) {
		this.cipherText = cipherText;
	}

	/**
	 * Create {@link VaultTransformEncodeResult} for an error during encryption.
	 * @param exception must not be {@literal null}.
	 */
	public VaultTransformEncodeResult(VaultException exception) {

		super(exception);
		this.cipherText = null;
	}

	@Nullable
	@Override
	protected TransformCiphertext get0() {
		return this.cipherText;
	}

	/**
	 * Return the result as {@link String} or throw a {@link VaultException} if the
	 * operation completed with an error. Use {@link #isSuccessful()} to verify the
	 * success status of this result without throwing an exception.
	 * @return the result value.
	 * @throws VaultException if the operation completed with an error.
	 */
	@Nullable
	public String getAsString() {

		TransformCiphertext ciphertext = get();
		return ciphertext == null ? null : ciphertext.getCiphertext();
	}

}
