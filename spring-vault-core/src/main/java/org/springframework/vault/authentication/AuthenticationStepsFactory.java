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

/**
 * Factory interface for components that create {@link AuthenticationSteps}.
 * <p>
 * Implementing objects are required to construct {@link AuthenticationSteps} by their
 * needs and invoked once upon {@link AuthenticationSteps} retrieval.
 *
 * @author Mark Paluch
 * @since 2.0
 * @see AuthenticationSteps
 */
@FunctionalInterface
public interface AuthenticationStepsFactory {

	/**
	 * Get the {@link AuthenticationSteps} describing an authentication flow.
	 * @return the {@link AuthenticationSteps} describing an authentication flow.
	 */
	AuthenticationSteps getAuthenticationSteps();

}
