/*
 * Copyright 2023-present the original author or authors.
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

import java.io.Serializable;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link LoginTokenUtil}.
 *
 * @author Mark Paluch
 */
class LoginTokenUtilUnitTests {

	@Test
	void shouldCreateServiceToken() {

		Map<String, ? extends Serializable> response = Map.of("type", "service", "ttl", 100, "accessor",
				"B6oixijqmeR4bsLOJH88Ska9");

		LoginToken loginToken = LoginToken.from("foo".toCharArray(), response);

		assertThat(loginToken.isServiceToken()).isTrue();
		assertThat(loginToken.getAccessor()).isEqualTo("B6oixijqmeR4bsLOJH88Ska9");
	}

	@Test
	void shouldCreateBatchToken() {

		Map<String, ? extends Serializable> response = Map.of("type", "batch", "ttl", 100, "accessor",
				"B6oixijqmeR4bsLOJH88Ska9");

		LoginToken loginToken = LoginToken.from("foo".toCharArray(), response);

		assertThat(loginToken.isBatchToken()).isTrue();
		assertThat(loginToken.getAccessor()).isEqualTo("B6oixijqmeR4bsLOJH88Ska9");
	}

}
