/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.vault.core.lease.event;

import java.util.Map;

import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;

/**
 * Event published after rotating secrets.
 *
 * @author Mark Paluch
 * @since 2.3
 */
public class SecretLeaseRotatedEvent extends SecretLeaseCreatedEvent {

	private final Lease previousLease;


	/**
	 * Create a new {@code SecretLeaseRotatedEvent} given {@link RequestedSecret},
	 * {@link Lease} and {@code secrets}.
	 * @param requestedSecret must not be {@literal null}.
	 * @param previousLease must not be {@literal null}.
	 * @param currentLease must not be {@literal null}.
	 */
	public SecretLeaseRotatedEvent(RequestedSecret requestedSecret, Lease previousLease, Lease currentLease,
			Map<String, Object> secrets) {
		super(requestedSecret, currentLease, secrets);
		this.previousLease = previousLease;
	}


	public Lease getPreviousLease() {
		return this.previousLease;
	}

	@SuppressWarnings("NullAway")
	public Lease getCurrentLease() {
		return getLease();
	}

}
