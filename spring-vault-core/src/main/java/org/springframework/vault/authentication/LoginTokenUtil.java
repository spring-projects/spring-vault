/*
 * Copyright 2016-2020 the original author or authors.
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

import java.time.Duration;
import java.util.Map;

import org.springframework.util.Assert;

/**
 * Utility class for {@link LoginToken}.
 *
 * @author Mark Paluch
 */
final class LoginTokenUtil {

	private LoginTokenUtil() {
		throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
	}

	/**
	 * Construct a {@link LoginToken} from an auth response.
	 * @param auth {@link Map} holding a login response.
	 * @return the {@link LoginToken}
	 */
	static LoginToken from(Map<String, Object> auth) {

		Assert.notNull(auth, "Authentication must not be null");

		String token = (String) auth.get("client_token");

		return from(token.toCharArray(), auth);
	}

	/**
	 * Construct a {@link LoginToken} from an auth response.
	 * @param auth {@link Map} holding a login response.
	 * @return the {@link LoginToken}
	 * @since 2.0
	 */
	static LoginToken from(char[] token, Map<String, Object> auth) {

		Assert.notNull(auth, "Authentication must not be null");

		Boolean renewable = (Boolean) auth.get("renewable");
		Number leaseDuration = (Number) auth.get("lease_duration");

		if (leaseDuration == null) {
			leaseDuration = (Number) auth.get("ttl");
		}

		if (renewable != null && renewable) {
			return LoginToken.renewable(token, Duration.ofSeconds(leaseDuration.longValue()));
		}

		if (leaseDuration != null) {
			return LoginToken.of(token, Duration.ofSeconds(leaseDuration.longValue()));
		}

		return LoginToken.of(token);
	}

}
