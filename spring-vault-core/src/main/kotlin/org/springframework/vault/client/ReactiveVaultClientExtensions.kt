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

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.*
import kotlinx.coroutines.withContext
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.ResponseEntity
import org.springframework.vault.client.ReactiveVaultClient.RequestHeadersSpec
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.CoExchangeFilterFunction
import org.springframework.web.reactive.function.client.CoExchangeFilterFunction.Companion.COROUTINE_CONTEXT_ATTRIBUTE
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.context.Context
import kotlin.coroutines.CoroutineContext

/**
 * Extension for [ReactiveVaultClient.RequestBodySpec.body] providing a `bodyWithType<Foo>(...)` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 */
inline fun <reified T : Any> ReactiveVaultClient.RequestBodySpec.bodyWithType(body: T): RequestHeadersSpec<*> =
	body(body, object : ParameterizedTypeReference<T>() {})

/**
 * Extension for [ReactiveVaultClient.RequestBodySpec.bodyValue] providing a `bodyValueWithType<Foo>(...)` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 */
inline fun <reified T : Any> ReactiveVaultClient.RequestBodySpec.bodyValueWithType(body: T): RequestHeadersSpec<*> =
	bodyValue(body, object : ParameterizedTypeReference<T>() {})

/**
 * Coroutines variant of [ReactiveVaultClient.RequestHeadersSpec.exchangeToFlux].
 */
fun <T : Any> RequestHeadersSpec<out RequestHeadersSpec<*>>.exchangeToFlow(
	responseHandler: (ClientResponse) -> Flow<T>
): Flow<T> =
	exchangeToFlux { responseHandler.invoke(it).asFlux() }.asFlow()

/**
 * Extension for [ReactiveVaultClient.ResponseSpec.bodyToMono] providing a `bodyToMono<Foo>()` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 */
inline fun <reified T : Any> ReactiveVaultClient.ResponseSpec.bodyToMono(): Mono<T> =
	bodyToMono(object : ParameterizedTypeReference<T>() {})

/**
 * Extension for [ReactiveVaultClient.ResponseSpec.bodyToFlux] providing a `bodyToFlux<Foo>()` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 */
inline fun <reified T : Any> ReactiveVaultClient.ResponseSpec.bodyToFlux(): Flux<T> =
	bodyToFlux(object : ParameterizedTypeReference<T>() {})

/**
 * Coroutines [kotlinx.coroutines.flow.Flow] based variant of [ReactiveVaultClient.ResponseSpec.bodyToFlux].
 */
inline fun <reified T : Any> ReactiveVaultClient.ResponseSpec.bodyToFlow(): Flow<T> =
	bodyToFlux<T>().asFlow()

/**
 * Coroutines variant of [ReactiveVaultClient.ResponseSpec.bodyToMono].
 *
 * @throws NoSuchElementException if the underlying [Mono] does not emit any value
 */
suspend inline fun <reified T : Any> ReactiveVaultClient.ResponseSpec.awaitBody(): T {
	val context = currentCoroutineContext().minusKey(Job.Key)
	return withContext(context.toReactorContext()) {
		when (T::class) {
			Unit::class -> toBodilessEntity().awaitSingle().let { Unit as T }
			else -> bodyToMono<T>().awaitSingle()
		}
	}
}

/**
 * Coroutines variant of [ReactiveVaultClient.ResponseSpec.bodyToMono].
 */
suspend inline fun <reified T : Any> ReactiveVaultClient.ResponseSpec.awaitBodyOrNull(): T? {
	val context = currentCoroutineContext().minusKey(Job.Key)
	return withContext(context.toReactorContext()) {
		when (T::class) {
			Unit::class -> toBodilessEntity().awaitSingle().let { Unit as T? }
			else -> bodyToMono<T>().awaitSingleOrNull()
		}
	}
}

/**
 * Coroutines variant of [ReactiveVaultClient.ResponseSpec.toBodilessEntity].
 */
suspend fun ReactiveVaultClient.ResponseSpec.awaitBodilessEntity(): ResponseEntity<Void> {
	val context = currentCoroutineContext().minusKey(Job.Key)
	return withContext(context.toReactorContext()) {
		toBodilessEntity().awaitSingle()
	}
}

/**
 * Extension for [ReactiveVaultClient.ResponseSpec.toEntity] providing a `toEntity<Foo>()` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 */
inline fun <reified T : Any> ReactiveVaultClient.ResponseSpec.toEntity(): Mono<ResponseEntity<T>> =
	toEntity(object : ParameterizedTypeReference<T>() {})

/**
 * Extension for [ReactiveVaultClient.ResponseSpec.toEntityList] providing a `toEntityList<Foo>()` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 */
inline fun <reified T : Any> ReactiveVaultClient.ResponseSpec.toEntityList(): Mono<ResponseEntity<List<T>>> =
	toEntityList(object : ParameterizedTypeReference<T>() {})

/**
 * Extension for [ReactiveVaultClient.ResponseSpec.toEntityFlux] providing a `toEntityFlux<Foo>()` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 */
inline fun <reified T : Any> ReactiveVaultClient.ResponseSpec.toEntityFlux(): Mono<ResponseEntity<Flux<T>>> =
	toEntityFlux(object : ParameterizedTypeReference<T>() {})

/**
 * Coroutines variant of [ReactiveVaultClient.RequestHeadersSpec.exchangeToMono].
 */
suspend fun <T : Any> RequestHeadersSpec<out RequestHeadersSpec<*>>.awaitExchange(responseHandler: suspend (ClientResponse) -> T): T {
	val context = currentCoroutineContext().minusKey(Job.Key)
	return withContext(context.toReactorContext()) {
		exchangeToMono { mono(context) { responseHandler.invoke(it) } }.awaitSingle()
	}
}

/**
 * Variant of [ReactiveVaultClient.RequestHeadersSpec.awaitExchange] that allows a nullable return
 */
suspend fun <T : Any> RequestHeadersSpec<out RequestHeadersSpec<*>>.awaitExchangeOrNull(responseHandler: suspend (ClientResponse) -> T?): T? {
	val context = currentCoroutineContext().minusKey(Job.Key)
	return withContext(context.toReactorContext()) {
		exchangeToMono { mono(context) { responseHandler.invoke(it) } }.awaitSingleOrNull()
	}
}

/**
 * Extension for [ReactiveVaultClient.ResponseSpec.toEntity] providing a `toEntity<Foo>()` variant
 * leveraging Kotlin reified type parameters and allows [kotlin.coroutines.CoroutineContext]
 * propagation to the [CoExchangeFilterFunction]. This extension is not subject to type erasure
 * and retains actual generic type arguments.
 */
suspend inline fun <reified T : Any> ReactiveVaultClient.ResponseSpec.awaitEntity(): ResponseEntity<T> {
	val context = currentCoroutineContext().minusKey(Job.Key)
	return withContext(context.toReactorContext()) {
		toEntity(object : ParameterizedTypeReference<T>() {}).awaitSingle()
	}
}

@PublishedApi
internal fun CoroutineContext.toReactorContext(): ReactorContext {
	val context = Context.of(COROUTINE_CONTEXT_ATTRIBUTE, this).readOnly()
	return (this[ReactorContext.Key]?.context?.putAll(context) ?: context).asCoroutineContext()
}