/*
 * Copyright 2020 the original author or authors.
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

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DurationParser}.
 *
 * @author Mark Paluch
 */
class DurationParserUnitTests {

	@Test
	void shouldParseSimpleDuration() {

		assertThat(DurationParser.parseDuration("0s")).isEqualTo(Duration.ZERO);
		assertThat(DurationParser.parseDuration("0h")).isEqualTo(Duration.ZERO);
		assertThat(DurationParser.parseDuration("0m")).isEqualTo(Duration.ZERO);
		assertThat(DurationParser.parseDuration("1s")).isEqualTo(Duration.ofSeconds(1));
		assertThat(DurationParser.parseDuration("1h")).isEqualTo(Duration.ofHours(1));
	}

	@Test
	void shouldParseComplexDuration() {

		Duration duration = Duration.ofMinutes(30).plusHours(6).plusSeconds(30).plusMillis(100).plusNanos(1100);

		assertThat(DurationParser.parseDuration("6h30m30s100ms1us100ns")).isEqualTo(duration);
		assertThat(DurationParser.parseDuration("23430s100001100ns")).isEqualTo(duration);
	}

	@Test
	void shouldFormatComplexDuration() {

		Duration duration = Duration.ofMinutes(30).plusHours(6).plusSeconds(30).plusMillis(100).plusNanos(1100);

		String result = DurationParser.formatDuration(duration);

		assertThat(result).isEqualTo("23430s100001100ns");
	}

}
