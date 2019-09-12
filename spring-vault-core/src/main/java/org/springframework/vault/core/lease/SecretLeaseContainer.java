/*
 * Copyright 2017-2019 the original author or authors.
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

import java.time.Duration;
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.domain.RequestedSecret.Mode;
import org.springframework.vault.core.lease.event.LeaseErrorListener;
import org.springframework.vault.core.lease.event.LeaseListener;
import org.springframework.vault.core.util.KeyValueDelegate;
import org.springframework.vault.support.LeaseStrategy;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Event-based container to request secrets from Vault and renew the associated
 * {@link Lease}. Secrets can be rotated, depending on the requested
 * {@link RequestedSecret#getMode()}.
 *
 * Usage example:
 *
 * <pre>
 * <code>
 * SecretLeaseContainer container = new SecretLeaseContainer(vaultOperations,
 * 		taskScheduler);
 *
 * RequestedSecret requestedSecret = container
 * 		.requestRotatingSecret("mysql/creds/my-role");
 * container.addLeaseListener(new LeaseListenerAdapter() {
 * 	&#64;Override
 * 	public void onLeaseEvent(SecretLeaseEvent secretLeaseEvent) {
 *
 * 		if (requestedSecret == secretLeaseEvent.getSource()) {
 *
 * 			if (secretLeaseEvent instanceof SecretLeaseCreatedEvent) {
 *
 * 			}
 *
 * 			if (secretLeaseEvent instanceof SecretLeaseExpiredEvent) {
 *
 * 			}
 * 		}
 * 	}
 * });
 *
 * container.afterPropertiesSet();
 * container.start(); // events are triggered after starting the container
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
 * @author Steven Swor
 * @author Erik Lindblom
 * @see RequestedSecret
 * @see SecretLeaseEventPublisher
 * @see Lease
 * @see LeaseEndpoints
 * @see LeaseStrategy
 */
