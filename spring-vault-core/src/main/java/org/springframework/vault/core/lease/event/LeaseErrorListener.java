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

/**
 * Listener for Vault exceptional {@link SecretLeaseEvent}s.
 * <p>
 * Error events can occur during secret retrieval, lease renewal, lease revocation and
 * secret rotation.
 *
 * @author Mark Paluch
 */
public interface LeaseErrorListener {

	/**
	 * Callback for a {@link SecretLeaseEvent}
	 *
	 * @param leaseEvent the event object, must not be {@literal null}.
	 * @param exception the thrown {@link Exception}.
	 */
	void onLeaseError(SecretLeaseEvent leaseEvent, Exception exception);
}
