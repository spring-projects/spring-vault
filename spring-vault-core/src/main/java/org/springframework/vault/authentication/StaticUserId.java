/*
 * Copyright 2016-2021 the original author or authors.
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

package org.springframework.vault.authentication;

import org.springframework.util.Assert;

/**
 * A static UserId.
 *
 * @author Mark Paluch
 * @see AppIdUserIdMechanism
 */
public class StaticUserId implements AppIdUserIdMechanism {

	private final String userId;

	/**
	 * Create a new {@link StaticUserId} for a given {@code userId}.
	 * @param userId must not be empty or {@literal null}.
	 */
	public StaticUserId(String userId) {

		Assert.hasText(userId, "UserId must not be empty");
		this.userId = userId;
	}

	@Override
	public String createUserId() {
		return this.userId;
	}

}
