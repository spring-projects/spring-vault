/*
 * Copyright 2016-2022 the original author or authors.
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

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.util.Assert;

/**
 * Client options for Vault.
 *
 * @author Mark Paluch
 */
public class ClientOptions {

	/**
	 * Connection timeout;
	 */
	private final Duration connectionTimeout;

	/**
	 * Read timeout;
	 */
	private final Duration readTimeout;

	/**
	 * Create new {@link ClientOptions} with default timeouts of {@literal 5}
	 * {@link TimeUnit#SECONDS} connection timeout and {@literal 15}
	 * {@link TimeUnit#SECONDS} read timeout.
	 */
	public ClientOptions() {
		this(Duration.ofSeconds(5), Duration.ofSeconds(15));
	}

	/**
	 * Create new {@link ClientOptions}.
	 * @param connectionTimeout connection timeout in {@link TimeUnit#MILLISECONDS}, must
	 * not be negative.
	 * @param readTimeout read timeout in {@link TimeUnit#MILLISECONDS}, must not be
	 * negative.
	 * @deprecated since 2.0, use {@link #ClientOptions(Duration, Duration)} for time unit
	 * safety.
	 */
	@Deprecated
	public ClientOptions(int connectionTimeout, int readTimeout) {
		this(Duration.ofMillis(connectionTimeout), Duration.ofMillis(readTimeout));
	}

	/**
	 * Create new {@link ClientOptions}.
	 * @param connectionTimeout connection timeout, must not be {@literal null}.
	 * @param readTimeout read timeout in, must not be {@literal null}.
	 * @since 2.0
	 */
	public ClientOptions(Duration connectionTimeout, Duration readTimeout) {

		Assert.notNull(connectionTimeout, "Connection timeout must not be null");
		Assert.notNull(readTimeout, "Read timeout must not be null");

		this.connectionTimeout = connectionTimeout;
		this.readTimeout = readTimeout;
	}

	/**
	 * @return the connection timeout in {@link TimeUnit#MILLISECONDS}.
	 */
	public Duration getConnectionTimeout() {
		return this.connectionTimeout;
	}

	/**
	 * @return the read timeout in {@link TimeUnit#MILLISECONDS}.
	 */
	public Duration getReadTimeout() {
		return this.readTimeout;
	}

}
