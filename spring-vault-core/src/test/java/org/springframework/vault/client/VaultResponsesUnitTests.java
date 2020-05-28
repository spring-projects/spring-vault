/*
 * Copyright 2018-2020 the original author or authors.
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
package org.springframework.vault.client;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.vault.VaultException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link VaultResponses}.
 *
 * @author Mark Paluch
 */
class VaultResponsesUnitTests {

	@Test
	void shouldBuildException() {

		HttpStatusCodeException cause = new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request");

		VaultException vaultException = VaultResponses.buildException(cause);
		assertThat(vaultException).hasMessageContaining("Status 400 Bad Request;").hasCause(cause);
	}

	@Test
	void shouldBuildExceptionWithErrorMessage() {

		HttpStatusCodeException cause = new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request",
				"{\"errors\":[\"some-error\"]}".getBytes(), StandardCharsets.US_ASCII);

		VaultException vaultException = VaultResponses.buildException(cause);
		assertThat(vaultException).hasMessageContaining("Status 400 Bad Request: some-error;").hasCause(cause);
	}

	@Test
	void shouldBuildExceptionWithPath() {

		HttpStatusCodeException cause = new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request");

		VaultException vaultException = VaultResponses.buildException(cause, "sys/path");
		assertThat(vaultException).hasMessageContaining("Status 400 Bad Request [sys/path];").hasCause(cause);
	}

	@Test
	void shouldBuildExceptionWithPathAndErrorMessage() {

		HttpStatusCodeException cause = new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request",
				"{\"errors\":[\"some-error\"]}".getBytes(), StandardCharsets.US_ASCII);

		VaultException vaultException = VaultResponses.buildException(cause, "sys/path");
		assertThat(vaultException).hasMessageContaining("Status 400 Bad Request [sys/path]: some-error;")
				.hasCause(cause);
	}

}