public class SecretLeaseContainer extends SecretLeaseEventPublisher
		implements InitializingBean, DisposableBean {

	private static final AtomicIntegerFieldUpdater<SecretLeaseContainer> UPDATER = AtomicIntegerFieldUpdater
			.newUpdater(SecretLeaseContainer.class, "status");

	private static final AtomicInteger poolId = new AtomicInteger();

	private static final int STATUS_INITIAL = 0;
	private static final int STATUS_STARTED = 1;
	private static final int STATUS_DESTROYED = 2;
	private static final Log log = LogFactory.getLog(SecretLeaseContainer.class);

	private final List<RequestedSecret> requestedSecrets = new CopyOnWriteArrayList<>();

	private final Map<RequestedSecret, LeaseRenewalScheduler> renewals = new ConcurrentHashMap<>();

	private final VaultOperations operations;

	private final KeyValueDelegate keyValueDelegate;

	private LeaseEndpoints leaseEndpoints = LeaseEndpoints.Legacy;

	private Duration minRenewal = Duration.ofSeconds(10);

	private Duration expiryThreshold = Duration.ofSeconds(60);

	private LeaseStrategy leaseStrategy = LeaseStrategy.dropOnError();

	@Nullable
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
		this.keyValueDelegate = new KeyValueDelegate(this.operations);
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
		this.keyValueDelegate = new KeyValueDelegate(this.operations);
		setTaskScheduler(taskScheduler);
	}

	/**
	 * Set the {@link LeaseEndpoints} to delegate renewal/revocation calls to.
	 * {@link LeaseEndpoints} encapsulates differences between Vault versions that affect
	 * the location of renewal/revocation endpoints.
	 *
	 * @param leaseEndpoints must not be {@literal null}.
	 * @since 2.1
	 * @see LeaseEndpoints
	 */
	public void setLeaseEndpoints(LeaseEndpoints leaseEndpoints) {

		Assert.notNull(leaseEndpoints, "LeaseEndpoints must not be null");

		this.leaseEndpoints = leaseEndpoints;
	}

	/**
	 * Sets the amount of seconds that is at least required before renewing a lease.
	 * {@code minRenewalSeconds} prevents renewals from happening too often.
	 *
	 * @param minRenewalSeconds number of seconds that is at least required before
	 *     renewing a {@link Lease}, must not be negative.
	 * @deprecated since 2.0, use {@link #setMinRenewal(Duration)} for time unit safety.
	 */
	@Deprecated
	public void setMinRenewalSeconds(int minRenewalSeconds) {
		setMinRenewal(Duration.ofSeconds(minRenewalSeconds));
	}

	/**
	 * Sets the amount {@link Duration} that is at least required before renewing a lease.
	 * {@code minRenewal} prevents renewals from happening too often.
	 *
	 * @param minRenewal duration that is at least required before renewing a
	 *     {@link Lease}, must not be {@literal null} or negative.
	 * @since 2.0
	 */
	public void setMinRenewal(Duration minRenewal) {

		Assert.notNull(minRenewal, "Minimal renewal time must not be null");
		Assert.isTrue(!minRenewal.isNegative(),
				"Minimal renewal time must not be negative");

		this.minRenewal = minRenewal;
	}

	/**
	 * Set the expiry threshold. A {@link Lease} is renewed the given seconds before it
	 * expires.
	 *
	 * @param expiryThresholdSeconds number of seconds before {@link Lease} expiry, must
	 *     not be negative.
	 * @deprecated since 2.0, use {@link #setExpiryThreshold(Duration)} for time unit
	 * safety.
	 */
	@Deprecated
	public void setExpiryThresholdSeconds(int expiryThresholdSeconds) {
		setExpiryThreshold(Duration.ofSeconds(expiryThresholdSeconds));
	}

	/**
	 * Set the expiry threshold. A {@link Lease} is renewed the given time before it
	 * expires.
	 *
	 * @param expiryThreshold duration before {@link Lease} expiry, must not be
	 *     {@literal null} or negative.
	 * @since 2.0
	 */
	public void setExpiryThreshold(Duration expiryThreshold) {

		Assert.notNull(expiryThreshold, "Expiry threshold must not be null");
		Assert.isTrue(!expiryThreshold.isNegative(),
				"Expiry threshold must not be negative");

		this.expiryThreshold = expiryThreshold;
	}

	public int getMinRenewalSeconds() {
		return Math.toIntExact(minRenewal.getSeconds());
	}

	/**
	 * @return minimum renewal timeout.
	 * @since 2.0
	 */
	public Duration getMinRenewal() {
		return minRenewal;
	}

	public int getExpiryThresholdSeconds() {
		return Math.toIntExact(expiryThreshold.getSeconds());
	}

	/**
	 * @return expiry threshold.
	 * @since 2.0
	 */
	public Duration getExpiryThreshold() {
		return expiryThreshold;
	}

	/**
	 * Set the {@link LeaseStrategy} for lease renewal error handling.
	 *
	 * @param leaseStrategy the {@link LeaseStrategy}, must not be {@literal null}.
	 * @since 2.2
	 */
	public void setLeaseStrategy(LeaseStrategy leaseStrategy) {

		Assert.notNull(leaseStrategy, "LeaseStrategy must not be null");
		this.leaseStrategy = leaseStrategy;
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

			Assert.state(this.taskScheduler != null, "TaskScheduler must not be null");

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

		Map<RequestedSecret, LeaseRenewalScheduler> renewals = new HashMap<>(
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

			Lease lease;

			if (StringUtils.hasText(secrets.getLeaseId())) {
				lease = Lease.of(secrets.getLeaseId(),
						Duration.ofSeconds(secrets.getLeaseDuration()),
						secrets.isRenewable());
			}
			else if (isRotatingGenericSecret(requestedSecret, secrets)) {
				lease = Lease
						.fromTimeToLive(Duration.ofSeconds(secrets.getLeaseDuration()));
			}
			else {
				lease = Lease.none();
			}

			if (renewalScheduler.isLeaseRenewable(lease, requestedSecret)) {
				scheduleLeaseRenewal(requestedSecret, lease, renewalScheduler);
			}
			else if (renewalScheduler.isLeaseRotateOnly(lease, requestedSecret)) {
				scheduleLeaseRotation(requestedSecret, lease, renewalScheduler);
			}

			onSecretsObtained(requestedSecret, lease, secrets.getRequiredData());
		}
	}

	private static boolean isRotatingGenericSecret(RequestedSecret requestedSecret,
			VaultResponseSupport<Map<String, Object>> secrets) {

		return Mode.ROTATE.equals(requestedSecret.getMode()) && !secrets.isRenewable()
				&& secrets.getLeaseDuration() > 0;
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

					if (lease != null && lease.hasLeaseId()) {
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

	/**
	 * Renew a {@link RequestedSecret secret}.
	 *
	 * @param secret the {@link RequestedSecret secret}' to renew.
	 * @return {@literal true} if the lease was renewed.
	 * @throws IllegalArgumentException if the {@link RequestedSecret secret} was not
	 *     previously {@link #addRequestedSecret(RequestedSecret) registered}.
	 * @throws IllegalStateException if there's no {@link Lease} associated with the
	 *     {@link RequestedSecret secret} or the secret is not qualified for renewal.
	 * @since 2.2
	 */
	public boolean renew(RequestedSecret secret) {

		LeaseRenewalScheduler renewalScheduler = getRenewalSchedulder(secret);
		Lease lease = renewalScheduler.getLease();

		if (lease == null) {
			throw new IllegalStateException(
					String.format("No lease associated with secret %s", secret));
		}

		if (!renewalScheduler.isLeaseRenewable(lease, secret)) {
			throw new IllegalStateException("Secret is not qualified for renewal");
		}

		return renewAndSchedule(secret, renewalScheduler, lease) != lease;
	}

	/**
	 * Rotate a {@link RequestedSecret secret}.
	 *
	 * @param secret the {@link RequestedSecret secret}' to rotate.
	 * @throws IllegalArgumentException if the {@link RequestedSecret secret} was not
	 *     previously {@link #addRequestedSecret(RequestedSecret) registered}.
	 * @throws IllegalStateException if there's no {@link Lease} associated with the
	 *     {@link RequestedSecret secret} or the secret is not qualified for rotation.
	 * @since 2.2
	 */
	public void rotate(RequestedSecret secret) {

		LeaseRenewalScheduler renewalScheduler = getRenewalSchedulder(secret);
		Lease lease = renewalScheduler.getLease();

		if (lease == null) {
			throw new IllegalStateException(
					String.format("No lease associated with secret %s", secret));
		}

		if (!renewalScheduler.isLeaseRenewable(lease, secret)
				&& !renewalScheduler.isLeaseRotateOnly(lease, secret)) {
			throw new IllegalStateException("Secret is not qualified for rotation");
		}

		onLeaseExpired(secret, lease);
	}

	private void scheduleLeaseRenewal(RequestedSecret requestedSecret, Lease lease,
			LeaseRenewalScheduler leaseRenewal) {

		logRenewalCandidate(requestedSecret, lease, "renewal");

		leaseRenewal.scheduleRenewal(requestedSecret, leaseToRenew -> {

			return renewAndSchedule(requestedSecret, leaseRenewal, leaseToRenew);
		}, lease, getMinRenewal(), getExpiryThreshold());

	}

	private Lease renewAndSchedule(RequestedSecret requestedSecret,
			LeaseRenewalScheduler leaseRenewal, Lease leaseToRenew) {

		Lease newLease = doRenewLease(requestedSecret, leaseToRenew);

		if (!Lease.none().equals(newLease)) {

			scheduleLeaseRenewal(requestedSecret, newLease, leaseRenewal);

			onAfterLeaseRenewed(requestedSecret, newLease);
		}

		return newLease;
	}

	private void scheduleLeaseRotation(RequestedSecret secret, Lease lease,
			LeaseRenewalScheduler leaseRenewal) {

		logRenewalCandidate(secret, lease, "rotation");

		leaseRenewal.scheduleRenewal(secret, leaseToRotate -> {

			onLeaseExpired(secret, lease);

			return Lease.none(); // rotation creates a new lease.
		}, lease, getMinRenewal(), getExpiryThreshold());
	}

	private LeaseRenewalScheduler getRenewalSchedulder(RequestedSecret secret) {
		LeaseRenewalScheduler renewalScheduler = this.renewals.get(secret);

		if (renewalScheduler == null) {
			throw new IllegalArgumentException(
					String.format("No such secret %s", secret));
		}
		return renewalScheduler;
	}

	private static void logRenewalCandidate(RequestedSecret requestedSecret, Lease lease,
			String action) {

		if (log.isDebugEnabled()) {

			if (lease.hasLeaseId()) {
				log.debug(String.format("Secret %s with Lease %s qualified for %s",
						requestedSecret.getPath(), lease.getLeaseId(), action));
			}
			else {
				log.debug(String.format("Secret %s with cache hint is qualified for %s",
						requestedSecret.getPath(), action));
			}
		}
	}

	// -------------------------------------------------------------------------
	// Implementation hooks and helper methods
	// -------------------------------------------------------------------------

	/**
	 * Retrieve secrets from {@link VaultOperations}.
	 *
	 * @param requestedSecret the {@link RequestedSecret} providing the secret
	 *     {@code path}.
	 * @return the response.
	 */
	@Nullable
	protected VaultResponseSupport<Map<String, Object>> doGetSecrets(
			RequestedSecret requestedSecret) {

		try {
			VaultResponseSupport<Map<String, Object>> secrets;

			if (keyValueDelegate.isVersioned(requestedSecret.getPath())) {
				secrets = keyValueDelegate.getSecret(requestedSecret.getPath());
			}
			else {
				secrets = this.operations.read(requestedSecret.getPath());
			}

			if (secrets == null) {
				onSecretsNotFound(requestedSecret);
			}

			return secrets;
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
	protected Lease doRenewLease(RequestedSecret requestedSecret, Lease lease) {

		try {
			Lease renewed = lease.hasLeaseId() ? doRenew(lease) : lease;

			if (!renewed.hasLeaseId() || renewed.getLeaseDuration().isZero() || renewed
					.getLeaseDuration().getSeconds() < minRenewal.getSeconds()) {

				onLeaseExpired(requestedSecret, lease);
				return Lease.none();
			}

			return renewed;
		}
		catch (RuntimeException e) {

			HttpStatusCodeException httpException = potentiallyUnwrapHttpStatusCodeException(
					e);

			boolean expired = false;
			Exception exceptionToUse;
			if (httpException != null) {

				if (httpException.getStatusCode() == HttpStatus.BAD_REQUEST) {
					expired = true;
					onLeaseExpired(requestedSecret, lease);
				}

				exceptionToUse = new VaultException(String.format(
						"Cannot renew lease: Status %s %s%s",
						httpException.getRawStatusCode(), httpException.getStatusText(),
						VaultResponses.getError(httpException.getResponseBodyAsString())),
						e);
			}
			else {
				exceptionToUse = new VaultException("Cannot renew lease", e);
			}

			onError(requestedSecret, lease, exceptionToUse);

			if (expired || leaseStrategy.shouldDrop(exceptionToUse)) {
				return Lease.none();
			}
			else {
				return lease;
			}
		}
	}

	@Nullable
	private HttpStatusCodeException potentiallyUnwrapHttpStatusCodeException(
			RuntimeException e) {

		if (e instanceof HttpStatusCodeException) {
			return (HttpStatusCodeException) e;
		}

		if (e.getCause() instanceof HttpStatusCodeException) {
			return (HttpStatusCodeException) e.getCause();
		}

		return null;
	}

	private Lease doRenew(Lease lease) {

		return operations.doWithSession(
				restOperations -> leaseEndpoints.renew(lease, restOperations));
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
	@SuppressWarnings("unchecked")
	protected void doRevokeLease(RequestedSecret requestedSecret, Lease lease) {

		try {

			onBeforeLeaseRevocation(requestedSecret, lease);

			operations.doWithSession(restOperations -> {
				leaseEndpoints.revoke(lease, restOperations);
				return null;
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
	static class LeaseRenewalScheduler {

		private static final Log log = org.apache.commons.logging.LogFactory
				.getLog(LeaseRenewalScheduler.class);
		private final TaskScheduler taskScheduler;

		final AtomicReference<Lease> currentLeaseRef = new AtomicReference<>();

		final Map<Lease, ScheduledFuture<?>> schedules = new ConcurrentHashMap<>();

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
		 * @param requestedSecret the requested secret.
		 * @param renewLease strategy to renew a {@link Lease}.
		 * @param lease the current {@link Lease}.
		 * @param minRenewal minimum duration before renewing a {@link Lease}. This is to
		 *     prevent too many renewals in a very short timeframe.
		 * @param expiryThreshold duration to renew before {@link Lease}.
		 */
		void scheduleRenewal(RequestedSecret requestedSecret, RenewLease renewLease,
				Lease lease, Duration minRenewal, Duration expiryThreshold) {

			if (log.isDebugEnabled()) {
				if (lease.hasLeaseId()) {
					log.debug(String.format(
							"Scheduling renewal for secret %s with lease %s, lease duration %d",
							requestedSecret.getPath(), lease.getLeaseId(),
							lease.getLeaseDuration().getSeconds()));
				}
				else {
					log.debug(String.format(
							"Scheduling renewal for secret %s, with cache hint duration %d",
							requestedSecret.getPath(),
							lease.getLeaseDuration().getSeconds()));
				}
			}

			Lease currentLease = this.currentLeaseRef.get();
			this.currentLeaseRef.set(lease);

			if (currentLease != null) {
				cancelSchedule(currentLease);
			}

			Runnable task = new Runnable() {

				@Override
				public void run() {

					schedules.remove(lease);

					if (currentLeaseRef.get() != lease) {
						log.debug("Current lease has changed. Skipping renewal");
						return;
					}

					if (log.isDebugEnabled()) {
						if (lease.hasLeaseId()) {
							log.debug(String.format("Renewing lease %s for secret %s",
									lease.getLeaseId(), requestedSecret.getPath()));
						}
						else {
							log.debug(String.format("Renewing secret without lease %s",
									requestedSecret.getPath()));
						}
					}

					try {

						// Renew lease may call scheduleRenewal(…) with a different lease
						// Id to alter set up its own renewal schedule. If it's the old
						// lease, then renewLease() outcome controls the current LeaseId.
						currentLeaseRef.compareAndSet(lease,
								renewLease.renewLease(lease));
					}
					catch (Exception e) {
						log.error(String.format("Cannot renew lease %s",
								lease.getLeaseId()), e);
					}
				}
			};

			ScheduledFuture<?> scheduledFuture = taskScheduler.schedule(task,
					new OneShotTrigger(
							getRenewalSeconds(lease, minRenewal, expiryThreshold)));

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
			Set<Lease> leases = new HashSet<>(schedules.keySet());

			for (Lease lease : leases) {
				cancelSchedule(lease);
				schedules.remove(lease);
			}
		}

		private long getRenewalSeconds(Lease lease, Duration minRenewal,
				Duration expiryThreshold) {
			return Math.max(minRenewal.getSeconds(),
					lease.getLeaseDuration().getSeconds() - expiryThreshold.getSeconds());
		}

		private boolean isLeaseRenewable(@Nullable Lease lease,
				RequestedSecret requestedSecret) {

			if (lease == null) {
				return false;
			}

			if (lease.isRenewable()) {
				return true;
			}

			if (!lease.hasLeaseId() && requestedSecret.getMode() == Mode.ROTATE) {
				return true;
			}

			return false;
		}

		@Nullable
		public Lease getLease() {
			return currentLeaseRef.get();
		}

		private boolean isLeaseRotateOnly(Lease lease, RequestedSecret requestedSecret) {

			if (lease == null) {
				return false;
			}

			return lease.hasLeaseId() && !lease.isRenewable()
					&& requestedSecret.getMode() == Mode.ROTATE;
		}
	}

	/**
	 * This one-shot trigger creates only one execution time to trigger an execution only
	 * once.
	 */
	static class OneShotTrigger implements Trigger {

		private static final AtomicIntegerFieldUpdater<OneShotTrigger> UPDATER = AtomicIntegerFieldUpdater
				.newUpdater(OneShotTrigger.class, "status");

		private static final int STATUS_ARMED = 0;
		private static final int STATUS_FIRED = 1;

		// see AtomicIntegerFieldUpdater UPDATER
		private volatile int status = 0;

		private final long seconds;

		OneShotTrigger(long seconds) {
			this.seconds = seconds;
		}

		@Override
		@Nullable
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
		 * @return the new lease.
		 * @throws VaultException if lease renewal runs into problems
		 */
		Lease renewLease(Lease lease) throws VaultException;
	}
}
