/*
 * Copyright 2019 the original author or authors.
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

import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;

/**
 * Event published after secrets could not be found for a {@link RequestedSecret}.
 *
 * @author Mark Paluch
 * @since 2.2
 */
public class SecretNotFoundEvent extends SecretLeaseEvent {

	private static final long serialVersionUID = 1L;

	/**
	 * Create a new {@link SecretNotFoundEvent} given {@link RequestedSecret}
	 *
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 */
	public SecretNotFoundEvent(RequestedSecret requestedSecret, Lease lease) {
		super(requestedSecret, lease);
	}
}
