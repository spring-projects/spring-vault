/*
 * Copyright 2019 the original author or authors.
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
package org.springframework.vault.core

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.vault.support.VaultResponse
import org.springframework.vault.support.VaultResponseSupport
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Unit tests for [ReactiveVaultOperationsExtensions].
 *
 * @author Mark Paluch
 */
class ReactiveVaultOperationsExtensionsTests {

    val operation = mockk<ReactiveVaultOperations>(relaxed = true)

    @Test
    fun `ReactiveVaultOperations#read() with reified type parameter extension should call its Java counterpart`() {

        operation.read<Person>("the-path")
        verify { operation.read("the-path", Person::class.java) }
    }

    @Test
    fun `ReactiveVaultOperations#read() should call its Java counterpart`() {

        operation.read("the-path")
        verify { operation.read("the-path") }
    }

    @Test
    fun awaitReadWithValue() {

        val expected = VaultResponseSupport<Person>()
        every { operation.read("path", Person::class.java) } returns Mono.just(expected)

        runBlocking {
            assertThat(operation.awaitRead<Person>("path")).isEqualTo(expected)
        }

        verify {
            operation.read("path", Person::class.java)
        }
    }

    @Test
    fun awaitReadWithoutValue() {

        every { operation.read("path", Person::class.java) } returns Mono.empty()

        runBlocking {
            assertThat(operation.awaitReadOrNull<Person>("path")).isNull()
        }

        verify {
            operation.read("path", Person::class.java)
        }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun listAsFlow() {

        every { operation.list("path") } returns Flux.just("one", "two", "three")

        runBlocking {
            assertThat(operation.listAsFlow("path").toList()).contains("one", "two", "three")
        }

        verify {
            operation.list("path")
        }
    }

    @Test
    fun awaitWrite() {

        val expected = VaultResponse()
        every { operation.write("path") } returns Mono.just(expected)

        runBlocking {
            assertThat(operation.awaitWrite("path")).isEqualTo(expected)
        }

        verify {
            operation.write("path")
        }
    }

    @Test
    fun awaitWriteBody() {

        val expected = VaultResponse()
        val body = Person()
        every { operation.write("path", body) } returns Mono.just(expected)

        runBlocking {
            assertThat(operation.awaitWrite("path", body)).isEqualTo(expected)
        }

        verify {
            operation.write("path", body)
        }
    }

    @Test
    fun awaitDelete() {

        every { operation.delete("path") } returns Mono.empty()

        runBlocking {
            assertThat(operation.awaitDelete("path")).isEqualTo(Unit)
        }

        verify {
            operation.delete("path")
        }
    }

    class Person
}
