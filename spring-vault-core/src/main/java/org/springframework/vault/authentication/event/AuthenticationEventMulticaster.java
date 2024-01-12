/*
 * Copyright 2023-2024 the original author or authors.
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
 * Interface to be implemented by objects that can manage a number of
 * {@link AuthenticationEvent} and {@link AuthenticationErrorEvent} objects and publish
 * events to them.
 * <p>
 * An {@link org.springframework.vault.authentication.AuthenticationEventPublisher},
 * typically a lifecycle-aware
 * {@link org.springframework.vault.authentication.SessionManager}, can use an
 * {@code AuthenticationEventMulticaster} as a delegate for actually publishing events.
 *
 * @author Mark Paluch
 * @see AuthenticationListener
 * @see AuthenticationErrorListener
 * @since 3.1
 */
public interface AuthenticationEventMulticaster {

	/**
	 * Add a {@link AuthenticationListener}.
	 * @param listener the listener, must not be {@literal null}.
	 */
	void addAuthenticationListener(AuthenticationListener listener);

	/**
	 * Remove a {@link AuthenticationListener}.
	 * @param listener the listener, must not be {@literal null}.
	 */
	void removeAuthenticationListener(AuthenticationListener listener);

	/**
	 * Add a {@link AuthenticationErrorListener}.
	 * @param listener the listener, must not be {@literal null}.
	 */
	void addErrorListener(AuthenticationErrorListener listener);

	/**
	 * Remove a {@link AuthenticationErrorListener}.
	 * @param listener the listener, must not be {@literal null}.
	 */
	void removeErrorListener(AuthenticationErrorListener listener);

	/**
	 * Multicast the given application event to appropriate listeners.
	 * @param event the event to multicast.
	 */
	void multicastEvent(AuthenticationEvent event);

	/**
	 * Multicast the given application event to appropriate listeners.
	 * @param event the event to multicast.
	 */
	void multicastEvent(AuthenticationErrorEvent event);

}
