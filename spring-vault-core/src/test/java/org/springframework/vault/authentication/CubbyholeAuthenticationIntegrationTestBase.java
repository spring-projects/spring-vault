/*
 * Copyright 2017-2018 the original author or authors.
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

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.util.IntegrationTestSupport;

import static org.junit.Assume.assumeNotNull;

/**
 * Integration test base class for {@link CubbyholeAuthentication} tests.
 *
 * @author Mark Paluch
 */
public abstract class CubbyholeAuthenticationIntegrationTestBase extends
		IntegrationTestSupport {

	protected Map<String, String> prepareWrappedToken() {

		ResponseEntity<VaultResponse> response = prepare().getVaultOperations()
				.doWithSession(
						restOperations -> {

							HttpHeaders headers = new HttpHeaders();
							headers.add("X-Vault-Wrap-TTL", "10m");

							return restOperations.exchange("auth/token/create",
									HttpMethod.POST, new HttpEntity<Object>(headers),
									VaultResponse.class);
						});

		Map<String, String> wrapInfo = response.getBody().getWrapInfo();

		// Response Wrapping requires Vault 0.6.0+
		assumeNotNull(wrapInfo);
		return wrapInfo;
	}
}
