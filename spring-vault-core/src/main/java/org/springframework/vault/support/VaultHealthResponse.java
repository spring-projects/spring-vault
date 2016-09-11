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
 * Value object to bind HTTP API responses for sys/health.
 *
 * @author Stuart Ingram
 * @author Bill Koch
 */
public class VaultHealthResponse {

	private boolean initialized;

	private boolean sealed;

	private boolean standby;

	@JsonProperty("server_time_utc") private int serverTimeUtc;

	public boolean isInitialized() {
		return initialized;
	}

	public void setInitialized(boolean initialized) {
		this.initialized = initialized;
	}

	public boolean isSealed() {
		return sealed;
	}

	public void setSealed(boolean sealed) {
		this.sealed = sealed;
	}

	public boolean isStandby() {
		return standby;
	}

	public void setStandby(boolean standby) {
		this.standby = standby;
	}

	public int getServerTimeUtc() {
		return serverTimeUtc;
	}

	public void setServerTimeUtc(int serverTimeUtc) {
		this.serverTimeUtc = serverTimeUtc;
	}
}
