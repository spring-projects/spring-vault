/*
 * Copyright 2017-2024 the original author or authors.
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

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;

/**
 * Event published after obtaining secrets potentially associated with a {@link Lease}.
 *
 * @author Mark Paluch
 */
public class SecretLeaseCreatedEvent extends SecretLeaseEvent {

	@Serial
	private static final long serialVersionUID = 1L;

	private final Map<String, Object> secrets;

	/**
	 * Create a new {@link SecretLeaseCreatedEvent} given {@link RequestedSecret},
	 * {@link Lease} and {@code secrets}.
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 * @param secrets must not be {@literal null}.
	 */
	public SecretLeaseCreatedEvent(RequestedSecret requestedSecret, Lease lease, Map<String, Object> secrets) {

		super(requestedSecret, lease);
		this.secrets = Collections.unmodifiableMap(new LinkedHashMap<>(secrets));
	}

	public Map<String, Object> getSecrets() {
		return this.secrets;
	}

}
