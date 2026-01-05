/*
 * Copyright 2016-present the original author or authors.
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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link LoginToken}.
 *
 * @author Mark Paluch
 */
class LoginTokenUnitTests {

	@Test
	void shouldConstructLoginToken() {

		assertThat(LoginToken.of("token")).isInstanceOf(LoginToken.class);
		assertThat(LoginToken.of("token".toCharArray(), Duration.ofSeconds(1))).isInstanceOf(LoginToken.class);
		assertThat(LoginToken.renewable("token".toCharArray(), Duration.ofSeconds(1))).isInstanceOf(LoginToken.class);
	}

	@Test
	void toStringShouldPrintFields() {

		assertThat(LoginToken.of("token")).hasToString("LoginToken [renewable=false, leaseDuration=PT0S, type=null]");
		assertThat(LoginToken.of("token".toCharArray(), Duration.ofSeconds(1)))
				.hasToString("LoginToken [renewable=false, leaseDuration=PT1S, type=null]");
		assertThat(LoginToken.renewable("token".toCharArray(), Duration.ofSeconds(1)))
				.hasToString("LoginToken [renewable=true, leaseDuration=PT1S, type=null]");
		assertThat(LoginToken.builder().token("foo").type("service").build())
				.hasToString("LoginToken [renewable=false, leaseDuration=PT0S, type=service]");
	}

	@Test
	void shouldConstructTokenWithAccessor() {

		assertThat(LoginToken.of("token").getAccessor()).isNull();

		LoginToken loginToken = LoginToken.builder().token("token").accessor("acc").build();
		assertThat(loginToken.getToken()).isEqualTo("token");
		assertThat(loginToken.getAccessor()).isEqualTo("acc");
	}

	@Test
	void shouldConstructServiceToken() {

		assertThat(LoginToken.of("token").isServiceToken()).isTrue();

		LoginToken loginToken = LoginToken.builder().token("token").type("service").build();
		assertThat(loginToken.isServiceToken()).isTrue();
	}

	@Test
	void shouldConstructBatchToken() {

		assertThat(LoginToken.of("token").isBatchToken()).isFalse();

		LoginToken loginToken = LoginToken.builder().token("token").type("batch").build();
		assertThat(loginToken.isBatchToken()).isTrue();
	}

}
