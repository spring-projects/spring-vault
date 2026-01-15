/*
 * Copyright 2025-present the original author or authors.
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

package org.springframework.vault.core.certificate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CertificateContainer}.
 *
 * @author Mark Paluch
 */
class CertificateContainerUnitTests {

	Clock clock = Clock.fixed(Instant.now(), ZoneId.of("Z"));

	@Test
	void shouldConsiderJitter() {

		Duration threshold = Duration.ofMinutes(10);
		Duration twiceTheThreshold = threshold.plus(threshold);
		Instant expiry = clock.instant().plus(twiceTheThreshold.plusSeconds(1));

		Duration renewal1 = CertificateContainer.getRenewalDelay(clock, expiry, threshold);
		Duration renewal2 = CertificateContainer.getRenewalDelay(clock, expiry, threshold);

		assertThat(renewal1).isGreaterThan(threshold).isLessThan(twiceTheThreshold);
		assertThat(renewal2).isGreaterThan(threshold).isLessThan(twiceTheThreshold);
		assertThat(renewal1).isNotEqualTo(renewal2);
		assertThat(renewal1).isNotEqualTo(threshold.plusSeconds(1));
	}

	@Test
	void shouldConsiderThresholdTime() {

		Duration threshold = Duration.ofMinutes(10);
		Instant expiry = clock.instant().plus(Duration.ofMinutes(12));

		Duration renewalSeconds = CertificateContainer.getRenewalDelay(clock, expiry, threshold);
		assertThat(renewalSeconds).isEqualTo(Duration.ofMinutes(2));
	}

	@Test
	void shouldReportInstantRenewalTime() {

		Duration threshold = Duration.ofMinutes(10);
		Instant expiry = clock.instant().plus(Duration.ofMinutes(8));

		Duration renewal = CertificateContainer.getRenewalDelay(clock, expiry, threshold);
		assertThat(renewal).isZero();
	}

}
