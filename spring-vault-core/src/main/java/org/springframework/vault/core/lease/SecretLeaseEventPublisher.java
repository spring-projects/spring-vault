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

package org.springframework.vault.core.lease;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.*;

/**
 * Publisher for {@link SecretLeaseEvent}s.
 * <p>This publisher dispatches events to {@link LeaseListener} and
 * {@link LeaseErrorListener}. Instances are thread-safe once
 * {@link #afterPropertiesSet() initialized}.
 *
 * @author Mark Paluch
 * @see SecretLeaseEvent
 * @see LeaseListener
 * @see LeaseErrorListener
 */
public class SecretLeaseEventPublisher implements InitializingBean {

	private final Set<LeaseListener> leaseListeners = new CopyOnWriteArraySet<>();

	private final Set<LeaseErrorListener> leaseErrorListeners = new CopyOnWriteArraySet<>();

	/**
	 * Add a {@link LeaseListener} to the container. The listener starts receiving
	 * events as soon as possible.
	 * @param listener lease listener, must not be {@literal null}.
	 */
	public void addLeaseListener(LeaseListener listener) {

		Assert.notNull(listener, "LeaseListener must not be null");

		this.leaseListeners.add(listener);
	}

	/**
	 * Remove a {@link LeaseListener}.
	 * @param listener must not be {@literal null}.
	 */
	public void removeLeaseListener(LeaseListener listener) {
		this.leaseListeners.remove(listener);
	}

	/**
	 * Add a {@link LeaseErrorListener} to the container. The listener starts
	 * receiving events as soon as possible.
	 * @param listener lease listener, must not be {@literal null}.
	 */
	public void addErrorListener(LeaseErrorListener listener) {

		Assert.notNull(listener, "LeaseListener must not be null");

		this.leaseErrorListeners.add(listener);
	}

	/**
	 * Remove a {@link LeaseErrorListener}.
	 * @param listener must not be {@literal null}.
	 */
	public void removeLeaseErrorListener(LeaseErrorListener listener) {
		this.leaseErrorListeners.remove(listener);
	}

	@Override
	public void afterPropertiesSet() {

		if (this.leaseErrorListeners.isEmpty()) {
			addErrorListener(LoggingErrorListener.INSTANCE);
		}
	}

	/**
	 * Hook method called when secrets were obtained. The default implementation is
	 * to notify {@link LeaseListener}. Implementations can override this method in
	 * subclasses.
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 * @param body must not be {@literal null}.
	 * @see SecretLeaseCreatedEvent
	 */
	protected void onSecretsObtained(RequestedSecret requestedSecret, Lease lease, Map<String, Object> body) {
		dispatch(new SecretLeaseCreatedEvent(requestedSecret, lease, body));
	}

	/**
	 * Hook method called when secrets were rotated. The default implementation is
	 * to notify {@link LeaseListener}. Implementations can override this method in
	 * subclasses.
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 * @param body must not be {@literal null}.
	 * @since 2.3
	 * @see SecretLeaseRotatedEvent
	 */
	protected void onSecretsRotated(RequestedSecret requestedSecret, Lease previousLease, Lease lease,
			Map<String, Object> body) {
		dispatch(new SecretLeaseRotatedEvent(requestedSecret, previousLease, lease, body));
	}

	/**
	 * Hook method called when secrets were not found. The default implementation is
	 * to notify {@link LeaseListener}. Implementations can override this method in
	 * subclasses.
	 * @param requestedSecret must not be {@literal null}.
	 * @see SecretNotFoundEvent
	 */
	protected void onSecretsNotFound(RequestedSecret requestedSecret) {
		dispatch(new SecretNotFoundEvent(requestedSecret, Lease.none()));
	}

	/**
	 * Hook method called when a {@link Lease} is renewed. The default
	 * implementation is to notify {@link LeaseListener}. Implementations can
	 * override this method in subclasses.
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 * @see AfterSecretLeaseRenewedEvent
	 */
	protected void onAfterLeaseRenewed(RequestedSecret requestedSecret, Lease lease) {
		dispatch(new AfterSecretLeaseRenewedEvent(requestedSecret, lease));
	}

	/**
	 * Hook method called before triggering revocation for a {@link Lease}. The
	 * default implementation is to notify {@link LeaseListener}. Implementations
	 * can override this method in subclasses.
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 * @see BeforeSecretLeaseRevocationEvent
	 */
	protected void onBeforeLeaseRevocation(RequestedSecret requestedSecret, Lease lease) {
		dispatch(new BeforeSecretLeaseRevocationEvent(requestedSecret, lease));
	}

	/**
	 * Hook method called after triggering revocation for a {@link Lease}. The
	 * default implementation is to notify {@link LeaseListener}. Implementations
	 * can override this method in subclasses.
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 * @see AfterSecretLeaseRevocationEvent
	 */
	protected void onAfterLeaseRevocation(RequestedSecret requestedSecret, Lease lease) {
		dispatch(new AfterSecretLeaseRevocationEvent(requestedSecret, lease));
	}

	/**
	 * Hook method called when a {@link Lease} expires. The default implementation
	 * is to notify {@link LeaseListener}. Implementations can override this method
	 * in subclasses.
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 * @see SecretLeaseExpiredEvent
	 */
	protected void onLeaseExpired(RequestedSecret requestedSecret, Lease lease) {
		dispatch(new SecretLeaseExpiredEvent(requestedSecret, lease));
	}

	/**
	 * Hook method called when an error occurred during secret retrieval, lease
	 * renewal, and other Vault interactions. The default implementation is to
	 * notify {@link LeaseErrorListener}. Implementations can override this method
	 * in subclasses.
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease may be {@literal null}
	 * @param e the causing exception.
	 * @see SecretLeaseErrorEvent
	 */
	protected void onError(RequestedSecret requestedSecret, @Nullable Lease lease, Exception e) {
		dispatch(new SecretLeaseErrorEvent(requestedSecret, lease, e));
	}

	/**
	 * Dispatch the event to all {@link LeaseListener}s.
	 * @param leaseEvent the event to dispatch.
	 */
	void dispatch(SecretLeaseEvent leaseEvent) {

		for (LeaseListener listener : this.leaseListeners) {
			listener.onLeaseEvent(leaseEvent);
		}
	}

	/**
	 * Dispatch the event to all {@link LeaseErrorListener}s.
	 * @param errorEvent the event to dispatch.
	 */
	void dispatch(SecretLeaseErrorEvent errorEvent) {

		for (LeaseErrorListener listener : this.leaseErrorListeners) {
			listener.onLeaseError(errorEvent, (Exception) errorEvent.getException());
		}
	}

	/**
	 * Simple {@link LeaseErrorListener} implementation to log errors.
	 */
	public enum LoggingErrorListener implements LeaseErrorListener {

		INSTANCE;

		@SuppressWarnings("FieldMayBeFinal") // allow setting via reflection.
		private static Log logger = LogFactory.getLog(LoggingErrorListener.class);

		@Override
		public void onLeaseError(SecretLeaseEvent leaseEvent, Exception exception) {
			logger.warn("[%s] %s %s".formatted(leaseEvent.getSource(), leaseEvent.getLease(), exception.getMessage()),
					exception);
		}

	}

}
