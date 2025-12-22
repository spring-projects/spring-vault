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

package org.springframework.vault.authentication;

import java.time.Clock;
import java.time.Duration;

/**
 * Support class for Google Cloud IAM-based Authentication options.
 * <p>Mainly to support implementations within the framework.
 *
 * @author Mark Paluch
 * @since 2.3.2
 * @see GcpIamCredentialsAuthenticationOptions
 */
public abstract class GcpIamAuthenticationSupport {

	/**
	 * Path of the gcp authentication backend mount.
	 */
	private final String path;

	/**
	 * Name of the role against which the login is being attempted. If role is not
	 * specified, the friendly name (i.e., role name or username) of the IAM
	 * principal authenticated. If a matching role is not found, login fails.
	 */
	private final String role;

	/**
	 * JWT validity/expiration.
	 */
	private final Duration jwtValidity;

	/**
	 * {@link Clock} to calculate JWT expiration.
	 */
	private final Clock clock;


	protected GcpIamAuthenticationSupport(String path, String role, Duration jwtValidity, Clock clock) {
		this.path = path;
		this.role = role;
		this.jwtValidity = jwtValidity;
		this.clock = clock;
	}


	/**
	 * @return the path of the gcp authentication backend mount.
	 */
	public String getPath() {
		return this.path;
	}

	/**
	 * @return name of the role against which the login is being attempted.
	 */
	public String getRole() {
		return this.role;
	}

	/**
	 * @return {@link Duration} of the JWT to generate.
	 */
	public Duration getJwtValidity() {
		return this.jwtValidity;
	}

	/**
	 * @return {@link Clock} used to calculate epoch seconds until the JWT expires.
	 */
	public Clock getClock() {
		return this.clock;
	}

}
