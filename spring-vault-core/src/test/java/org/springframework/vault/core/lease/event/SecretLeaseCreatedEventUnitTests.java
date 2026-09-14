/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.vault.core.lease.event;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SecretLeaseCreatedEvent}.
 *
 * @author Burak KALAYCI
 */
class SecretLeaseCreatedEventUnitTests {

	@Test
	void shouldAcceptNullSecretValues() {

		Map<String, Object> secrets = new LinkedHashMap<>();
		secrets.put("access_key", "SOME_KEY");
		secrets.put("secret_key", "SOME_VALUE");
		secrets.put("session_token", null);

		SecretLeaseCreatedEvent event = new SecretLeaseCreatedEvent(RequestedSecret.renewable("aws/creds/my-role"),
				Lease.none(), secrets);

		assertThat(event.getSecrets()).containsEntry("access_key", "SOME_KEY")
			.containsEntry("secret_key", "SOME_VALUE")
			.containsEntry("session_token", null);
		assertThatExceptionOfType(UnsupportedOperationException.class)
			.isThrownBy(() -> event.getSecrets().put("other", "value"));
	}

	@Test
	void shouldAcceptNullSecretValuesOnRotation() {

		Map<String, Object> secrets = new LinkedHashMap<>();
		secrets.put("access_key", "SOME_KEY");
		secrets.put("session_token", null);

		SecretLeaseRotatedEvent event = new SecretLeaseRotatedEvent(RequestedSecret.renewable("aws/creds/my-role"),
				Lease.none(), Lease.fromTimeToLive(Duration.ofMinutes(5)), secrets);

		assertThat(event.getSecrets()).containsEntry("session_token", null);
	}

}
