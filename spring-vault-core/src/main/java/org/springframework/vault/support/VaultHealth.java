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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Value object to bind HTTP API responses for sys/health. Instances of this class are immutable.
 *
 * @author Stuart Ingram
 * @author Bill Koch
 */
public class VaultHealth {

	/**
	 * Reports whether the Vault instance is initialized.
	 */
	private final boolean initialized;

	/**
	 * Reports whether the Vault instance is sealed.
	 */
	private final boolean sealed;

	/**
	 * Reports whether the Vault instance is in stand-by mode if running using High-Availability.
	 */
	private final boolean standby;

	/**
	 * The server time in seconds, UTC.
	 */
	private final int serverTimeUtc;

	private VaultHealth(@JsonProperty("initialized") boolean initialized, @JsonProperty("sealed") boolean sealed,
			@JsonProperty("standby") boolean standby, @JsonProperty("server_time_utc") int serverTimeUtc) {

		this.initialized = initialized;
		this.sealed = sealed;
		this.standby = standby;
		this.serverTimeUtc = serverTimeUtc;
	}

	/**
	 * @return {@literal true} if the Vault instance is initialized, otherwise {@literal false}.
	 */
	public boolean isInitialized() {
		return initialized;
	}

	/**
	 * @return {@literal true} if the Vault instance is sealed, otherwise {@literal false} if the Vault instance is
	 *         unsealed.
	 */
	public boolean isSealed() {
		return sealed;
	}

	/**
	 * @return {@literal true} if the Vault instance is in standby mode, otherwise {@literal false} if the Vault instance
	 *         is active.
	 */
	public boolean isStandby() {
		return standby;
	}

	/**
	 * @return the server time in seconds, UTC.
	 */
	public int getServerTimeUtc() {
		return serverTimeUtc;
	}
}
