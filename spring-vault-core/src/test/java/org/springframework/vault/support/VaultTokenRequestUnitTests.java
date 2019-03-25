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
package org.springframework.vault.support;

import java.time.Duration;
import java.util.Collections;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link VaultTokenRequest}.
 *
 * @author Mark Paluch
 */
public class VaultTokenRequestUnitTests {

	@Test
	public void shouldBuildEmptyRequest() {

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder().build();

		assertThat(tokenRequest.getMeta()).isEmpty();
		assertThat(tokenRequest.getPolicies()).isEmpty();
	}

	@Test
	public void shouldBuildRequestWithMeta() {

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder()
				.meta(Collections.singletonMap("key", "value")).build();

		assertThat(tokenRequest.getMeta()).containsEntry("key", "value");
	}

	@Test
	public void shouldBuildRequestWithPolicies() {

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder().withPolicy("foo")
				.build();

		assertThat(tokenRequest.getPolicies()).containsOnly("foo");
	}

	@Test
	public void shouldRequestWithDuration() {

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder()
				.ttl(Duration.ofSeconds(10)).explicitMaxTtl(Duration.ofSeconds(20))
				.build();

		assertThat(tokenRequest.getTtl()).isEqualTo("10s");
		assertThat(tokenRequest.getExplicitMaxTtl()).isEqualTo("20s");
	}
}
