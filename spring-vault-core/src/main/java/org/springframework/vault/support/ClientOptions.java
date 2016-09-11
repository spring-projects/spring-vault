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

import java.util.concurrent.TimeUnit;

/**
 * Client options for Vault.
 * 
 * @author Mark Paluch
 */
public class ClientOptions {

	/**
	 * Connection timeout;
	 */
	private final int connectionTimeout;

	/**
	 * Read timeout;
	 */
	private final int readTimeout;

	/**
	 * Creates new {@link ClientOptions} with default timeouts of {@literal 5} {@link TimeUnit#SECONDS} connection timeout
	 * and {@literal 15} {@link TimeUnit#SECONDS} read timeout.
	 */
	public ClientOptions() {
		this((int) TimeUnit.SECONDS.toMillis(5), (int) TimeUnit.SECONDS.toMillis(15));
	}

	/**
	 * Creates new {@link ClientOptions}.
	 * 
	 * @param connectionTimeout connection timeout in {@link TimeUnit#MILLISECONDS}, must be greater {@literal 0}.
	 * @param readTimeout read timeout in {@link TimeUnit#MILLISECONDS}, must be greater {@literal 0}.
	 */
	public ClientOptions(int connectionTimeout, int readTimeout) {
		this.connectionTimeout = connectionTimeout;
		this.readTimeout = readTimeout;
	}

	/**
	 * @return the connection timeout in {@link TimeUnit#MILLISECONDS}.
	 */
	public int getConnectionTimeout() {
		return connectionTimeout;
	}

	/**
	 * @return the read timeout in {@link TimeUnit#MILLISECONDS}.
	 */
	public int getReadTimeout() {
		return readTimeout;
	}

}
