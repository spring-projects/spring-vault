/*
 * Copyright 2017-2025 the original author or authors.
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

import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpEntity;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.AuthenticationSteps.HttpRequest;
import org.springframework.vault.authentication.AuthenticationSteps.HttpRequestNode;
import org.springframework.vault.authentication.AuthenticationSteps.MapStep;
import org.springframework.vault.authentication.AuthenticationSteps.Node;
import org.springframework.vault.authentication.AuthenticationSteps.OnNextStep;
import org.springframework.vault.authentication.AuthenticationSteps.Pair;
import org.springframework.vault.authentication.AuthenticationSteps.ScalarValueStep;
import org.springframework.vault.authentication.AuthenticationSteps.SupplierStep;
import org.springframework.vault.authentication.AuthenticationSteps.ZipStep;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;

/**
 * {@link VaultTokenSupplier} using {@link AuthenticationSteps} to create an
 * authentication flow emitting {@link VaultToken}.
 * <p>
 * This class uses {@link WebClient} for non-blocking and reactive HTTP access. The
 * {@link AuthenticationSteps authentication flow} is materialized as reactive sequence
 * postponing execution until {@link Mono#subscribe() subscription}.
 * <p>
 * {@link Supplier Supplier} instances are inspected for their type.
 * {@link ResourceCredentialSupplier} instances are loaded through
 * {@link DataBufferUtils#read(org.springframework.core.io.Resource, org.springframework.core.io.buffer.DataBufferFactory, int)
 * DataBufferUtils} to use non-blocking I/O for file access. {@link Supplier#get() Calls}
 * to generic supplier types are offloaded to a {@link Schedulers#boundedElastic()
 * scheduler} to avoid blocking calls on reactive worker/eventloop threads.
 *
 * @author Mark Paluch
 * @since 2.0
 * @see AuthenticationSteps
 */
public class AuthenticationStepsOperator implements VaultTokenSupplier {

	private static final Log logger = LogFactory.getLog(AuthenticationStepsOperator.class);

	private final AuthenticationSteps chain;

	private final WebClient webClient;

	private final DataBufferFactory factory = new DefaultDataBufferFactory();

	/**
	 * Create a new {@link AuthenticationStepsOperator} given {@link AuthenticationSteps}
	 * and {@link WebClient}.
	 * @param steps must not be {@literal null}.
	 * @param webClient must not be {@literal null}.
	 */
	public AuthenticationStepsOperator(AuthenticationSteps steps, WebClient webClient) {

		Assert.notNull(steps, "AuthenticationSteps must not be null");
		Assert.notNull(webClient, "WebClient must not be null");

		this.chain = steps;
		this.webClient = webClient;
	}

	@Override
	public Mono<VaultToken> getVaultToken() throws VaultException {

		Mono<Object> state = createMono(this.chain.steps);

		return state.map(stateObject -> {

			if (stateObject instanceof VaultToken) {
				return (VaultToken) stateObject;
			}

			if (stateObject instanceof VaultResponse response) {

				Assert.state(response.getAuth() != null, "Auth field must not be null");

				return LoginTokenUtil.from(response.getAuth());
			}

			throw new IllegalStateException(
					"Cannot retrieve VaultToken from authentication chain. Got instead %s".formatted(stateObject));
		}).onErrorMap(t -> new VaultLoginException("Cannot retrieve VaultToken from authentication chain", t));
	}

	@SuppressWarnings("unchecked")
	private Mono<Object> createMono(Iterable<Node<?>> steps) {

		Mono<Object> state = Mono.just(Undefinded.UNDEFINDED);

		for (Node<?> o : steps) {

			if (logger.isDebugEnabled()) {
				logger.debug("Executing %s with current state %s".formatted(o, state));
			}

			if (o instanceof HttpRequestNode) {
				state = state.flatMap(stateObject -> doHttpRequest((HttpRequestNode<Object>) o, stateObject));
			}

			if (o instanceof MapStep) {
				state = state.map(stateObject -> doMapStep((MapStep<Object, Object>) o, stateObject));
			}

			if (o instanceof ZipStep) {
				state = state.zipWith(doZipStep((ZipStep<Object, Object>) o))
					.map(it -> Pair.of(it.getT1(), it.getT2()));
			}

			if (o instanceof OnNextStep) {
				state = state.doOnNext(stateObject -> doOnNext((OnNextStep<Object>) o, stateObject));
			}

			if (o instanceof ScalarValueStep<?>) {
				state = state.map(stateObject -> doScalarValueStep((ScalarValueStep<Object>) o));
			}

			if (o instanceof SupplierStep<?>) {
				state = state.flatMap(stateObject -> doSupplierStepLater((SupplierStep<Object>) o));
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Executed %s with current state %s".formatted(o, state));
			}
		}
		return state;
	}

	private Mono<Object> doHttpRequest(HttpRequestNode<Object> step, Object state) {

		HttpRequest<Object> definition = step.getDefinition();
		HttpEntity<?> entity = AuthenticationStepsExecutor.getEntity(definition.getEntity(), state);

		RequestBodySpec spec;
		if (definition.getUri() == null) {

			spec = this.webClient.method(definition.getMethod())
				.uri(definition.getUriTemplate(), definition.getUrlVariables());
		}
		else {
			spec = this.webClient.method(definition.getMethod()).uri(definition.getUri());
		}

		for (Entry<String, List<String>> header : entity.getHeaders().entrySet()) {
			spec = spec.header(header.getKey(), header.getValue().get(0));
		}

		if (entity.getBody() != null && !entity.getBody().equals(Undefinded.UNDEFINDED)) {
			return spec.bodyValue(entity.getBody()).retrieve().bodyToMono(definition.getResponseType());
		}

		return spec.retrieve().bodyToMono(definition.getResponseType());
	}

	private static Object doMapStep(MapStep<Object, Object> o, Object state) {
		return o.apply(state);
	}

	private Mono<Object> doZipStep(ZipStep<Object, Object> o) {
		return createMono(o.getRight());
	}

	private static void doOnNext(OnNextStep<Object> o, Object state) {
		o.apply(state);
	}

	private static Object doScalarValueStep(ScalarValueStep<Object> scalarValueStep) {
		return scalarValueStep.get();
	}

	private Mono<Object> doSupplierStepLater(SupplierStep<Object> supplierStep) {

		Supplier<?> supplier = supplierStep.getSupplier();

		if (!(supplier instanceof ResourceCredentialSupplier resourceSupplier)) {
			return Mono.fromSupplier(supplierStep.getSupplier()).subscribeOn(Schedulers.boundedElastic());
		}

		return DataBufferUtils.join(DataBufferUtils.read(resourceSupplier.getResource(), this.factory, 4096))
			.map(dataBuffer -> {
				String result = dataBuffer.toString(ResourceCredentialSupplier.CHARSET);
				DataBufferUtils.release(dataBuffer);
				return (Object) result;
			})
			.onErrorMap(IOException.class, e -> new VaultException(
					"Credential retrieval from %s failed".formatted(resourceSupplier.getResource()), e));
	}

	enum Undefinded {

		UNDEFINDED;

	}

}
