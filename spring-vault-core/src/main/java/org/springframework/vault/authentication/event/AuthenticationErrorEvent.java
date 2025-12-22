/*
 * Copyright 2019-2025 the original author or authors.
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

import java.io.Serial;

import org.springframework.context.ApplicationEvent;

/**
 * Generic event class for authentication error events. These can be generic
 * failures or specific ones such as renewal or login errors.
 *
 * @author Mark Paluch
 * @since 2.2
 * @see LoginFailedEvent
 * @see LoginTokenRenewalFailedEvent
 * @see LoginTokenRevocationFailedEvent
 * @see AuthenticationErrorListener
 */
public class AuthenticationErrorEvent extends ApplicationEvent {

	@Serial
	private static final long serialVersionUID = 1L;


	private final Throwable exception;


	/**
	 * Create a new {@code AuthenticationErrorEvent} given {@code source} and
	 * {@link Exception}.
	 * @param source must not be {@literal null}.
	 * @param exception must not be {@literal null}.
	 */
	public AuthenticationErrorEvent(Object source, Throwable exception) {
		super(source);
		this.exception = exception;
	}

	public Throwable getException() {
		return this.exception;
	}

}
