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
package org.springframework.vault.core.lease.event;

import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;

/**
 * Event published when caught an {@link Exception} during secret retrieval and lease
 * interaction.
 *
 * @author Mark Paluch
 */
public class SecretLeaseErrorEvent extends SecretLeaseEvent {

	private static final long serialVersionUID = 1L;

	private final Throwable exception;

	/**
	 * Create a new {@link SecretLeaseExpiredEvent} given {@link RequestedSecret},
	 * {@link Lease} and {@link Throwable}.
	 *
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease can be {@literal null}.
	 * @param exception must not be {@literal null}.
	 */
	public SecretLeaseErrorEvent(RequestedSecret requestedSecret, Lease lease,
			Throwable exception) {
		super(requestedSecret, lease);
		this.exception = exception;
	}

	public Throwable getException() {
		return exception;
	}
}
