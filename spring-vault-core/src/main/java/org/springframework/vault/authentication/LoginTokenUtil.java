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
package org.springframework.vault.authentication;

import java.util.Map;

import lombok.experimental.UtilityClass;

/**
 * Utility class for {@link LoginToken}.
 * 
 * @author Mark Paluch
 */
@UtilityClass
class LoginTokenUtil {

	/**
	 * Construct a {@link LoginToken} from an auth response.
	 * 
	 * @param auth {@link Map} holding a login response.
	 * @return the {@link LoginToken}
	 */
	static LoginToken from(Map<String, Object> auth) {

		String token = (String) auth.get("client_token");
		Boolean renewable = (Boolean) auth.get("renewable");
		Number leaseDuration = (Number) auth.get("lease_duration");

		if (renewable != null && renewable) {
			return LoginToken.renewable(token, leaseDuration.longValue());
		}

		if (leaseDuration != null) {
			return LoginToken.of(token, leaseDuration.longValue());
		}

		return LoginToken.of(token);
	}
}
