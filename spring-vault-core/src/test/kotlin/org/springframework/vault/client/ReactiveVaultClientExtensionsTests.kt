/*
 * Copyright 2025 the original author or authors.
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

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.util.function.Function
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.vault.core.ReactiveVaultOperationsExtensionsTests.Person
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Unit tests for [ReactiveVaultClient] extensions.
 *
 * @author Mark Paluch
 */
class ReactiveVaultClientExtensionsTests {

	val request = mockk<ReactiveVaultClient.RequestBodySpec>(relaxed = true)

	val response = mockk<ReactiveVaultClient.ResponseSpec>(relaxed = true)

	@Test
	fun `RequestBodySpec#bodyWithType() with reified type parameter extension should call its Java counterpart`() {
		val body = Person()
		request.bodyWithType<Person>(body)
		verify { request.body(body, object : ParameterizedTypeReference<Person>() {}) }
	}

	@Test
	fun `RequestBodySpec#bodyValueWithType() with reified type parameter extension should call its Java counterpart`() {
		val body = Person()
		request.bodyValueWithType<Person>(body)
		verify { request.bodyValue(body, object : ParameterizedTypeReference<Person>() {}) }
	}

	@Test
	fun `ResponseSpec#bodyToMono() with reified type parameter extension should call its Java counterpart`() {
		response.bodyToMono<Person>()
		verify { response.bodyToMono(object : ParameterizedTypeReference<Person>() {}) }
	}

	@Test
	fun `ResponseSpec#bodyToFlux() with reified type parameter extension should call its Java counterpart`() {
		response.bodyToFlux<Person>()
		verify { response.bodyToFlux(object : ParameterizedTypeReference<Person>() {}) }
	}

	@Test
	fun `ResponseSpec#toEntity() with reified type parameter extension should call its Java counterpart`() {
		response.toEntity<Person>()
		verify { response.toEntity(object : ParameterizedTypeReference<Person>() {}) }
	}

	@Test
	fun `ResponseSpec#toEntityList() with reified type parameter extension should call its Java counterpart`() {
		response.toEntityList<Person>()
		verify { response.toEntityList(object : ParameterizedTypeReference<Person>() {}) }
	}

	@Test
	fun `ResponseSpec#toEntityFlux() with reified type parameter extension should call its Java counterpart`() {
		response.toEntityFlux<Person>()
		verify { response.toEntityFlux(object : ParameterizedTypeReference<Person>() {}) }
	}

	@Test
	fun `awaitExchange with function parameter`() {
		val Person = mockk<Person>()
		every { request.exchangeToMono(any<Function<ClientResponse, Mono<Person>>>()) } returns Mono.just(Person)
		runBlocking {
			assertThat(request.awaitExchange { Person }).isEqualTo(Person)
		}
	}

	@Test
	fun `awaitExchange with coroutines context`() {
		val Person = mockk<Person>()
		val slot = slot<Function<ClientResponse, Mono<Person>>>()
		every { request.exchangeToMono(capture(slot)) } answers {
			slot.captured.apply(mockk<ClientResponse>())
		}
		runBlocking(PersonContextElement(Person)) {
			assertThat(request.awaitExchange { currentCoroutineContext()[PersonContextElement]!!.person }).isEqualTo(
				Person
			)
		}
	}

	@Test
	fun `awaitExchangeOrNull returning null`() {
		val Person = mockk<Person>()
		every { request.exchangeToMono(any<Function<ClientResponse, Mono<Person>>>()) } returns Mono.empty()
		runBlocking {
			assertThat(request.awaitExchangeOrNull { Person }).isEqualTo(null)
		}
	}

	@Test
	fun `awaitExchangeOrNull returning object`() {
		val Person = mockk<Person>()
		every { request.exchangeToMono(any<Function<ClientResponse, Mono<Person>>>()) } returns Mono.just(Person)
		runBlocking {
			assertThat(request.awaitExchangeOrNull { Person }).isEqualTo(Person)
		}
	}

	@Test
	fun `awaitExchangeOrNull with coroutines context`() {
		val Person = mockk<Person>()
		val slot = slot<Function<ClientResponse, Mono<Person>>>()
		every { request.exchangeToMono(capture(slot)) } answers {
			slot.captured.apply(mockk<ClientResponse>())
		}
		runBlocking(PersonContextElement(Person)) {
			assertThat(request.awaitExchangeOrNull { currentCoroutineContext()[PersonContextElement]!!.person }).isEqualTo(
				Person
			)
		}
	}

	@Test
	fun exchangeToFlow() {
		val Person = mockk<Person>()
		every { request.exchangeToFlux(any<Function<ClientResponse, Flux<Person>>>()) } returns Flux.just(
			Person,
			Person
		)
		runBlocking {
			assertThat(request.exchangeToFlow {
				flow {
					emit(Person)
					emit(Person)
				}
			}.toList()).isEqualTo(listOf(Person, Person))
		}
	}

	@Test
	fun awaitBody() {
		val spec = mockk<ReactiveVaultClient.ResponseSpec>()
		every { spec.bodyToMono<String>() } returns Mono.just("Person")
		runBlocking {
			assertThat(spec.awaitBody<String>()).isEqualTo("Person")
		}
	}

	@Test
	fun `awaitBody of type Unit`() {
		val spec = mockk<ReactiveVaultClient.ResponseSpec>()
		val entity = mockk<ResponseEntity<Void>>()
		every { spec.toBodilessEntity() } returns Mono.just(entity)
		runBlocking {
			assertThat(spec.awaitBody<Unit>()).isEqualTo(Unit)
		}
	}

	@Test
	fun awaitBodyOrNull() {
		val spec = mockk<ReactiveVaultClient.ResponseSpec>()
		every { spec.bodyToMono<String>() } returns Mono.just("Person")
		runBlocking {
			assertThat(spec.awaitBodyOrNull<String>()).isEqualTo("Person")
		}
	}

	@Test
	fun `awaitBodyOrNull of type Unit`() {
		val spec = mockk<ReactiveVaultClient.ResponseSpec>()
		val entity = mockk<ResponseEntity<Void>>()
		every { spec.toBodilessEntity() } returns Mono.just(entity)
		runBlocking {
			assertThat(spec.awaitBodyOrNull<Unit>()).isEqualTo(Unit)
		}
	}

	@Test
	fun awaitBodilessEntity() {
		val spec = mockk<ReactiveVaultClient.ResponseSpec>()
		val entity = mockk<ResponseEntity<Void>>()
		every { spec.toBodilessEntity() } returns Mono.just(entity)
		runBlocking {
			assertThat(spec.awaitBodilessEntity()).isEqualTo(entity)
		}
	}

	@Test
	fun awaitEntity() {
		val spec = mockk<ReactiveVaultClient.ResponseSpec>()
		every {
			spec.toEntity(object : ParameterizedTypeReference<String>() {})
		} returns Mono.just(ResponseEntity("Person", HttpStatus.OK))
		runBlocking {
			assertThat(spec.awaitEntity<String>().body).isEqualTo("Person")
		}
	}

	private data class PersonContextElement(val person: Person) :
		AbstractCoroutineContextElement(PersonContextElement) {
		companion object Key : CoroutineContext.Key<PersonContextElement>
	}

}