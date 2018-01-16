/*
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.vault.authentication;

import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpEntity;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.AuthenticationSteps.HttpRequest;
import org.springframework.vault.authentication.AuthenticationSteps.HttpRequestNode;
import org.springframework.vault.authentication.AuthenticationSteps.MapStep;
import org.springframework.vault.authentication.AuthenticationSteps.Node;
import org.springframework.vault.authentication.AuthenticationSteps.OnNextStep;
import org.springframework.vault.authentication.AuthenticationSteps.SupplierStep;
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
 *
 * @author Mark Paluch
 * @since 2.0
 * @see AuthenticationSteps
 */
public class AuthenticationStepsOperator implements VaultTokenSupplier {

	private static final Log logger = LogFactory.getLog(AppIdAuthentication.class);

	private final AuthenticationSteps chain;

	private final WebClient webClient;

	/**
	 * Create a new {@link AuthenticationStepsOperator} given {@link AuthenticationSteps}
	 * and {@link WebClient}.
	 *
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
	@SuppressWarnings("unchecked")
	public Mono<VaultToken> getVaultToken() throws VaultException {

		Mono<Object> state = Mono.just(Undefinded.INSTANCE);

		for (Node<?> o : chain.steps) {

			if (logger.isDebugEnabled()) {
				logger.debug(String
						.format("Executing %s with current state %s", o, state));
			}

			if (o instanceof HttpRequestNode) {
				state = state.flatMap(stateObject -> doHttpRequest(
						(HttpRequestNode<Object>) o, stateObject));
			}

			if (o instanceof AuthenticationSteps.MapStep) {
				state = state.map(stateObject -> doMapStep((MapStep<Object, Object>) o,
						stateObject));
			}

			if (o instanceof OnNextStep) {
				state = state.doOnNext(stateObject -> doOnNext((OnNextStep<Object>) o,
						stateObject));
			}

			if (o instanceof AuthenticationSteps.SupplierStep<?>) {
				state = state
						.map(stateObject -> doSupplierStep((SupplierStep<Object>) o));
			}

			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Executed %s with current state %s", o, state));
			}
		}

		return state
				.map(stateObject -> {

					if (stateObject instanceof VaultToken) {
						return (VaultToken) stateObject;
					}

					if (stateObject instanceof VaultResponse) {

						VaultResponse response = (VaultResponse) stateObject;

						Assert.state(response.getAuth() != null,
								"Auth field must not be null");

						return LoginTokenUtil.from(response.getAuth());
					}

					throw new IllegalStateException(
							String.format(
									"Cannot retrieve VaultToken from authentication chain. Got instead %s",
									stateObject));
				})
				.onErrorMap(
						t -> new VaultException(
								"Cannot retrieve VaultToken from authentication chain", t));
	}

	private static Object doSupplierStep(SupplierStep<Object> supplierStep) {
		return supplierStep.get();
	}

	private static Object doMapStep(MapStep<Object, Object> o, Object state) {
		return o.apply(state);
	}

	private static Object doOnNext(OnNextStep<Object> o, Object state) {
		return o.apply(state);
	}

	private Mono<Object> doHttpRequest(HttpRequestNode<Object> step, Object state) {

		HttpRequest<Object> definition = step.getDefinition();
		HttpEntity<?> entity = getEntity(definition.getEntity(), state);

		RequestBodySpec spec;
		if (definition.getUri() == null) {

			spec = webClient.method(definition.getMethod()).uri(
					definition.getUriTemplate(), (Object[]) definition.getUrlVariables());
		}
		else {
			spec = webClient.method(definition.getMethod()).uri(definition.getUri());
		}

		for (Entry<String, List<String>> header : entity.getHeaders().entrySet()) {
			spec = spec.header(header.getKey(), header.getValue().get(0));
		}

		if (entity.getBody() != null && !entity.getBody().equals(Undefinded.INSTANCE)) {
			return spec.syncBody(entity.getBody()).retrieve()
					.bodyToMono(definition.getResponseType());
		}

		return spec.retrieve().bodyToMono(definition.getResponseType());
	}

	private static HttpEntity<?> getEntity(HttpEntity<?> entity, Object state) {

		if (entity == null) {
			return state == null ? HttpEntity.EMPTY : new HttpEntity<>(state);
		}

		if (entity.getBody() == null && state != null) {
			return new HttpEntity<>(state, entity.getHeaders());
		}

		return entity;
	}

	static class Undefinded {

		static final Undefinded INSTANCE = new Undefinded();

		private Undefinded() {
		}
	}
}
