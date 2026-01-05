/*
 * Copyright 2019-present the original author or authors.
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

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.util.Assert;
import org.springframework.vault.authentication.event.AuthenticationErrorEvent;
import org.springframework.vault.authentication.event.AuthenticationErrorListener;
import org.springframework.vault.authentication.event.AuthenticationEvent;
import org.springframework.vault.authentication.event.AuthenticationEventMulticaster;
import org.springframework.vault.authentication.event.AuthenticationListener;

/**
 * Publisher for {@link AuthenticationEvent}s.
 * <p>This publisher dispatches events to {@link AuthenticationListener} and
 * {@link AuthenticationErrorListener}.
 *
 * @author Mark Paluch
 * @see AuthenticationEvent
 * @see AuthenticationErrorEvent
 * @see AuthenticationListener
 * @see AuthenticationErrorListener
 * @since 2.2
 */
public abstract class AuthenticationEventPublisher implements AuthenticationEventMulticaster {

	private final Set<AuthenticationListener> listeners = new CopyOnWriteArraySet<>();

	private final Set<AuthenticationErrorListener> errorListeners = new CopyOnWriteArraySet<>();


	/**
	 * Add a {@link AuthenticationListener}. The listener starts receiving events as
	 * soon as possible.
	 * @param listener the listener, must not be {@literal null}.
	 */
	@Override
	public void addAuthenticationListener(AuthenticationListener listener) {
		Assert.notNull(listener, "AuthenticationEventListener must not be null");
		this.listeners.add(listener);
	}

	/**
	 * Remove a {@link AuthenticationListener}.
	 * @param listener the listener, must not be {@literal null}.
	 */
	@Override
	public void removeAuthenticationListener(AuthenticationListener listener) {
		this.listeners.remove(listener);
	}

	/**
	 * Add a {@link AuthenticationErrorListener}. The listener starts receiving
	 * events as soon as possible.
	 * @param listener the listener, must not be {@literal null}.
	 */
	@Override
	public void addErrorListener(AuthenticationErrorListener listener) {
		Assert.notNull(listener, "AuthenticationEventErrorListener must not be null");
		this.errorListeners.add(listener);
	}

	/**
	 * Remove a {@link AuthenticationErrorListener}.
	 * @param listener the listener, must not be {@literal null}.
	 */
	@Override
	public void removeErrorListener(AuthenticationErrorListener listener) {
		this.errorListeners.remove(listener);
	}

	@Override
	public void multicastEvent(AuthenticationEvent event) {
		for (AuthenticationListener listener : this.listeners) {
			listener.onAuthenticationEvent(event);
		}
	}

	@Override
	public void multicastEvent(AuthenticationErrorEvent event) {
		for (AuthenticationErrorListener listener : this.errorListeners) {
			listener.onAuthenticationError(event);
		}
	}

}
