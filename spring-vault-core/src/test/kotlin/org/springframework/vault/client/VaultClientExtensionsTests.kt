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

package org.springframework.vault.client

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import org.springframework.vault.core.ReactiveVaultOperationsExtensionsTests.Person

/**
 * Unit tests for [VaultClient] extensions.
 *
 * @author Mark Paluch
 */
class VaultClientExtensionsTests {

	val request = mockk<VaultClient.RequestBodySpec>(relaxed = true)

	val response = mockk<VaultClient.ResponseSpec>(relaxed = true)

	@Test
	fun `RequestBodySpec#bodyWithType() with reified type parameter extension should call its Java counterpart`() {
		val body = Person()
		request.bodyWithType<Person>(body)
		verify { request.body(body, object : ParameterizedTypeReference<Person>() {}) }
	}

	@Test
	fun `ResponseSpec#body() with reified type parameter extension should call its Java counterpart`() {
		response.body<Person>()
		verify { response.body(object : ParameterizedTypeReference<Person>() {}) }
	}

	@Test
	fun `ResponseSpec#requiredBody() with reified type parameter extension should call its Java counterpart`() {
		response.requiredBody<Person>()
		verify { response.requiredBody(object : ParameterizedTypeReference<Person>() {}) }
	}

	@Test
	fun `ResponseSpec#toEntity() with reified type parameter extension should call its Java counterpart`() {
		response.toEntity<Person>()
		verify { response.toEntity(object : ParameterizedTypeReference<Person>() {}) }
	}

}
