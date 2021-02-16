/*
 * Copyright 2017-2021 the original author or authors.
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.AuthenticationSteps.HttpRequest;
import org.springframework.vault.authentication.AuthenticationSteps.HttpRequestNode;
import org.springframework.vault.authentication.AuthenticationSteps.MapStep;
import org.springframework.vault.authentication.AuthenticationSteps.Node;
import org.springframework.vault.authentication.AuthenticationSteps.OnNextStep;
import org.springframework.vault.authentication.AuthenticationSteps.Pair;
import org.springframework.vault.authentication.AuthenticationSteps.SupplierStep;
import org.springframework.vault.authentication.AuthenticationSteps.ZipStep;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

/**
 * Synchronous executor for {@link AuthenticationSteps} using {@link RestOperations} to
 * login using authentication flows.
 *
 * @author Mark Paluch
 * @since 2.0
 * @see AuthenticationSteps
 */
public class AuthenticationStepsExecutor implements ClientAuthentication {

	private static final Log logger = LogFactory.getLog(AppIdAuthentication.class);

	private final AuthenticationSteps chain;

	private final RestOperations restOperations;

	/**
	 * Create a new {@link AuthenticationStepsExecutor} given {@link AuthenticationSteps}
	 * and {@link RestOperations}.
	 * @param steps must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 */
	public AuthenticationStepsExecutor(AuthenticationSteps steps, RestOperations restOperations) {

		Assert.notNull(steps, "AuthenticationSteps must not be null");
		Assert.notNull(restOperations, "RestOperations must not be null");

		this.chain = steps;
		this.restOperations = restOperations;
	}

	@Override
	@SuppressWarnings("unchecked")
	public VaultToken login() throws VaultException {

		Iterable<Node<?>> steps = this.chain.steps;

		Object state = evaluate(steps);

		if (state instanceof VaultToken) {
			return (VaultToken) state;
		}

		if (state instanceof VaultResponse) {

			VaultResponse response = (VaultResponse) state;
			Assert.state(response.getAuth() != null, "Auth field must not be null");
			return LoginTokenUtil.from(response.getAuth());
		}

		throw new IllegalStateException(
				String.format("Cannot retrieve VaultToken from authentication chain. Got instead %s", state));
	}

	private Object evaluate(Iterable<Node<?>> steps) {

		Object state = null;

		for (Node<?> o : steps) {

			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Executing %s with current state %s", o, state));
			}

			try {
				if (o instanceof HttpRequestNode) {
					state = doHttpRequest((HttpRequestNode<Object>) o, state);
				}

				if (o instanceof MapStep) {
					state = doMapStep((MapStep<Object, Object>) o, state);
				}

				if (o instanceof ZipStep) {
					state = doZipStep((ZipStep<Object, Object>) o, state);
				}

				if (o instanceof OnNextStep) {
					state = doOnNext((OnNextStep<Object>) o, state);
				}

				if (o instanceof SupplierStep<?>) {
					state = doSupplierStep((SupplierStep<Object>) o);
				}

				if (logger.isDebugEnabled()) {
					logger.debug(String.format("Executed %s with current state %s", o, state));
				}
			}
			catch (HttpStatusCodeException e) {
				throw new VaultLoginException(
						String.format("HTTP request %s in state %s failed with Status %s and body %s", o, state,
								e.getRawStatusCode(), VaultResponses.getError(e.getResponseBodyAsString())),
						e);
			}
			catch (RuntimeException e) {
				throw new VaultLoginException(String.format("Authentication execution failed in %s", o), e);
			}
		}
		return state;
	}

	private static Object doSupplierStep(SupplierStep<Object> supplierStep) {
		return supplierStep.get();
	}

	private static Object doMapStep(MapStep<Object, Object> o, Object state) {
		return o.apply(state);
	}

	private Object doZipStep(ZipStep<Object, Object> o, Object state) {

		Object result = evaluate(o.getRight());
		return Pair.of(state, result);
	}

	private static Object doOnNext(OnNextStep<Object> o, Object state) {
		return o.apply(state);
	}

	@SuppressWarnings("ConstantConditions")
	@Nullable
	private Object doHttpRequest(HttpRequestNode<Object> step, @Nullable Object state) {

		HttpRequest<Object> definition = step.getDefinition();

		if (definition.getUri() == null) {

			ResponseEntity<?> exchange = this.restOperations.exchange(definition.getUriTemplate(),
					definition.getMethod(), getEntity(definition.getEntity(), state), definition.getResponseType(),
					(Object[]) definition.getUrlVariables());

			return exchange.getBody();
		}
		ResponseEntity<?> exchange = this.restOperations.exchange(definition.getUri(), definition.getMethod(),
				getEntity(definition.getEntity(), state), definition.getResponseType());

		return exchange.getBody();

	}

	private static HttpEntity<?> getEntity(HttpEntity<?> entity, @Nullable Object state) {

		if (entity == null) {
			return state == null ? HttpEntity.EMPTY : new HttpEntity<>(state);
		}

		if (entity.getBody() == null && state != null) {
			return new HttpEntity<>(state, entity.getHeaders());
		}

		return entity;
	}

}
