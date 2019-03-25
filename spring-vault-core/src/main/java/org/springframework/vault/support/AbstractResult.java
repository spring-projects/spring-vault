/*
 * Copyright 2017-2019 the original author or authors.
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
 * Supporting class for computation results allowing introspection of the result value.
 * Accessing the result with {@link #get()} returns either the result or throws a
 * {@link VaultException} if the execution completed with an error.
 *
 * @author Mark Paluch
 * @since 1.1
 */
public abstract class AbstractResult<V> {

	private final @Nullable VaultException exception;

	/**
	 * Create a {@link AbstractResult} completed without an {@link VaultException}.
	 */
	protected AbstractResult() {
		exception = null;
	}

	/**
	 * Create a {@link AbstractResult} completed with an {@link VaultException}.
	 *
	 * @param exception must not be {@literal null}.
	 */
	protected AbstractResult(VaultException exception) {

		Assert.notNull(exception, "VaultException must not be null");

		this.exception = exception;
	}

	/**
	 * Returns {@literal true} if and only if the batch operation was completed
	 * successfully. Use {@link #getCause()} to obtain the actual exception if the
	 * operation completed with an error.
	 *
	 * @return {@literal true} if the batch operation was completed successfully.
	 */
	public boolean isSuccessful() {
		return exception == null;
	}

	/**
	 * Returns the cause of the failed operation if the operation completed with an error.
	 *
	 * @return the cause of the failure or {@literal null} if succeeded.
	 */
	@Nullable
	public Exception getCause() {
		return exception;
	}

	/**
	 * Return the result or throw a {@link VaultException} if the operation completed with
	 * an error. Use {@link #isSuccessful()} to verify the success status of this result
	 * without throwing an exception.
	 *
	 * @return the result value.
	 * @throws VaultException if the operation completed with an error.
	 */
	@Nullable
	public V get() {

		if (isSuccessful()) {
			return get0();
		}

		throw new VaultException(exception.getMessage());
	}

	/**
	 * @return the actual result if this result completed successfully.
	 */
	@Nullable
	protected abstract V get0();
}
