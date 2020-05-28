/*
 * Copyright 2017-2020 the original author or authors.
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

import org.springframework.context.ApplicationEvent;
import org.springframework.lang.Nullable;
import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;

/**
 * Abstract base class for {@link Lease} based events associated with
 * {@link RequestedSecret}.
 *
 * @author Mark Paluch
 * @see ApplicationEvent
 * @see Lease
 * @see RequestedSecret
 */
public abstract class SecretLeaseEvent extends ApplicationEvent {

	private static final long serialVersionUID = 1L;

	@Nullable
	private final Lease lease;

	/**
	 * Create a new {@link SecretLeaseEvent} given {@link RequestedSecret} and
	 * {@link Lease}.
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease can be {@literal null}.
	 */
	protected SecretLeaseEvent(RequestedSecret requestedSecret, @Nullable Lease lease) {
		super(requestedSecret);

		this.lease = lease;
	}

	@Override
	public RequestedSecret getSource() {
		return (RequestedSecret) super.getSource();
	}

	@Nullable
	public Lease getLease() {
		return this.lease;
	}

}
