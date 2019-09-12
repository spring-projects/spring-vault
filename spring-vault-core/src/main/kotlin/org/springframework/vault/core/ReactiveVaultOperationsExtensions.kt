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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.vault.support.VaultResponse
import org.springframework.vault.support.VaultResponseSupport
import reactor.core.publisher.Mono

/**
 * Extension for [ReactiveVaultOperations.read] leveraging reified type parameters.
 *
 * @author Mark Paluch
 * @since 2.2
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
inline fun <reified T : Any> ReactiveVaultOperations.read(path: String): Mono<VaultResponseSupport<T>> =
        read(path, T::class.java)

/**
 * Non-nullable Coroutines variant of [ReactiveVaultOperations.read].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend inline fun <reified T : Any> ReactiveVaultOperations.awaitRead(path: String): VaultResponseSupport<T> =
        read(path, T::class.java).awaitSingle()

/**
 * Nullable Coroutines variant of [ReactiveVaultOperations.read].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend inline fun <reified T : Any> ReactiveVaultOperations.awaitReadOrNull(path: String): VaultResponseSupport<T>? =
        read(path, T::class.java).awaitFirstOrNull()

/**
 * Coroutines [Flow] variant of [ReactiveVaultOperations.list].
 *
 * @author Mark Paluch
 * @since 2.2
 */
@ExperimentalCoroutinesApi
fun ReactiveVaultOperations.listAsFlow(path: String): Flow<String> =
        list(path).asFlow()

/**
 * Non-nullable Coroutines variant of [ReactiveVaultOperations.write].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend fun ReactiveVaultOperations.awaitWrite(path: String): VaultResponse =
        write(path).awaitSingle()

/**
 * Non-nullable Coroutines variant of [ReactiveVaultOperations.write].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend fun ReactiveVaultOperations.awaitWrite(path: String, body: Any): VaultResponse =
        write(path, body).awaitSingle()

/**
 * Non-nullable Coroutines variant of [ReactiveVaultOperations.delete].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend fun ReactiveVaultOperations.awaitDelete(path: String) = delete(path).awaitFirstOrNull()

