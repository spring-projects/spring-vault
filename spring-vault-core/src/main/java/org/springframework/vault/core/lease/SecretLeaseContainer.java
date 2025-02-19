/*
 * Copyright 2017-2025 the original author or authors.
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.event.AuthenticationErrorEvent;
import org.springframework.vault.authentication.event.AuthenticationErrorListener;
import org.springframework.vault.authentication.event.AuthenticationEvent;
import org.springframework.vault.authentication.event.AuthenticationListener;
import org.springframework.vault.authentication.event.LoginTokenExpiredEvent;
import org.springframework.vault.authentication.event.LoginTokenRenewalFailedEvent;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.core.RestOperationsCallback;
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
 * {@link RequestedSecret#getMode()}. Usage example: <pre>
 * <code>
 * SecretLeaseContainer container = new SecretLeaseContainer(vaultOperations,
 * 		taskScheduler);
 * RequestedSecret requestedSecret = container
 * 		.requestRotatingSecret("mysql/creds/my-role");
 * container.addLeaseListener(new LeaseListenerAdapter() {
 * 	&#64;Override
 * 	public void onLeaseEvent(SecretLeaseEvent secretLeaseEvent) {
 * 		if (requestedSecret == secretLeaseEvent.getSource()) {
 * 			if (secretLeaseEvent instanceof SecretLeaseCreatedEvent) {
 *            }
 * 			if (secretLeaseEvent instanceof SecretLeaseExpiredEvent) {
 *            }
 *        }
 *    }
 * });
 * container.afterPropertiesSet();
 * container.start(); // events are triggered after starting the container
 * </code> </pre>
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
		implements InitializingBean, DisposableBean, SmartLifecycle {

	private static final AtomicIntegerFieldUpdater<SecretLeaseContainer> UPDATER = AtomicIntegerFieldUpdater
		.newUpdater(SecretLeaseContainer.class, "status");

	/**
	 * {@link Predicate} to test whether a {@link Lease} has no lease identifier.
	 */
	public static Predicate<Lease> NO_LEASE_ID = Predicate.not(Lease::hasLeaseId);

	/**
	 * {@link Predicate} to test whether a {@link Lease} has no lease identifier.
	 */
	public static Predicate<Lease> NO_LEASE_DURATION = forDuration(Duration::isZero);

	private static final AtomicInteger poolId = new AtomicInteger();

	private static final int STATUS_INITIAL = 0;

	private static final int STATUS_STARTED = 1;

	private static final int STATUS_DESTROYED = 2;

	@SuppressWarnings("FieldMayBeFinal") // allow setting via reflection.
	private static Log logger = LogFactory.getLog(SecretLeaseContainer.class);

	private final Clock clock = Clock.systemDefaultZone();

	private final LeaseAuthenticationEventListener authenticationListener = new LeaseAuthenticationEventListener();

	private final List<RequestedSecret> requestedSecrets = new CopyOnWriteArrayList<>();

	private final Map<RequestedSecret, LeaseRenewalScheduler> renewals = new ConcurrentHashMap<>();

	private final VaultOperations operations;

	private final KeyValueDelegate keyValueDelegate;

	private LeaseEndpoints leaseEndpoints = LeaseEndpoints.Leases;

	private Duration minRenewal = Duration.ofSeconds(10);

	private @Nullable Predicate<Lease> isExpired;

	private Predicate<Lease> isExpiredFallback = createIsExpiredPredicate(this.minRenewal);

	private Duration expiryThreshold = Duration.ofSeconds(60);

	private LeaseStrategy leaseStrategy = LeaseStrategy.dropOnError();

	@Nullable
	private TaskScheduler taskScheduler;

	private boolean manageTaskScheduler;

	private volatile boolean initialized;

	private volatile int status = STATUS_INITIAL;

	/**
	 * Create a new {@link SecretLeaseContainer} given {@link VaultOperations}.
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
	 * Returns the {@link AuthenticationListener} to listen for login token events.
	 * @return the {@link AuthenticationListener} to listen for login token events.
	 * @since 3.1
	 */
	public AuthenticationListener getAuthenticationListener() {
		return this.authenticationListener;
	}

	/**
	 * Returns the {@link AuthenticationListener} to listen for login token error events.
	 * @return the {@link AuthenticationListener} to listen for login token error events
	 * @since 3.1
	 */
	public AuthenticationErrorListener getAuthenticationErrorListener() {
		return this.authenticationListener;
	}

	/**
	 * Set the {@link LeaseEndpoints} to delegate renewal/revocation calls to.
	 * {@link LeaseEndpoints} encapsulates differences between Vault versions that affect
	 * the location of renewal/revocation endpoints.
	 * @param leaseEndpoints must not be {@literal null}.
	 * @see LeaseEndpoints
	 * @since 2.1
	 */
	public void setLeaseEndpoints(LeaseEndpoints leaseEndpoints) {

		Assert.notNull(leaseEndpoints, "LeaseEndpoints must not be null");

		this.leaseEndpoints = leaseEndpoints;
	}

	/**
	 * Sets the amount {@link Duration} that is at least required before renewing a lease.
	 * {@code minRenewal} prevents renewals from happening too often.
	 * @param minRenewal duration that is at least required before renewing a
	 * {@link Lease}, must not be {@literal null} or negative.
	 * @since 2.0
	 */
	public void setMinRenewal(Duration minRenewal) {

		Assert.notNull(minRenewal, "Minimal renewal time must not be null");
		Assert.isTrue(!minRenewal.isNegative(), "Minimal renewal time must not be negative");

		this.minRenewal = minRenewal;
		this.isExpiredFallback = createIsExpiredPredicate(this.minRenewal);
	}

	/**
	 * Sets the {@link Predicate} to determine whether a {@link Lease} is expired.
	 * Defaults to comparing whether a lease {@link Lease#hasLeaseId() has no identifier},
	 * its remaining TTL is zero or less or equal to {@code minRenewal}.
	 * @since 3.2
	 */
	public void setExpiryPredicate(Predicate<Lease> isExpired) {

		Assert.notNull(isExpired, "Expiry predicate must not be null");

		this.isExpired = isExpired;
	}

	/**
	 * Set the expiry threshold. A {@link Lease} is renewed the given time before it
	 * expires.
	 * @param expiryThreshold duration before {@link Lease} expiry, must not be
	 * {@literal null} or negative.
	 * @since 2.0
	 */
	public void setExpiryThreshold(Duration expiryThreshold) {

		Assert.notNull(expiryThreshold, "Expiry threshold must not be null");
		Assert.isTrue(!expiryThreshold.isNegative(), "Expiry threshold must not be negative");

		this.expiryThreshold = expiryThreshold;
	}

	public int getMinRenewalSeconds() {
		return Math.toIntExact(this.minRenewal.getSeconds());
	}

	/**
	 * @return minimum renewal timeout.
	 * @since 2.0
	 */
	public Duration getMinRenewal() {
		return this.minRenewal;
	}

	public int getExpiryThresholdSeconds() {
		return Math.toIntExact(this.expiryThreshold.getSeconds());
	}

	/**
	 * @return expiry threshold.
	 * @since 2.0
	 */
	public Duration getExpiryThreshold() {
		return this.expiryThreshold;
	}

	/**
	 * Set the {@link LeaseStrategy} for lease renewal error handling.
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
	 * @param taskScheduler must not be {@literal null}.
	 */
	public void setTaskScheduler(TaskScheduler taskScheduler) {

		Assert.notNull(taskScheduler, "TaskScheduler must not be null");
		this.taskScheduler = taskScheduler;
	}

	/**
	 * Request a renewable secret at {@code path}.
	 * @param path must not be {@literal null} or empty.
	 * @return the {@link RequestedSecret}.
	 */
	public RequestedSecret requestRenewableSecret(String path) {
		return addRequestedSecret(RequestedSecret.renewable(path));
	}

	/**
	 * Request a rotating secret at {@code path}.
	 * @param path must not be {@literal null} or empty.
	 * @return the {@link RequestedSecret}.
	 */
	public RequestedSecret requestRotatingSecret(String path) {
		return addRequestedSecret(RequestedSecret.rotating(path));
	}

	/**
	 * Add a {@link RequestedSecret}.
	 * @param requestedSecret must not be {@literal null}.
	 */
	public RequestedSecret addRequestedSecret(RequestedSecret requestedSecret) {

		Assert.notNull(requestedSecret, "RequestedSecret must not be null");

		this.requestedSecrets.add(requestedSecret);

		if (this.initialized) {

			Assert.state(this.taskScheduler != null, "TaskScheduler must not be null");

			LeaseRenewalScheduler leaseRenewalScheduler = new LeaseRenewalScheduler(this.taskScheduler);
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
	@Override
	public void start() {

		Assert.state(this.initialized, "Container is not initialized");
		Assert.state(this.status != STATUS_DESTROYED, "Container is destroyed and cannot be started");

		Map<RequestedSecret, LeaseRenewalScheduler> renewals = new HashMap<>(this.renewals);

		if (UPDATER.compareAndSet(this, STATUS_INITIAL, STATUS_STARTED)) {

			for (Entry<RequestedSecret, LeaseRenewalScheduler> entry : renewals.entrySet()) {
				start(entry.getKey(), entry.getValue());
			}
		}
	}

	private void start(RequestedSecret requestedSecret, LeaseRenewalScheduler renewalScheduler) {

		doStart(requestedSecret, renewalScheduler, (secrets, lease) -> {
			onSecretsObtained(requestedSecret, lease, secrets.getRequiredData());
		}, () -> {
		});
	}

	private void doStart(RequestedSecret requestedSecret, LeaseRenewalScheduler renewalScheduler,
			BiConsumer<VaultResponseSupport<Map<String, Object>>, Lease> callback,
			Runnable cannotObtainSecretsCallback) {

		VaultResponseSupport<Map<String, Object>> secrets = doGetSecrets(requestedSecret);

		if (secrets != null) {

			Lease lease;

			if (StringUtils.hasText(secrets.getLeaseId())) {
				lease = Lease.of(secrets.getLeaseId(), Duration.ofSeconds(secrets.getLeaseDuration()),
						secrets.isRenewable());
			}
			else if (isRotatingGenericSecret(requestedSecret, secrets)) {
				lease = Lease.fromTimeToLive(Duration.ofSeconds(secrets.getLeaseDuration()));
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
			else if (lease.hasLeaseId()) {
				renewalScheduler.associateLease(lease);
			}

			callback.accept(secrets, lease);
		}
		else {
			cannotObtainSecretsCallback.run();
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
	@Override
	public void stop() {

		if (UPDATER.compareAndSet(this, STATUS_STARTED, STATUS_INITIAL)) {

			for (LeaseRenewalScheduler leaseRenewal : this.renewals.values()) {
				leaseRenewal.disableScheduleRenewal();
			}
		}
	}

	@Override
	public boolean isRunning() {
		return UPDATER.get(this) == STATUS_STARTED;
	}

	@Override
	public int getPhase() {
		return 200;
	}

	@Override
	public void afterPropertiesSet() {

		if (this.initialized) {
			return;
		}

		super.afterPropertiesSet();

		this.initialized = true;

		if (this.taskScheduler == null) {

			ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
			scheduler.setDaemon(true);
			scheduler.setThreadNamePrefix("%s-%d-".formatted(getClass().getSimpleName(), poolId.incrementAndGet()));
			scheduler.afterPropertiesSet();

			this.taskScheduler = scheduler;
			this.manageTaskScheduler = true;
		}

		for (RequestedSecret requestedSecret : this.requestedSecrets) {
			this.renewals.put(requestedSecret, new LeaseRenewalScheduler(this.taskScheduler));
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

				for (Entry<RequestedSecret, LeaseRenewalScheduler> entry : this.renewals.entrySet()) {

					Lease lease = entry.getValue().getLease();
					Lease previousLease = entry.getValue().getPreviousLease();
					entry.getValue().disableScheduleRenewal();

					if (lease != null && lease.hasLeaseId()) {
						doRevokeLease(entry.getKey(), lease);
					}

					if (previousLease != null && previousLease.hasLeaseId()) {
						doRevokeLease(entry.getKey(), previousLease);
					}
				}
				this.renewals.clear();

				if (this.manageTaskScheduler) {

					if (this.taskScheduler instanceof DisposableBean) {
						((DisposableBean) this.taskScheduler).destroy();
						this.taskScheduler = null;
					}
				}
			}
		}
	}

	void restartSecrets() {

		Assert.state(this.taskScheduler != null, "TaskScheduler is not set");

		int status = this.status;
		if (status == STATUS_STARTED) {

			logger.debug("Restarting all secrets after token expiry/rotation");

			try {

				Map<RequestedSecret, LeaseRenewalScheduler> previousLeases = new LinkedHashMap<>(this.renewals);
				this.renewals.clear();
				previousLeases.values().forEach(LeaseRenewalScheduler::disableScheduleRenewal);

				for (RequestedSecret requestedSecret : this.requestedSecrets) {

					LeaseRenewalScheduler renewalScheduler = new LeaseRenewalScheduler(this.taskScheduler);
					Lease previousLease = getPreviousLease(previousLeases, requestedSecret);

					try {
						this.renewals.put(requestedSecret, renewalScheduler);
						doStart(requestedSecret, renewalScheduler, (secrets, lease) -> {
							onSecretsRotated(requestedSecret, previousLease, lease, secrets.getRequiredData());
						}, () -> {
						});

					}
					catch (Exception e) {
						onError(requestedSecret, previousLease, e);
					}
				}
			}
			catch (Exception e) {
				logger.error("Cannot restart secrets", e);
			}
		}
	}

	private static Lease getPreviousLease(Map<RequestedSecret, LeaseRenewalScheduler> previousLeases,
			RequestedSecret requestedSecret) {

		LeaseRenewalScheduler leaseRenewalScheduler = previousLeases.get(requestedSecret);
		Lease previousLease = leaseRenewalScheduler != null ? leaseRenewalScheduler.getLease() : null;

		return previousLease == null ? Lease.none() : previousLease;
	}

	/**
	 * Renew a {@link RequestedSecret secret}.
	 * @param secret the {@link RequestedSecret secret}' to renew.
	 * @return {@literal true} if the lease was renewed.
	 * @throws IllegalArgumentException if the {@link RequestedSecret secret} was not
	 * previously {@link #addRequestedSecret(RequestedSecret) registered}.
	 * @throws IllegalStateException if there's no {@link Lease} associated with the
	 * {@link RequestedSecret secret} or the secret is not qualified for renewal.
	 * @since 2.2
	 */
	public boolean renew(RequestedSecret secret) {

		LeaseRenewalScheduler renewalScheduler = getRenewalSchedulder(secret);
		Lease lease = renewalScheduler.getLease();

		if (lease == null) {
			throw new IllegalStateException("No lease associated with secret %s".formatted(secret));
		}

		if (!renewalScheduler.isLeaseRenewable(lease, secret)) {
			throw new IllegalStateException("Secret is not qualified for renewal");
		}

		return renewAndSchedule(secret, renewalScheduler, lease) != lease;
	}

	/**
	 * Rotate a {@link RequestedSecret secret}.
	 * @param secret the {@link RequestedSecret secret}' to rotate.
	 * @throws IllegalArgumentException if the {@link RequestedSecret secret} was not
	 * previously {@link #addRequestedSecret(RequestedSecret) registered}.
	 * @throws IllegalStateException if there's no {@link Lease} associated with the
	 * {@link RequestedSecret secret} or the secret is not qualified for rotation.
	 * @since 2.2
	 */
	public void rotate(RequestedSecret secret) {

		LeaseRenewalScheduler renewalScheduler = getRenewalSchedulder(secret);
		Lease lease = renewalScheduler.getLease();

		if (lease == null) {
			throw new IllegalStateException("No lease associated with secret %s".formatted(secret));
		}

		if (!renewalScheduler.isLeaseRenewable(lease, secret) && !renewalScheduler.isLeaseRotateOnly(lease, secret)) {
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

	private Lease renewAndSchedule(RequestedSecret requestedSecret, LeaseRenewalScheduler leaseRenewal,
			Lease leaseToRenew) {

		Lease newLease = doRenewLease(requestedSecret, leaseToRenew);

		if (!Lease.none().equals(newLease)) {

			scheduleLeaseRenewal(requestedSecret, newLease, leaseRenewal);

			onAfterLeaseRenewed(requestedSecret, newLease);
		}

		return newLease;
	}

	private void scheduleLeaseRotation(RequestedSecret secret, Lease lease, LeaseRenewalScheduler leaseRenewal) {

		logRenewalCandidate(secret, lease, "rotation");

		leaseRenewal.scheduleRenewal(secret, leaseToRotate -> {

			onLeaseExpired(secret, lease);

			return Lease.none(); // rotation creates a new lease.
		}, lease, getMinRenewal(), getExpiryThreshold());
	}

	private LeaseRenewalScheduler getRenewalSchedulder(RequestedSecret secret) {
		LeaseRenewalScheduler renewalScheduler = this.renewals.get(secret);

		if (renewalScheduler == null) {
			throw new IllegalArgumentException("No such secret %s".formatted(secret));
		}
		return renewalScheduler;
	}

	private static void logRenewalCandidate(RequestedSecret requestedSecret, Lease lease, String action) {

		if (logger.isDebugEnabled()) {

			if (lease.hasLeaseId()) {
				logger.debug("Secret %s with Lease %s qualified for %s".formatted(requestedSecret.getPath(),
						lease.getLeaseId(), action));
			}
			else {
				logger.debug(
						"Secret %s with cache hint is qualified for %s".formatted(requestedSecret.getPath(), action));
			}
		}
	}

	// -------------------------------------------------------------------------
	// Implementation hooks and helper methods
	// -------------------------------------------------------------------------

	/**
	 * Retrieve secrets from {@link VaultOperations}.
	 * @param requestedSecret the {@link RequestedSecret} providing the secret
	 * {@code path}.
	 * @return the response.
	 */
	@Nullable
	protected VaultResponseSupport<Map<String, Object>> doGetSecrets(RequestedSecret requestedSecret) {

		try {
			VaultResponseSupport<Map<String, Object>> secrets;

			if (this.keyValueDelegate.isVersioned(requestedSecret.getPath())) {
				secrets = this.keyValueDelegate.getSecret(requestedSecret.getPath());
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
	 * @param requestedSecret the requested secret.
	 * @param lease the lease.
	 * @return the new lease or {@literal null} if expired/secret cannot be rotated.
	 */
	protected Lease doRenewLease(RequestedSecret requestedSecret, Lease lease) {

		try {
			Lease renewed = lease.hasLeaseId() ? doRenew(lease) : lease;

			if (isExpired(renewed)) {

				onLeaseExpired(requestedSecret, lease);
				return Lease.none();
			}

			return renewed;
		}
		catch (RuntimeException e) {

			HttpStatusCodeException httpException = potentiallyUnwrapHttpStatusCodeException(e);

			boolean expired = false;
			Exception exceptionToUse;
			if (httpException != null) {

				if (httpException.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()) {
					expired = true;
					onLeaseExpired(requestedSecret, lease);
				}

				exceptionToUse = new VaultException("Cannot renew lease: Status %s %s %s".formatted(
						httpException.getStatusCode().value(), httpException.getStatusText(),
						VaultResponses.getError(httpException.getResponseBodyAsString())), e);
			}
			else {
				exceptionToUse = new VaultException("Cannot renew lease", e);
			}

			onError(requestedSecret, lease, exceptionToUse);

			if (expired || this.leaseStrategy.shouldDrop(exceptionToUse)) {
				return Lease.none();
			}
			else {
				return lease;
			}
		}
	}

	boolean isExpired(Lease lease) {
		return isExpired == null ? isExpiredFallback.test(lease) : isExpired.test(lease);
	}

	@Nullable
	private HttpStatusCodeException potentiallyUnwrapHttpStatusCodeException(RuntimeException e) {

		if (e instanceof HttpStatusCodeException) {
			return (HttpStatusCodeException) e;
		}

		if (e.getCause() instanceof HttpStatusCodeException) {
			return (HttpStatusCodeException) e.getCause();
		}

		return null;
	}

	private Lease doRenew(Lease lease) {

		return this.operations.doWithSession(restOperations -> this.leaseEndpoints.renew(lease, restOperations));
	}

	/**
	 * Hook method called when a {@link Lease} expires. The default implementation is to
	 * notify {@link LeaseListener}. Implementations can override this method in
	 * subclasses.
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 */
	@Override
	protected void onLeaseExpired(RequestedSecret requestedSecret, Lease lease) {

		if (requestedSecret.getMode() == Mode.ROTATE) {
			LeaseRenewalScheduler renewalScheduler = this.renewals.get(requestedSecret);
			// prevent races for concurrent renewals of the same secret using different
			// leases
			if (renewalScheduler == null || !renewalScheduler.leaseEquals(lease)) {
				logger.debug(
						"Skipping rotation after renewal expiry for secret %s with lease %s as no LeaseRenewalScheduler is found. This can happen if leases have been restarted while concurrent expiry processing."
							.formatted(requestedSecret.getPath(), lease.getLeaseId()));

				super.onLeaseExpired(requestedSecret, lease);
				return;
			}

			doStart(requestedSecret, renewalScheduler, (secrets, currentLease) -> {
				onSecretsRotated(requestedSecret, lease, currentLease, secrets.getRequiredData());
			}, () -> super.onLeaseExpired(requestedSecret, lease));
		}
		else {
			super.onLeaseExpired(requestedSecret, lease);
		}
	}

	/**
	 * Revoke the {@link Lease}.
	 * @param requestedSecret must not be {@literal null}.
	 * @param lease must not be {@literal null}.
	 */
	@SuppressWarnings("NullAway")
	protected void doRevokeLease(RequestedSecret requestedSecret, Lease lease) {

		try {

			onBeforeLeaseRevocation(requestedSecret, lease);

			this.operations.doWithSession((RestOperationsCallback<@Nullable Void>) restOperations -> {
				this.leaseEndpoints.revoke(lease, restOperations);
				return null;
			});

			onAfterLeaseRevocation(requestedSecret, lease);
		}
		catch (HttpStatusCodeException e) {
			onError(requestedSecret, lease, new VaultException(
					"Cannot revoke lease: %s".formatted(VaultResponses.getError(e.getResponseBodyAsString()))));
		}
		catch (RuntimeException e) {
			onError(requestedSecret, lease, e);
		}
	}

	private Predicate<Lease> createIsExpiredPredicate(Duration minRenewal) {
		return NO_LEASE_ID.or(NO_LEASE_DURATION).or(forDuration(isLessOrEqual(minRenewal)));
	}

	private static <T extends Comparable<T>> Predicate<T> isLessOrEqual(T other) {
		return it -> it.compareTo(other) <= 0;
	}

	private static Predicate<Lease> forDuration(Predicate<Duration> predicate) {
		return lease -> predicate.test(lease.getLeaseDuration());
	}

	/**
	 * Abstracts scheduled lease renewal. A {@link LeaseRenewalScheduler} can be accessed
	 * concurrently to schedule lease renewal. Each renewal run checks if the previously
	 * attached {@link Lease} is still relevant to update. If any other process scheduled
	 * a newer {@link Lease} for renewal, the previously registered renewal task will skip
	 * renewal.
	 */
	static class LeaseRenewalScheduler {

		private static final AtomicReferenceFieldUpdater<LeaseRenewalScheduler, Lease> CURRENT_UPDATER = AtomicReferenceFieldUpdater
			.newUpdater(LeaseRenewalScheduler.class, Lease.class, "currentLeaseRef");

		@SuppressWarnings("FieldMayBeFinal") // allow setting via reflection.
		private static Log logger = LogFactory.getLog(LeaseRenewalScheduler.class);

		private final TaskScheduler taskScheduler;

		@Nullable
		volatile Lease currentLeaseRef;

		@Nullable
		volatile Lease previousLeaseRef;

		final Map<Lease, ScheduledFuture<?>> schedules = new ConcurrentHashMap<>();

		/**
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
		 * prevent too many renewals in a very short timeframe.
		 * @param expiryThreshold duration to renew before {@link Lease}.
		 */
		void scheduleRenewal(RequestedSecret requestedSecret, RenewLease renewLease, Lease lease, Duration minRenewal,
				Duration expiryThreshold) {

			if (logger.isDebugEnabled()) {
				if (lease.hasLeaseId()) {
					logger.debug("Scheduling renewal for secret %s with lease %s, lease duration %d".formatted(
							requestedSecret.getPath(), lease.getLeaseId(), lease.getLeaseDuration().getSeconds()));
				}
				else {
					logger.debug("Scheduling renewal for secret %s, with cache hint duration %d"
						.formatted(requestedSecret.getPath(), lease.getLeaseDuration().getSeconds()));
				}
			}

			Lease currentLease = CURRENT_UPDATER.get(this);
			CURRENT_UPDATER.set(this, lease);

			if (currentLease != null) {
				cancelSchedule(currentLease);
			}

			Runnable task = new Runnable() {

				@Override
				public void run() {

					LeaseRenewalScheduler.this.schedules.remove(lease);

					if (CURRENT_UPDATER.get(LeaseRenewalScheduler.this) != lease) {
						logger.debug("Current lease has changed. Skipping renewal");
						return;
					}

					if (logger.isDebugEnabled()) {
						if (lease.hasLeaseId()) {
							logger.debug("Renewing lease %s for secret %s".formatted(lease.getLeaseId(),
									requestedSecret.getPath()));
						}
						else {
							logger.debug("Renewing secret without lease %s".formatted(requestedSecret.getPath()));
						}
					}

					try {

						// Renew lease may call scheduleRenewal(…) with a different lease
						// Id to alter set up its own renewal schedule. If it's the old
						// lease, then renewLease() outcome controls the current LeaseId.
						CURRENT_UPDATER.compareAndSet(LeaseRenewalScheduler.this, lease, renewLease.renewLease(lease));
					}
					catch (Exception e) {
						logger.error("Cannot renew lease %s".formatted(lease.getLeaseId()), e);
					}
				}
			};

			ScheduledFuture<?> scheduledFuture = this.taskScheduler.schedule(task,
					new OneShotTrigger(getRenewalSeconds(lease, minRenewal, expiryThreshold)));

			this.schedules.put(lease, scheduledFuture);
		}

		void associateLease(Lease lease) {
			CURRENT_UPDATER.set(this, lease);
		}

		private void cancelSchedule(Lease lease) {

			ScheduledFuture<?> scheduledFuture = this.schedules.get(lease);
			if (scheduledFuture != null) {

				if (logger.isDebugEnabled()) {
					logger.debug("Canceling previously registered schedule for lease %s".formatted(lease.getLeaseId()));
				}

				scheduledFuture.cancel(false);
			}
		}

		/**
		 * Disables schedule for already scheduled renewals.
		 */
		void disableScheduleRenewal() {

			// capture the previous lease to revoke it
			this.previousLeaseRef = CURRENT_UPDATER.getAndSet(this, null);
			Set<Lease> leases = new HashSet<>(this.schedules.keySet());

			for (Lease lease : leases) {
				cancelSchedule(lease);
				this.schedules.remove(lease);
			}
		}

		private long getRenewalSeconds(Lease lease, Duration minRenewal, Duration expiryThreshold) {
			return Math.max(minRenewal.getSeconds(),
					lease.getLeaseDuration().getSeconds() - expiryThreshold.getSeconds());
		}

		private boolean isLeaseRenewable(@Nullable Lease lease, RequestedSecret requestedSecret) {

			if (lease == null) {
				return false;
			}

			if (lease.isRenewable()) {
				return true;
			}

			if (!lease.hasLeaseId() && !lease.getLeaseDuration().isZero() && requestedSecret.getMode() == Mode.ROTATE) {
				return true;
			}

			return false;
		}

		@Nullable
		public Lease getLease() {
			return CURRENT_UPDATER.get(this);
		}

		@Nullable
		public Lease getPreviousLease() {
			return this.previousLeaseRef;
		}

		private boolean isLeaseRotateOnly(Lease lease, RequestedSecret requestedSecret) {

			if (lease == null) {
				return false;
			}

			return lease.hasLeaseId() && !lease.getLeaseDuration().isZero() && !lease.isRenewable()
					&& requestedSecret.getMode() == Mode.ROTATE;
		}

		boolean leaseEquals(Lease lease) {
			return getLease() == lease;
		}

	}

	private class LeaseAuthenticationEventListener implements AuthenticationListener, AuthenticationErrorListener {

		private final BackOff backOff = new ExponentialBackOff(500, 1.5);

		private final AtomicReference<Timeout> timeout = new AtomicReference<>();

		@Override
		public void onAuthenticationError(AuthenticationErrorEvent authenticationEvent) {
			if (authenticationEvent instanceof LoginTokenRenewalFailedEvent) {
				logger.debug("LoginTokenRenewalFailedEvent received");
				restartSecrets();
			}
		}

		@Override
		public void onAuthenticationEvent(AuthenticationEvent leaseEvent) {
			if (leaseEvent instanceof LoginTokenExpiredEvent) {
				logger.debug("LoginTokenExpiredEvent received");
				restartSecrets();
			}
		}

		/**
		 * Restart secrets after a changed token. Either the token was rotated or it has
		 * expired.
		 */
		private void restartSecrets() {

			Assert.state(taskScheduler != null, "TaskScheduler is not set");

			if (!isRunning()) {
				logger.debug("Ignore token event as the container is not running");
			}

			Timeout timeout = this.timeout.get();
			if (timeout != null && !timeout.isExpired(clock)) {
				logger.debug("Backoff timeout not reached. Dropping event");
				return;
			}

			Timeout executionToSet = new Timeout(backOff.start(), clock);

			if (this.timeout.compareAndSet(timeout, executionToSet)) {

				if (taskScheduler instanceof Executor e) {
					e.execute(SecretLeaseContainer.this::restartSecrets);
				}
				else {
					taskScheduler.schedule(SecretLeaseContainer.this::restartSecrets, Instant.now());
				}
			}
		}

		record Timeout(BackOffExecution execution, long timeout) {

			public Timeout(BackOffExecution execution, Clock clock) {
				this(execution, clock.millis() + execution.nextBackOff());
			}

			public boolean isExpired(Clock clock) {
				return clock.millis() > timeout;
			}
		}

	}

	/**
	 * This one-shot trigger creates only one execution time to trigger an execution only
	 * once.
	 */
	static class OneShotTrigger implements Trigger {

		private static final Clock CLOCK = Clock.systemDefaultZone();

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

		@Nullable
		@Override
		public Instant nextExecution(TriggerContext triggerContext) {
			return UPDATER.compareAndSet(this, STATUS_ARMED, STATUS_FIRED) ? CLOCK.instant().plusSeconds(this.seconds)
					: null;
		}

	}

	/**
	 * Strategy interface to renew a {@link Lease}.
	 */
	interface RenewLease {

		/**
		 * Renew a lease.
		 * @param lease must not be {@literal null}.
		 * @return the new lease.
		 * @throws VaultException if lease renewal runs into problems
		 */
		Lease renewLease(Lease lease) throws VaultException;

	}

}
