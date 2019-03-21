/*
 * Copyright 2016-2018 the original author or authors.
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

/**
 * Value object to bind Vault HTTP Token API responses.
 *
 * @author Mark Paluch
 */
public class VaultTokenResponse extends VaultResponse {

	/**
	 * Return a {@link VaultToken} from the {@link VaultResponse}.
	 *
	 * @return the {@link VaultToken}.
	 */
	public VaultToken getToken() {
		return VaultToken.of((String) getRequiredAuth().get("client_token"));
	}
}
