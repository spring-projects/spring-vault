/*
 * Copyright 2017-present the original author or authors.
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

/**
 * Empty listener adapter implementing {@link LeaseListener} and
 * {@link LeaseErrorListener}. Typically used to facilitate interface evolution.
 *
 * @author Mark Paluch
 * @see SecretLeaseEvent
 */
public abstract class LeaseListenerAdapter implements LeaseListener, LeaseErrorListener {

	@Override
	public void onLeaseEvent(SecretLeaseEvent leaseEvent) {
		// empty listener method
	}

	@Override
	public void onLeaseError(SecretLeaseEvent leaseEvent, Exception exception) {
		// empty listener method
	}

}
