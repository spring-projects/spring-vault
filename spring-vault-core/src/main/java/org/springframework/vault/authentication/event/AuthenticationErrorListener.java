/*
 * Copyright 2019-2020 the original author or authors.
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
package org.springframework.vault.authentication.event;

/**
 * Listener for Vault exceptional {@link AuthenticationEvent}s.
 * <p>
 * Error events can occur during login, login token renewal and login token revocation.
 *
 * @author Mark Paluch
 * @since 2.2
 */
@FunctionalInterface
public interface AuthenticationErrorListener {

	/**
	 * Callback for a {@link AuthenticationErrorEvent}.
	 *
	 * @param authenticationEvent the event object, must not be {@literal null}.
	 */
	void onAuthenticationError(AuthenticationErrorEvent authenticationEvent);
}
