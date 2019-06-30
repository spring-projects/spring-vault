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

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

/**
 * Unit tests for [VaultOperationsExtensions].
 *
 * @author Mark Paluch
 */
class VaultOperationsExtensionsTests {

    val operation = mockk<VaultOperations>(relaxed = true)

    @Test
    fun `VaultOperations#read() with reified type parameter extension should call its Java counterpart`() {

        operation.read<Person>("the-path")
        verify { operation.read("the-path", Person::class.java) }
    }

    @Test
    fun `VaultOperations#read() should call its Java counterpart`() {

        operation.read("the-path")
        verify { operation.read("the-path") }
    }

    class Person
}
