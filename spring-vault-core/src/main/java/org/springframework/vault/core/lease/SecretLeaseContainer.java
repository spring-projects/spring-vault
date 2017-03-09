/*
 * Copyright 2017 the original author or authors.
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

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.apachecommons.CommonsLog;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.core.RestOperationsCallback;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.domain.RequestedSecret.Mode;
import org.springframework.vault.core.lease.event.LeaseErrorListener;
import org.springframework.vault.core.lease.event.LeaseListener;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

/**
 * Event-based container to request secrets from Vault and renew the associated
 * {@link Lease}. Secrets can be rotated, depending on the requested
 * {@link RequestedSecret.Mode}.
 *
 * Usage example:
 *
 * <pre>
 * <code>
SecretLeaseContainer container = new SecretLeaseContainer(vaultOperations,
		taskScheduler);

final RequestedSecret requestedSecret = container
		.requestRotatingSecret("mysql/creds/my-role");
container.addLeaseListener(new LeaseListenerAdapter() {
	&#64;Override
	public void onLeaseEvent(LeaseEvent leaseEvent) {

		if (requestedSecret == leaseEvent.getSource()) {

			if (leaseEvent instanceof LeaseCreatedEvent) {

			}

			if (leaseEvent instanceof LeaseExpiredEvent) {

			}
		}
	}
});

container.afterPropertiesSet();
container.start(); // events are triggered after starting the container
 * </code>
 * </pre>
 * <p>
 * This container keeps track over {@link RequestedSecret}s and requests secrets upon
 * {@link #start()}. Leases qualified for {@link Lease#isRenewable() renewal} are renewed
 * by this container applying {@code minRenewalSeconds}/{@code expiryThresholdSeconds} on
 * a {@link TaskScheduler background thread}.
 * <p>
 * Requests for secrets can define either renewal or rotation. The container renews leases
 * until expiry. Rotating secrets renew their associated lease until expiry and request
 * new secrets after expiry. Vault requires active interaction from a caller side to
 * determine a secret is expired. Vault does not send any events. Expired secrets events
 * can dispatch later than the actual expiry.
 * <p>
 * The container dispatches lease events to {@link LeaseListener} and
 * {@link LeaseErrorListener}. Event notifications are dispatched either on the
 * {@link #start() starting} {@link Thread} or worker threads used for background renewal.
 * <p>
 * Instances are thread-safe once {@link #afterPropertiesSet() initialized}.
 *
 * @author Mark Paluch
 * @see RequestedSecret
 * @see SecretLeaseEventPublisher
 * @see Lease
 */
