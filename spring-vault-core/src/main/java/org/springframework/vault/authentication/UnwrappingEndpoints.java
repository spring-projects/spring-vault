/*
 * Copyright 2019-2020 the original author or authors.
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

import org.springframework.http.HttpMethod;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;

/**
 * Version-specific endpoint implementations for response unwrapping. Uses either legacy
 * cubbyhole or {@code sys/wrapping} endpoints.
 *
 * @author Mark Paluch
 * @since 2.2
 */
public enum UnwrappingEndpoints {

	/**
	 * Legacy cubbyhole endpoints prior to Vault 0.6.2 ({@literal cubbyhole/response}).
	 */
	Cubbyhole {

		@Override
		String getPath() {
			return "cubbyhole/response";
		}

		@Override
		VaultResponse unwrap(VaultResponse vaultResponse) {
			return VaultResponses.unwrap((String) vaultResponse.getRequiredData().get("response"), VaultResponse.class);
		}

		@Override
		HttpMethod getUnwrapRequestMethod() {
			return HttpMethod.GET;
		}
	},

	/**
	 * Sys/wrapping endpoints for Vault 0.6.2 and higher
	 * ({@literal /sys/wrapping/unwrap}).
	 */
	SysWrapping {

		@Override
		String getPath() {
			return "sys/wrapping/unwrap";
		}

		@Override
		VaultResponse unwrap(VaultResponse vaultResponse) {
			return vaultResponse;
		}

		@Override
		HttpMethod getUnwrapRequestMethod() {
			return HttpMethod.POST;
		}
	};

	/**
	 * Retrieve the path of the unwrapping endpoint.
	 * @return the unwrapping endpoint path.
	 */
	abstract String getPath();

	/**
	 * Unwrap the response data from {@link VaultResponses}.
	 * @param response the raw response entity.
	 * @return unwrapped {@link VaultResponse}.
	 */
	abstract VaultResponse unwrap(VaultResponse response);

	/**
	 * Unwrapping request {@link HttpMethod method}.
	 * @return the unwrapping request {@link HttpMethod method}.
	 */
	abstract HttpMethod getUnwrapRequestMethod();

}
