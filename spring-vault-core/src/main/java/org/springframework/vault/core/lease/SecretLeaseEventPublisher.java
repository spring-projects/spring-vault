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
package org.springframework.vault.core.lease;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import lombok.extern.apachecommons.CommonsLog;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.AfterSecretLeaseRenewedEvent;
import org.springframework.vault.core.lease.event.AfterSecretLeaseRevocationEvent;
import org.springframework.vault.core.lease.event.BeforeSecretLeaseRevocationEvent;
import org.springframework.vault.core.lease.event.LeaseErrorListener;
import org.springframework.vault.core.lease.event.LeaseListener;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseErrorEvent;
import org.springframework.vault.core.lease.event.SecretLeaseEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;

/**
 * Publisher for {@link SecretLeaseEvent}s.
 * <p>
 * This publisher dispatches events to {@link LeaseListener} and
 * {@link LeaseErrorListener}. Instances are thread-safe once
 * {@link #afterPropertiesSet() initialized}.
 *
 * @author Mark Paluch
 * @see SecretLeaseEvent
 * @see LeaseListener
 * @see LeaseErrorListener
 */
public class SecretLeaseEventPublisher implements InitializingBean {

	private final Set<LeaseListener> leaseListeners = new CopyOnWriteArraySet<LeaseListener>();

	private final Set<LeaseErrorListener> leaseErrorListeners = new CopyOnWriteArraySet<LeaseErrorListener>();

	/**
	 * Add a {@link LeaseListener} to the container. The listener starts receiving events
	 * as soon as possible.
	 *
	 * @param listener lease listener, must not be {@literal null}.
	 */
	public void addLeaseListener(LeaseListener listener) {

		Assert.notNull(listener, "LeaseListener must not be null");

		this.leaseListeners.add(listener);
	}

	/**
	 * Remove a {@link LeaseListener}.
	 *
	 * @param listener must not be {@literal null}.
	 */
	public void removeLeaseListener(LeaseListener listener) {
		this.leaseListeners.remove(listener);
	}

	/**
	 * Add a {@link LeaseErrorListener} to the container. The listener starts receiving
	 * events as soon as possible.
	 *
	 * @param listener lease listener, must not be {@literal null}.
	 */
	public void addErrorListener(LeaseErrorListener listener) {

		Assert.notNull(listener, "LeaseListener must not be null");

		this.leaseErrorListeners.add(listener);
	}

	/**
	 * Remove a {@link LeaseErrorListener}.
	 *
	 * @param listener must not be {@literal null}.
	 */
	public void removeLeaseErrorListener(LeaseErrorListener listener) {
		this.leaseErrorListeners.remove(listener);
	}

	@Override
	public void afterPropertiesSet() throws Exception {

		if (this.leaseErrorListeners.isEmpty()) {
			addErrorListener(LoggingErrorListener.INSTANCE);
		}
	}

	/**
	 * Hook method called when secrets were obtained. The default implementation is to
	 * notify {@link LeaseListener}. Implementations can override this method in
	 * subclasses.
	 *
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 * @param body must not be {@literal null}.
	 */
	protected void onSecretsObtained(RequestedSecret requestedSecret, Lease lease,
			Map<String, Object> body) {

		for (LeaseListener leaseListener : leaseListeners) {
			leaseListener.onLeaseEvent(new SecretLeaseCreatedEvent(requestedSecret,
					lease, body));
		}
	}

	/**
	 * Hook method called when a {@link Lease} is renewed. The default implementation is
	 * to notify {@link LeaseListener}. Implementations can override this method in
	 * subclasses.
	 *
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 */
	protected void onAfterLeaseRenewed(RequestedSecret requestedSecret, Lease lease) {

		for (LeaseListener leaseListener : leaseListeners) {
			leaseListener.onLeaseEvent(new AfterSecretLeaseRenewedEvent(requestedSecret,
					lease));
		}
	}

	/**
	 * Hook method called before triggering revocation for a {@link Lease}. The default
	 * implementation is to notify {@link LeaseListener}. Implementations can override
	 * this method in subclasses.
	 *
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 */
	protected void onBeforeLeaseRevocation(RequestedSecret requestedSecret, Lease lease) {

		for (LeaseListener leaseListener : leaseListeners) {
			leaseListener.onLeaseEvent(new BeforeSecretLeaseRevocationEvent(
					requestedSecret, lease));
		}
	}

	/**
	 * Hook method called after triggering revocation for a {@link Lease}. The default
	 * implementation is to notify {@link LeaseListener}. Implementations can override
	 * this method in subclasses.
	 *
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 */
	protected void onAfterLeaseRevocation(RequestedSecret requestedSecret, Lease lease) {

		for (LeaseListener leaseListener : leaseListeners) {
			leaseListener.onLeaseEvent(new AfterSecretLeaseRevocationEvent(
					requestedSecret, lease));
		}
	}

	/**
	 * Hook method called when a {@link Lease} expires. The default implementation is to
	 * notify {@link LeaseListener}. Implementations can override this method in
	 * subclasses.
	 *
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 */
	protected void onLeaseExpired(RequestedSecret requestedSecret, Lease lease) {

		for (LeaseListener leaseListener : leaseListeners) {
			leaseListener
					.onLeaseEvent(new SecretLeaseExpiredEvent(requestedSecret, lease));
		}
	}

	/**
	 * Hook method called when an error occurred during secret retrieval, lease renewal,
	 * and other Vault interactions. The default implementation is to notify
	 * {@link LeaseErrorListener}. Implementations can override this method in subclasses.
	 *
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease may be {@literal null}
	 * @param e the causing exception.
	 */
	protected void onError(RequestedSecret requestedSecret, Lease lease, Exception e) {

		for (LeaseErrorListener leaseErrorListener : leaseErrorListeners) {
			leaseErrorListener.onLeaseError(new SecretLeaseErrorEvent(requestedSecret,
					lease, e), e);
		}
	}

	/**
	 * Simple {@link LeaseErrorListener} implementation to log errors.
	 */
	@CommonsLog
	public enum LoggingErrorListener implements LeaseErrorListener {

		INSTANCE;

		@Override
		public void onLeaseError(SecretLeaseEvent leaseEvent, Exception exception) {
			log.warn(
					String.format("[%s] %s %s", leaseEvent.getSource(),
							leaseEvent.getLease(), exception.getMessage()), exception);
		}
	}
}
