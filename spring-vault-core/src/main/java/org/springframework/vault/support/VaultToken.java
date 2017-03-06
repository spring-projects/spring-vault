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

import lombok.EqualsAndHashCode;
import lombok.ToString;

import org.springframework.util.Assert;

/**
 * Value object for a Vault token.
 *
 * @author Mark Paluch
 */
@EqualsAndHashCode
@ToString(exclude = "token")
public class VaultToken {

	private final String token;

	protected VaultToken(String token) {

		Assert.hasText(token, "Token must not be empty");

		this.token = token;
	}

	/**
	 * Create a new {@link VaultToken}.
	 *
	 * @param token must not be empty or {@literal null}.
	 * @return the created {@link VaultToken}
	 */
	public static VaultToken of(String token) {
		return new VaultToken(token);
	}

	/**
	 * @return the token value.
	 */
	public String getToken() {
		return token;
	}

}
