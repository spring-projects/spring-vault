/*
 * Copyright 2016-2019 the original author or authors.
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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LoginToken}.
 *
 * @author Mark Paluch
 */
public class LoginTokenUnitTests {

	@Test
	public void shouldConstructLoginToken() {

		assertThat(LoginToken.of("token")).isInstanceOf(LoginToken.class);
		assertThat(LoginToken.of("token", 1)).isInstanceOf(LoginToken.class);
		assertThat(LoginToken.renewable("token", 1)).isInstanceOf(LoginToken.class);
	}

	@Test
	public void toStringShouldPrintFields() {

		assertThat(LoginToken.of("token").toString()).isEqualTo(
				"LoginToken(renewable=false, leaseDuration=PT0S)");
		assertThat(LoginToken.of("token", 1).toString()).isEqualTo(
				"LoginToken(renewable=false, leaseDuration=PT1S)");
		assertThat(LoginToken.renewable("token", 1).toString()).isEqualTo(
				"LoginToken(renewable=true, leaseDuration=PT1S)");
	}
}
