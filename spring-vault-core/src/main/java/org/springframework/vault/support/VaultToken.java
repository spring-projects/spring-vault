/*
 * Copyright 2016 the original author or authors.
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

import org.springframework.util.Assert;

import lombok.EqualsAndHashCode;

/**
 * Value object for a Vault token.
 *
 * @author Mark Paluch
 */
@EqualsAndHashCode
public class VaultToken {

	private final String token;

	private final long leaseDuration;

	private VaultToken(String token, long leaseDuration) {
		this.token = token;
		this.leaseDuration = leaseDuration;
	}

	/**
	 * Creates a new {@link VaultToken}.
	 * 
	 * @param token must not be {@literal null}.
	 * @return the created {@link VaultToken}
	 */
	public static VaultToken of(String token) {
		return of(token, 0);
	}

	/**
	 * Creates a new {@link VaultToken} with a {@code leaseDuration}.
	 * 
	 * @param token must not be {@literal null}.
	 * @param leaseDuration the lease duration.
	 * @return the created {@link VaultToken}
	 */
	public static VaultToken of(String token, long leaseDuration) {

		Assert.hasText(token, "Token must not be empty");

		return new VaultToken(token, leaseDuration);
	}

	/**
	 * @return the token value.
	 */
	public String getToken() {
		return token;
	}

	/**
	 * @return the lease duration. May be {@literal 0} if none.
	 */
	public long getLeaseDuration() {
		return leaseDuration;
	}

}
