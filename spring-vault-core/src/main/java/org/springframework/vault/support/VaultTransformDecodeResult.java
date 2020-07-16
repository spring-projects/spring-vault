/*
 * Copyright 2017-2020 the original author or authors.
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
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;

/**
 * Holds the response from decryption operation and provides methods to access the result.
 *
 * @author Lauren Voswinkel
 */
public class VaultTransformDecodeResult extends AbstractResult<TransformPlaintext> {

	private final @Nullable TransformPlaintext plaintext;

	/**
	 * Create {@link VaultTransformDecodeResult} for a successfully decrypted {@link TransformPlaintext}
	 * .
	 * @param plaintext must not be {@literal null}.
	 */
	public VaultTransformDecodeResult(TransformPlaintext plaintext) {

		Assert.notNull(plaintext, "Plaintext must not be null");

		this.plaintext = plaintext;
	}

	/**
	 * Create {@link VaultTransformDecodeResult} for an error during decryption.
	 * @param exception must not be {@literal null}.
	 */
	public VaultTransformDecodeResult(VaultException exception) {

		super(exception);
		this.plaintext = null;
	}

	@Nullable
	@Override
	protected TransformPlaintext get0() {
		return this.plaintext;
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

		TransformPlaintext plaintext = get();
		return plaintext == null ? null : plaintext.asString();
	}

}
