/*
 * Copyright 2018-2019 the original author or authors.
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

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.springframework.vault.authentication.LifecycleAwareSessionManagerSupport.FixedTimeoutRefreshTrigger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LifecycleAwareSessionManagerSupport} .
 *
 * @author Mark Paluch
 */
class LifecycleAwareSessionManagerSupportUnitTests {

	@Test
	void shouldScheduleNextExecutionTimeCorrectly() {

		FixedTimeoutRefreshTrigger trigger = new FixedTimeoutRefreshTrigger(5,
				TimeUnit.SECONDS);

		Date nextExecutionTime = trigger.nextExecutionTime(LoginToken.of(
				"foo".toCharArray(), Duration.ofMinutes(1)));
		assertThat(nextExecutionTime).isBetween(
				new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(52)),
				new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(56)));
	}

	@Test
	void shouldScheduleNextExecutionIfValidityLessThanTimeout() {

		FixedTimeoutRefreshTrigger trigger = new FixedTimeoutRefreshTrigger(5,
				TimeUnit.SECONDS);

		Date nextExecutionTime = trigger.nextExecutionTime(LoginToken.of(
				"foo".toCharArray(), Duration.ofSeconds(2)));
		assertThat(nextExecutionTime).isBetween(
				new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(0)),
				new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(2)));
	}
}
