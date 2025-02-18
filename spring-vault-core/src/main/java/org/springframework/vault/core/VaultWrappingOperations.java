/*
 * Copyright 2018-2025 the original author or authors.
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

import java.time.Duration;

import org.springframework.lang.Nullable;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.WrappedMetadata;

/**
 * Interface that specifies wrapping-related operations.
 *
 * @author Mark Paluch
 * @since 2.1
 */
public interface VaultWrappingOperations {

	/**
	 * Looks up {@link WrappedMetadata metadata} for the given token containing a wrapped
	 * response.
	 * @param token must not be {@literal null}.
	 * @return the {@link WrappedMetadata} the {@code token} or {@literal null} if the
	 * token was invalid or expired.
	 */
	@Nullable
	WrappedMetadata lookup(VaultToken token);

	/**
	 * Read a wrapped secret.
	 * @param token must not be {@literal null}.
	 * @return the data or {@literal null} if the token was invalid or expired.
	 */
	@Nullable
	VaultResponse read(VaultToken token);

	/**
	 * Read a wrapped secret of type {@link Class responseType}.
	 * @param token must not be {@literal null}.
	 * @param responseType must not be {@literal null}.
	 * @return the data or {@literal null} if the token was invalid or expired.
	 */
	@Nullable
	<T> VaultResponseSupport<T> read(VaultToken token, Class<T> responseType);

	/**
	 * Rewraps a response-wrapped token. The new token will use the same creation TTL as
	 * the original token and contain the same response. The old token will be
	 * invalidated. This can be used for long-term storage of a secret in a
	 * response-wrapped token when rotation is a requirement. Rewrapping with an invalid
	 * token throws {@link VaultException}.
	 * @param token must not be {@literal null}.
	 * @return the {@link WrappedMetadata} for this wrapping operation.
	 */
	WrappedMetadata rewrap(VaultToken token);

	/**
	 * Wraps the given user-supplied data inside a response-wrapped token.
	 * @param body must not be {@literal null}.
	 * @param ttl must not be {@literal null}.
	 * @return the {@link WrappedMetadata} for this wrapping operation.
	 */
	WrappedMetadata wrap(Object body, Duration ttl);

}
