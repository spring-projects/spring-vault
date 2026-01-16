/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.vault.core.lease;

import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.LeaseListener;

/**
 * Registry to manage {@link RequestedSecret}s (request, lease renewal,
 * rotation).
 *
 * @author Mark Paluch
 * @since 4.1
 * @see SecretLeaseContainer
 */
public interface SecretsRegistry {

	/**
	 * Register a {@link RequestedSecret} with the registry.
	 * <p>Subsequent registrations of the same {@link RequestedSecret}
	 * are considered as a single registration and the secret will be managed only once.
	 * @param secret the requested secret to be managed.
	 */
	void register(RequestedSecret secret);

	/**
	 * Register a {@link RequestedSecret} with the registry with an associated
	 * {@link LeaseListener}.
	 * <p>Subsequent registrations of the same {@link RequestedSecret} are
	 * considered as a single registration and the secret will be managed only once.
	 * A requested secret that has been already been registered and activated by the
	 * container will not lead to emission of a new {@code SecretLeaseCreatedEvent} with
	 * the previous secrets body but rather only to future events such as rotations
	 * or renewals.
	 * @param secret the requested secret to be managed.
	 * @param listener listener to associate with the requested secret. The listener
	 * will be notified only with events concerning the requested secret.
	 */
	void register(RequestedSecret secret, LeaseListener listener);

	/**
	 * Unregister the {@link RequestedSecret} from the registry. Removing the secret
	 * stops lease renewals and secret rotations, and it removes listener
	 * registrations that were {@link #register(RequestedSecret, LeaseListener)
	 * associated with the secret registration}.
	 * @param secret the secret to be deregistered.
	 * @return {@literal true} if the secret was registered before and has been
	 * removed; {@literal false} otherwise.
	 */
	boolean unregister(RequestedSecret secret);

}
