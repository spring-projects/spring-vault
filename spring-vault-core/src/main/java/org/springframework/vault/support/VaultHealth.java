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
package org.springframework.vault.support;

import org.springframework.lang.Nullable;

/**
 * Vault health state.
 *
 * @author Stuart Ingram
 * @author Bill Koch
 * @author Mark Paluch
 */
public interface VaultHealth {

	/**
	 * @return {@literal true} if the Vault instance is initialized, otherwise
	 * {@literal false}.
	 */
	boolean isInitialized();

	/**
	 * @return {@literal true} if the Vault instance is sealed, otherwise {@literal false}
	 * if the Vault instance is unsealed.
	 */
	boolean isSealed();

	/**
	 * @return {@literal true} if the Vault instance is in standby mode, otherwise
	 * {@literal false} if the Vault instance is active.
	 */
	boolean isStandby();

	/**
	 * @return the server time in seconds, UTC.
	 */
	int getServerTimeUtc();

	/**
	 * @return the Vault version.
	 */
	@Nullable
	String getVersion();
}