@CommonsLog
public class SecretLeaseContainer extends SecretLeaseEventPublisher
		implements InitializingBean, DisposableBean {

	private final static AtomicIntegerFieldUpdater<SecretLeaseContainer> UPDATER = AtomicIntegerFieldUpdater
			.newUpdater(SecretLeaseContainer.class, "status");

	private static final AtomicInteger poolId = new AtomicInteger();

	private final static int STATUS_INITIAL = 0;
	private final static int STATUS_STARTED = 1;
	private final static int STATUS_DESTROYED = 2;

	private final List<RequestedSecret> requestedSecrets = new CopyOnWriteArrayList<RequestedSecret>();

	private final Map<RequestedSecret, LeaseRenewalScheduler> renewals = new ConcurrentHashMap<RequestedSecret, LeaseRenewalScheduler>();

	private final VaultOperations operations;

	private int minRenewalSeconds = 10;

	private int expiryThresholdSeconds = 60;

	private TaskScheduler taskScheduler;

	private boolean manageTaskScheduler;

	private volatile boolean initialized;

	private volatile int status = STATUS_INITIAL;

	/**
	 * Create a new {@link SecretLeaseContainer} given {@link VaultOperations}.
	 *
	 * @param operations must not be {@literal null}.
	 */
	public SecretLeaseContainer(VaultOperations operations) {

		Assert.notNull(operations, "VaultOperations must not be null");

		this.operations = operations;
	}

	/**
	 * Create a new {@link SecretLeaseContainer} given {@link VaultOperations} and
	 * {@link TaskScheduler}.
	 *
	 * @param operations must not be {@literal null}.
	 * @param taskScheduler must not be {@literal null}.
	 */
	public SecretLeaseContainer(VaultOperations operations, TaskScheduler taskScheduler) {

		Assert.notNull(operations, "VaultOperations must not be null");
		Assert.notNull(taskScheduler, "TaskScheduler must not be null");

		this.operations = operations;
		setTaskScheduler(taskScheduler);
	}

	/**
	 * Set the expiry threshold. {@link Lease} is renewed the given seconds before it
	 * expires.
	 *
	 * @param expiryThresholdSeconds number of seconds before {@link Lease} expiry.
	 */
	public void setExpiryThresholdSeconds(int expiryThresholdSeconds) {
		this.expiryThresholdSeconds = expiryThresholdSeconds;
	}

	/**
	 * Sets the amount of seconds that is at least required before renewing a lease.
	 * {@code minRenewalSeconds} prevents renewals to happen too often.
	 *
	 * @param minRenewalSeconds number of seconds that is at least required before
	 * renewing a {@link Lease}.
	 */
	public void setMinRenewalSeconds(int minRenewalSeconds) {
		this.minRenewalSeconds = minRenewalSeconds;
	}

	public int getMinRenewalSeconds() {
		return minRenewalSeconds;
	}

	public int getExpiryThresholdSeconds() {
		return expiryThresholdSeconds;
	}

	/**
	 * Sets the {@link TaskScheduler} to use for scheduling and execution of lease
	 * renewals.
	 *
	 * @param taskScheduler must not be {@literal null}.
	 */
	public void setTaskScheduler(TaskScheduler taskScheduler) {

		Assert.notNull(taskScheduler, "TaskScheduler must not be null");
		this.taskScheduler = taskScheduler;
	}

	/**
	 * Request a renewable secret at {@code path}.
	 *
	 * @param path must not be {@literal null} or empty.
	 * @return the {@link RequestedSecret}.
	 */
	public RequestedSecret requestRenewableSecret(String path) {
		return addRequestedSecret(RequestedSecret.renewable(path));
	}

	/**
	 * Request a rotating secret at {@code path}.
	 *
	 * @param path must not be {@literal null} or empty.
	 * @return the {@link RequestedSecret}.
	 */
	public RequestedSecret requestRotatingSecret(String path) {
		return addRequestedSecret(RequestedSecret.rotating(path));
	}

	/**
	 * Add a {@link RequestedSecret}.
	 *
	 * @param requestedSecret must not be {@literal null}.
	 */
	public RequestedSecret addRequestedSecret(RequestedSecret requestedSecret) {

		Assert.notNull(requestedSecret, "RequestedSecret must not be null");

		this.requestedSecrets.add(requestedSecret);

		if (initialized) {

			LeaseRenewalScheduler leaseRenewalScheduler = new LeaseRenewalScheduler(
					this.taskScheduler);
			this.renewals.put(requestedSecret, leaseRenewalScheduler);

			if (this.status == STATUS_STARTED) {
				start(requestedSecret, leaseRenewalScheduler);
			}
		}

		return requestedSecret;
	}

	/**
	 * Start the {@link SecretLeaseContainer}. Starting the container will initially
	 * obtain secrets and leases for the requested secrets. A started container publishes
	 * events through {@link LeaseListener}. Additional secrets can be requested at any
	 * time.
	 * <p>
	 * Multiple calls are synchronized to start the container only once. Container start
	 * requires {@link #afterPropertiesSet() initialization} and cannot be started once
	 * the container was {@link #destroy() destroyed}.
	 *
	 * @see #afterPropertiesSet()
	 * @see #stop()
	 */
	public void start() {

		Assert.state(this.initialized, "Container is not initialized");
		Assert.state(this.status != STATUS_DESTROYED,
				"Container is destroyed and cannot be started");

		Map<RequestedSecret, LeaseRenewalScheduler> renewals = new HashMap<RequestedSecret, LeaseRenewalScheduler>(
				this.renewals);

		if (UPDATER.compareAndSet(this, STATUS_INITIAL, STATUS_STARTED)) {

			for (Entry<RequestedSecret, LeaseRenewalScheduler> entry : renewals
					.entrySet()) {
				start(entry.getKey(), entry.getValue());
			}
		}
	}

	private void start(RequestedSecret requestedSecret,
			LeaseRenewalScheduler renewalScheduler) {

		VaultResponseSupport<Map<String, Object>> secrets = doGetSecrets(requestedSecret);

		if (secrets != null) {

			Lease lease = !StringUtils.hasText(secrets.getLeaseId()) ? Lease.none()
					: Lease.of(secrets.getLeaseId(), secrets.getLeaseDuration(),
							secrets.isRenewable());

			potentiallyScheduleLeaseRenewal(requestedSecret, lease, renewalScheduler);
			onSecretsObtained(requestedSecret, lease, secrets.getData());
		}
	}

	/**
	 * Stop the {@link SecretLeaseContainer}. Stopping the container will stop lease
	 * renewal, secrets rotation and event publishing. Active leases are not expired.
	 * <p>
	 * Multiple calls are synchronized to stop the container only once.
	 *
	 * @see #start()
	 */
	public void stop() {

		if (UPDATER.compareAndSet(this, STATUS_STARTED, STATUS_INITIAL)) {

			for (LeaseRenewalScheduler leaseRenewal : this.renewals.values()) {
				leaseRenewal.disableScheduleRenewal();
			}
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {

		if (!this.initialized) {

			super.afterPropertiesSet();

			this.initialized = true;

			if (this.taskScheduler == null) {

				ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
				scheduler.setDaemon(true);
				scheduler.setThreadNamePrefix(String.format("%s-%d-",
						getClass().getSimpleName(), poolId.incrementAndGet()));
				scheduler.afterPropertiesSet();

				this.taskScheduler = scheduler;
				this.manageTaskScheduler = true;
			}

			for (RequestedSecret requestedSecret : requestedSecrets) {
				this.renewals.put(requestedSecret,
						new LeaseRenewalScheduler(this.taskScheduler));
			}
		}
	}

	/**
	 * Shutdown this {@link SecretLeaseContainer}, disable lease renewal and revoke
	 * leases.
	 *
	 * @see #afterPropertiesSet()
	 * @see #start()
	 * @see #stop()
	 */
	@Override
	public void destroy() throws Exception {

		int status = this.status;

		if (status == STATUS_INITIAL || status == STATUS_STARTED) {

			if (UPDATER.compareAndSet(this, status, STATUS_DESTROYED)) {

				for (Entry<RequestedSecret, LeaseRenewalScheduler> entry : renewals
						.entrySet()) {

					Lease lease = entry.getValue().getLease();
					entry.getValue().disableScheduleRenewal();

					if (lease != null) {
						doRevokeLease(entry.getKey(), lease);
					}
				}

				if (manageTaskScheduler) {

					if (this.taskScheduler instanceof DisposableBean) {
						((DisposableBean) this.taskScheduler).destroy();
						this.taskScheduler = null;
					}
				}
			}
		}
	}

	void potentiallyScheduleLeaseRenewal(final RequestedSecret requestedSecret,
			final Lease lease, final LeaseRenewalScheduler leaseRenewal) {

		if (leaseRenewal.isLeaseRenewable(lease)) {

			if (log.isDebugEnabled()) {
				log.debug(String.format("Lease %s qualified for renewal",
						lease.getLeaseId()));
			}

			leaseRenewal.scheduleRenewal(new RenewLease() {

				@Override
				public Lease renewLease(Lease lease) {

					Lease newLease = doRenewLease(requestedSecret, lease);

					if (newLease == null) {
						return null;
					}

					potentiallyScheduleLeaseRenewal(requestedSecret, newLease,
							leaseRenewal);

					onAfterLeaseRenewed(requestedSecret, newLease);

					return newLease;
				}
			}, lease, getMinRenewalSeconds(), getExpiryThresholdSeconds());
		}
	}

	// -------------------------------------------------------------------------
	// Implementation hooks and helper methods
	// -------------------------------------------------------------------------

	/**
	 * Retrieve secrets from {@link VaultOperations}.
	 *
	 * @param requestedSecret the {@link RequestedSecret} providing the secret
	 * {@code path}.
	 * @return the response.
	 */
	protected VaultResponseSupport<Map<String, Object>> doGetSecrets(
			RequestedSecret requestedSecret) {

		try {
			return this.operations.read(requestedSecret.getPath());
		}
		catch (RuntimeException e) {

			onError(requestedSecret, Lease.none(), e);
			return null;
		}
	}

	/**
	 * Renew a {@link Lease} for a {@link RequestedSecret}.
	 *
	 * @param requestedSecret the requested secret.
	 * @param lease the lease.
	 * @return the new lease or {@literal null} if expired/secret cannot be rotated.
	 */
	protected Lease doRenewLease(RequestedSecret requestedSecret, final Lease lease) {

		try {
			ResponseEntity<Map<String, Object>> entity = operations.doWithSession(
					new RestOperationsCallback<ResponseEntity<Map<String, Object>>>() {

						@Override
						@SuppressWarnings("unchecked")
						public ResponseEntity<Map<String, Object>> doWithRestOperations(
								RestOperations restOperations) {
							return (ResponseEntity) restOperations.exchange(
									"/sys/renew/{leaseId}", HttpMethod.PUT, null,
									Map.class, lease.getLeaseId());
						}
					});

			Map<String, Object> body = entity.getBody();
			String leaseId = (String) body.get("lease_id");
			Number leaseDuration = (Number) body.get("lease_duration");
			boolean renewable = (Boolean) body.get("renewable");

			if (leaseDuration == null || leaseDuration.intValue() < minRenewalSeconds) {
				onLeaseExpired(requestedSecret, lease);
				return null;
			}

			return Lease.of(leaseId, leaseDuration.longValue(), renewable);
		}
		catch (HttpStatusCodeException e) {

			if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
				onLeaseExpired(requestedSecret, lease);
			}

			onError(requestedSecret, lease,
					new VaultException(String.format("Cannot renew lease: %s",
							VaultResponses.getError(e.getResponseBodyAsString()))));
		}
		catch (RuntimeException e) {
			onError(requestedSecret, lease, e);
		}

		return null;
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

		super.onLeaseExpired(requestedSecret, lease);

		if (requestedSecret.getMode() == Mode.ROTATE) {
			start(requestedSecret, renewals.get(requestedSecret));
		}
	}

	/**
	 * Revoke the {@link Lease}.
	 *
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 */
	protected void doRevokeLease(RequestedSecret requestedSecret, final Lease lease) {

		try {

			onBeforeLeaseRevocation(requestedSecret, lease);

			operations.doWithSession(
					new RestOperationsCallback<ResponseEntity<Map<String, Object>>>() {

						@Override
						@SuppressWarnings("unchecked")
						public ResponseEntity<Map<String, Object>> doWithRestOperations(
								RestOperations restOperations) {
							return (ResponseEntity) restOperations.exchange(
									"/sys/revoke/{leaseId}", HttpMethod.PUT, null,
									Map.class, lease.getLeaseId());
						}
					});

			onAfterLeaseRevocation(requestedSecret, lease);
		}
		catch (HttpStatusCodeException e) {
			onError(requestedSecret, lease,
					new VaultException(String.format("Cannot revoke lease: %s",
							VaultResponses.getError(e.getResponseBodyAsString()))));
		}
		catch (RuntimeException e) {
			onError(requestedSecret, lease, e);
		}
	}

	/**
	 * Abstracts scheduled lease renewal. A {@link LeaseRenewalScheduler} can be accessed
	 * concurrently to schedule lease renewal. Each renewal run checks if the previously
	 * attached {@link Lease} is still relevant to update. If any other process scheduled
	 * a newer {@link Lease} for renewal, the previously registered renewal task will skip
	 * renewal.
	 */
	@CommonsLog
	static class LeaseRenewalScheduler {

		private final TaskScheduler taskScheduler;

		final AtomicReference<Lease> currentLeaseRef = new AtomicReference<Lease>();

		final Map<Lease, ScheduledFuture<?>> schedules = new ConcurrentHashMap<Lease, ScheduledFuture<?>>();

		/**
		 *
		 * @param taskScheduler must not be {@literal null}.
		 */
		LeaseRenewalScheduler(TaskScheduler taskScheduler) {
			this.taskScheduler = taskScheduler;
		}

		/**
		 * Schedule {@link Lease} renewal. Previously registered renewal tasks are
		 * canceled to prevent renewal of stale {@link Lease}s.
		 * @param renewLease strategy to renew a {@link Lease}.
		 * @param lease the current {@link Lease}.
		 * @param minRenewalSeconds minimum number of seconds before renewing a
		 * {@link Lease}. This is to prevent too many renewals in a very short timeframe.
		 * @param expiryThresholdSeconds number of seconds to renew before {@link Lease}.
		 * expires.
		 */
		void scheduleRenewal(final RenewLease renewLease, final Lease lease,
				final int minRenewalSeconds, final int expiryThresholdSeconds) {

			if (log.isDebugEnabled()) {
				log.debug(String.format(
						"Scheduling renewal for lease %s, lease duration %d",
						lease.getLeaseId(), lease.getLeaseDuration()));
			}

			Lease currentLease = this.currentLeaseRef.get();
			this.currentLeaseRef.set(lease);

			if (currentLease != null) {
				cancelSchedule(currentLease);
			}

			ScheduledFuture<?> scheduledFuture = taskScheduler.schedule(new Runnable() {

				@Override
				public void run() {

					try {

						schedules.remove(lease);

						if (currentLeaseRef.get() != lease) {
							log.debug("Current lease has changed. Skipping renewal");
							return;
						}

						if (log.isDebugEnabled()) {
							log.debug(String.format("Renewing lease %s",
									lease.getLeaseId()));
						}

						currentLeaseRef.compareAndSet(lease,
								renewLease.renewLease(lease));
					}
					catch (Exception e) {
						log.error(String.format("Cannot renew lease %s",
								lease.getLeaseId()), e);
					}
				}
			}, new OneShotTrigger(
					getRenewalSeconds(lease, minRenewalSeconds, expiryThresholdSeconds)));

			schedules.put(lease, scheduledFuture);
		}

		private void cancelSchedule(Lease lease) {

			ScheduledFuture<?> scheduledFuture = schedules.get(lease);
			if (scheduledFuture != null) {

				if (log.isDebugEnabled()) {
					log.debug(String.format(
							"Canceling previously registered schedule for lease %s",
							lease.getLeaseId()));
				}

				scheduledFuture.cancel(false);
			}
		}

		/**
		 * Disables schedule for already scheduled renewals.
		 */
		void disableScheduleRenewal() {

			currentLeaseRef.set(null);
			Set<Lease> leases = new HashSet<Lease>(schedules.keySet());

			for (Lease lease : leases) {
				cancelSchedule(lease);
				schedules.remove(lease);
			}
		}

		private long getRenewalSeconds(Lease lease, int minRenewalSeconds,
				int expiryThresholdSeconds) {
			return Math.max(minRenewalSeconds,
					lease.getLeaseDuration() - expiryThresholdSeconds);
		}

		private boolean isLeaseRenewable(Lease lease) {
			return lease != null && lease.isRenewable();
		}

		public Lease getLease() {
			return currentLeaseRef.get();
		}
	}

	/**
	 * This one-shot trigger creates only one execution time to trigger an execution only
	 * once.
	 */
	static class OneShotTrigger implements Trigger {

		private final static AtomicIntegerFieldUpdater<OneShotTrigger> UPDATER = AtomicIntegerFieldUpdater
				.newUpdater(OneShotTrigger.class, "status");

		private final static int STATUS_ARMED = 0;
		private final static int STATUS_FIRED = 1;

		// see AtomicIntegerFieldUpdater UPDATER
		private volatile int status = 0;

		private final long seconds;

		OneShotTrigger(long seconds) {
			this.seconds = seconds;
		}

		@Override
		public Date nextExecutionTime(TriggerContext triggerContext) {

			if (UPDATER.compareAndSet(this, STATUS_ARMED, STATUS_FIRED)) {
				return new Date(
						System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds));
			}

			return null;
		}
	}

	/**
	 * Strategy interface to renew a {@link Lease}.
	 */
	interface RenewLease {

		/**
		 * Renew a lease.
		 *
		 * @param lease must not be {@literal null}.
		 * @return the new lease
		 * @throws VaultException if lease renewal runs into problems
		 */
		Lease renewLease(Lease lease) throws VaultException;
	}
}
