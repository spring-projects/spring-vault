/*
 * Copyright 2017-2019 the original author or authors.
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
 * Event published before revoking a {@link Lease} for a {@link RequestedSecret}. The
 * secrets associated with {@link Lease} can be valid at the time this event is received.
 *
 * @author Mark Paluch
 * @see AfterSecretLeaseRevocationEvent
 */
public class BeforeSecretLeaseRevocationEvent extends SecretLeaseEvent {

	private static final long serialVersionUID = 1L;

	/**
	 * Create a new {@link SecretLeaseExpiredEvent} given {@link RequestedSecret} and
	 * {@link Lease}.
	 *
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 */
	public BeforeSecretLeaseRevocationEvent(RequestedSecret requestedSecret, Lease lease) {
		super(requestedSecret, lease);
	}
}
