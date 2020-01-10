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

import org.springframework.context.ApplicationEvent;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.VaultTokenSupplier;
import org.springframework.vault.support.VaultToken;

/**
 * Event published before renewing a {@link VaultToken login token}.
 * <p>
 * Provides {@link ClientAuthentication} or {@link VaultTokenSupplier} as
 * {@link #getSource() source}.
 *
 * @author Mark Paluch
 * @since 2.2
 * @see ApplicationEvent
 */
public class LoginFailedEvent extends AuthenticationErrorEvent {

	private static final long serialVersionUID = 1L;

	/**
	 * Create a new {@link LoginFailedEvent} given {@link Exception}.
	 *
	 * @param source the {@link ClientAuthentication} or {@link VaultTokenSupplier}
	 *     associated with this event, must not be {@literal null}.
	 * @param exception must not be {@literal null}.
	 */
	public LoginFailedEvent(Object source, Throwable exception) {
		super(source, exception);
	}
}
