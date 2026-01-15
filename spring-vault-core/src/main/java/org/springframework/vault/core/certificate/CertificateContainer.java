/*
 * Copyright 2025-present the original author or authors.
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

package org.springframework.vault.core.certificate;

import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.format.annotation.DurationFormat.Style;
import org.springframework.format.datetime.standard.DurationFormatterUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.core.VaultPkiOperations;
import org.springframework.vault.core.certificate.domain.RequestedCertificate;
import org.springframework.vault.core.certificate.domain.RequestedCertificateBundle;
import org.springframework.vault.core.certificate.domain.RequestedTrustAnchor;
import org.springframework.vault.core.certificate.event.CertificateErrorListener;
import org.springframework.vault.core.certificate.event.CertificateListener;
import org.springframework.vault.support.Certificate;

/**
 * Event-based container to request certificates from Vault's PKI engine and
 * rotate these on expiry. Usage example: <pre class="code">
 * CertificateContainer container = new CertificateContainer(vaultOperations.opsForPki());
 * RequestedCertificate cert = container
 * 		.register(RequestedCertificate.trustAnchor("vault-ca"));
 * container.addCertificateListener(new CertificateListenerrAdapter() {
 * 	&#64;Override
 * 	public void onCertificateEvent(CertificateEvent event) {
 * 		if (cert == event.getSource()) {
 * 			if (event instanceof CertificateObtainedEvent) {
 * 				// initial certificate obtained
 *			}
 * 		}
 * 	}
 * });
 * container.afterPropertiesSet();
 * container.start(); // events are triggered after starting the container
 * </pre>
 * <p>This container keeps track over {@link RequestedCertificate}s and
 * obtains/issues certificates upon {@link #start()}.
 * <p>The container dispatches certificate events to {@link CertificateListener}
 * and {@link CertificateErrorListener}. Event notifications are dispatched
 * either on the {@link #start() starting} {@link Thread} or worker threads used
 * for background renewal.
 * <p>Instances are thread-safe once {@link #afterPropertiesSet() initialized}.
 *
 * @author Mark Paluch
 * @since 4.1
 * @see RequestedCertificate
 * @see CertificateEventPublisher
 */
public class CertificateContainer extends CertificateEventPublisher
		implements CertificateRegistry, InitializingBean, DisposableBean, SmartLifecycle {

	private static final AtomicIntegerFieldUpdater<CertificateContainer> UPDATER = AtomicIntegerFieldUpdater
			.newUpdater(CertificateContainer.class, "status");

	private static final AtomicInteger poolId = new AtomicInteger();

	private static final int STATUS_INITIAL = 0;

	private static final int STATUS_STARTED = 1;

	private static final int STATUS_DESTROYED = 2;

	@SuppressWarnings("FieldMayBeFinal") // allow setting via reflection.
	private static Log logger = LogFactory.getLog(CertificateContainer.class);


	private final CertificateAuthority certificateAuthority;

	private Duration expiryThreshold = Duration.ofSeconds(60);

	private final List<RequestedCertificate> certificateRequests = new CopyOnWriteArrayList<>();

	private final Map<RequestedCertificate, CertificateRenewalScheduler> schedulers = new ConcurrentHashMap<>();

	private final Map<RequestedCertificate, ManagedCertificate> managedCertificates = new ConcurrentHashMap<>();

	private @Nullable TaskScheduler taskScheduler;

	private boolean manageTaskScheduler;

	private volatile boolean initialized;

	private volatile int status = STATUS_INITIAL;


	/**
	 * Create a new {@code CertificateContainer} given {@link VaultPkiOperations}.
	 * @param pkiOperations must not be {@literal null}.
	 */
	public CertificateContainer(VaultPkiOperations pkiOperations) {
		this(new VaultCertificateAuthority(pkiOperations));
	}

	/**
	 * Create a new {@code CertificateContainer} given {@link CertificateAuthority}.
	 * @param certificateAuthority must not be {@literal null}.
	 */
	public CertificateContainer(CertificateAuthority certificateAuthority) {
		Assert.notNull(certificateAuthority, "CertificateAuthority must not be null");
		this.certificateAuthority = certificateAuthority;
	}

	/**
	 * Create a new {@code CertificateContainer} given {@link CertificateAuthority}
	 * and {@link TaskScheduler}.
	 * @param certificateAuthority must not be {@literal null}.
	 * @param taskScheduler must not be {@literal null}.
	 */
	public CertificateContainer(CertificateAuthority certificateAuthority, TaskScheduler taskScheduler) {
		Assert.notNull(certificateAuthority, "CertificateAuthority must not be null");
		this.certificateAuthority = certificateAuthority;
		setTaskScheduler(taskScheduler);
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

	private TaskScheduler getRequiredTaskScheduler() {
		Assert.state(this.taskScheduler != null, "TaskScheduler must not be null");
		return this.taskScheduler;
	}


	public Duration getExpiryThreshold() {
		return expiryThreshold;
	}

	/**
	 * Set the expiry threshold. A {@link Certificate} is rotated the given time
	 * before it expires.
	 * @param expiryThreshold duration before {@link Certificate} expiry, must not
	 * be {@literal null} or negative.
	 */
	public void setExpiryThreshold(Duration expiryThreshold) {
		Assert.notNull(expiryThreshold, "Expiry threshold must not be null");
		Assert.isTrue(!expiryThreshold.isNegative(), "Expiry threshold must not be negative");
		this.expiryThreshold = expiryThreshold;
	}

	@Override
	public void register(RequestedCertificate certificate) {
		Assert.notNull(certificate, "RequestedCertificate must not be null");
		this.certificateRequests.add(certificate);

		if (this.initialized) {
			Assert.state(this.taskScheduler != null, "TaskScheduler must not be null");
			CertificateRenewalScheduler scheduler = new CertificateRenewalScheduler(certificate,
					this.expiryThreshold);
			this.schedulers.put(certificate, scheduler);

			if (this.status == STATUS_STARTED) {
				start(certificate, scheduler);
			}
		}
	}

	@Override
	public void register(RequestedCertificate certificate, CertificateListener listener) {
		CertificateErrorListener errorListener = listener instanceof CertificateErrorListener cel ? cel
				: (leaseEvent, exception) -> {
				};
		ManagedCertificate managedSecret = new ManagedCertificate(certificate, leaseEvent -> {
			if (leaseEvent.getSource().equals(certificate)) {
				listener.onCertificateEvent(leaseEvent);
			}
		}, (leaseEvent, exception) -> {
			if (leaseEvent.getSource().equals(certificate)) {
				errorListener.onCertificateError(leaseEvent, exception);
			}
		});

		ManagedCertificate previous = managedCertificates.put(certificate, managedSecret);
		if (previous != null) {
			removeCertificateListener(previous.certificateListener());
			removeCertificateErrorListener(previous.errorListener());
		}
		addCertificateListener(managedSecret.certificateListener());
		if (listener instanceof CertificateErrorListener) {
			addErrorListener(managedSecret.errorListener());
		}
		register(certificate);
	}

	@Override
	public boolean unregister(RequestedCertificate certificate) {
		boolean removed = false;
		CertificateRenewalScheduler scheduler = this.schedulers.get(certificate);
		if (this.certificateRequests.remove(certificate)) {
			removed = true;
		}
		if (scheduler != null) {
			stop(certificate, scheduler);
		}
		ManagedCertificate previous = managedCertificates.remove(certificate);
		if (previous != null) {
			removeCertificateListener(previous.certificateListener());
			removeCertificateErrorListener(previous.errorListener());
		}
		return removed;
	}

	/**
	 * Force certificate rotation.
	 * @param requestedCertificate the certificate to rotate.
	 */
	public void rotate(RequestedCertificate requestedCertificate) {
		CertificateRenewalScheduler scheduler = schedulers.get(requestedCertificate);
		if (scheduler != null) {
			scheduler.rotate();
		}
	}

	/**
	 * Start the {@code CertificateContainer}. Starting the container will initially
	 * obtain certificates for the requested certificates. A started container
	 * publishes events through {@link CertificateListener}. Additional certificates
	 * can be requested at any time.
	 * <p>Multiple calls are synchronized to start the container only once.
	 * Container start requires {@link #afterPropertiesSet() initialization} and
	 * cannot be started once the container was {@link #destroy() destroyed}.
	 *
	 * @see #afterPropertiesSet()
	 * @see #stop()
	 */
	@Override
	public void start() {
		Assert.state(this.initialized, "Container is not initialized");
		Assert.state(this.status != STATUS_DESTROYED, "Container is destroyed and cannot be started");
		Map<RequestedCertificate, CertificateRenewalScheduler> renewals = new HashMap<>(this.schedulers);

		if (UPDATER.compareAndSet(this, STATUS_INITIAL, STATUS_STARTED)) {
			for (Entry<RequestedCertificate, CertificateRenewalScheduler> entry : renewals.entrySet()) {
				start(entry.getKey(), entry.getValue());
			}
		}
	}

	private void start(RequestedCertificate request, CertificateRenewalScheduler renewalScheduler) {
		try {
			CertificateHolder holder = request(request);
			logger.debug("Certificate for %s obtained, serial number %s".formatted(request.getName(),
					holder.getSerialNumber()));
			renewalScheduler.scheduleRotation(holder);
			onCertificateObtained(request, holder.certificate());
		} catch (Exception e) {
			onError(request, e);
		}
	}

	private CertificateHolder request(RequestedCertificate request) {

		if (request instanceof RequestedCertificateBundle bundle) {
			Certificate certificate = this.certificateAuthority.issueCertificate(bundle.getName(),
					bundle.getRole(), bundle.getRequest());
			return new CertificateHolder(certificate);
		}

		if (request instanceof RequestedTrustAnchor trustAnchor) {
			Certificate certificate = this.certificateAuthority.getIssuerCertificate(trustAnchor.getName(),
					trustAnchor.getIssuer());
			return new CertificateHolder(certificate);
		}

		throw new IllegalStateException("Unsupported RequestedCertificate type: " + request.getClass());
	}

	private void stop(RequestedCertificate certificate, CertificateRenewalScheduler scheduler) {
		scheduler.disableScheduleRenewal();
	}

	/**
	 * Stop the {@link CertificateContainer}. Stopping the container will stop
	 * certificate rotation and event publishing.
	 * <p>Multiple calls are synchronized to stop the container only once.
	 *
	 * @see #start()
	 */
	@Override
	public void stop() {
		if (UPDATER.compareAndSet(this, STATUS_STARTED, STATUS_INITIAL)) {
			for (CertificateRenewalScheduler scheduler : this.schedulers.values()) {
				scheduler.disableScheduleRenewal();
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
	}

	/**
	 * Shutdown this {@code CertificateContainer}, disable rotation of active
	 * certificates.
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
				stop();
				this.schedulers.clear();
				if (this.manageTaskScheduler) {
					if (this.taskScheduler instanceof DisposableBean) {
						((DisposableBean) this.taskScheduler).destroy();
						this.taskScheduler = null;
					}
				}
			}
		}
	}

	boolean isExpired(Instant expiration) {
		Duration expiresIn = Duration.between(getRequiredTaskScheduler().getClock().instant(), expiration);
		return expiresIn.isNegative() || expiresIn.isZero() || expiresIn.compareTo(expiryThreshold) >= 0;
	}

	static Duration getRenewalDelay(Clock clock, Instant expiration, Duration expiryThreshold) {

		Duration expiresIn = Duration.between(clock.instant(), expiration);
		long nextDelay = expiresIn.toSeconds() - expiryThreshold.getSeconds();

		// apply jitter if the expiry is between the expiry threshold and twice the
		// expiry threshold
		if (!expiryThreshold.isZero() && expiresIn.compareTo(expiryThreshold) > 0
				&& expiresIn.minus(expiryThreshold).compareTo(expiryThreshold) > 0) {
			long jitter = Math.min(ThreadLocalRandom.current().nextLong(1, expiryThreshold.toSeconds()),
					expiryThreshold.toSeconds());
			nextDelay += jitter;
		}

		return Duration.ofSeconds(Math.max(0, nextDelay));
	}

	/**
	 * Renewal scheduler for a managed certificate request.
	 */
	class CertificateRenewalScheduler {

		private static final AtomicReferenceFieldUpdater<CertificateRenewalScheduler, CertificateHolder> CURRENT_UPDATER = AtomicReferenceFieldUpdater
				.newUpdater(CertificateRenewalScheduler.class, CertificateHolder.class, "currentBundleRef");

		private final RequestedCertificate requestedCertificate;

		private final Duration expiryThreshold;

		volatile CertificateContainer.@Nullable CertificateHolder currentBundleRef;

		final Map<CertificateHolder, ScheduledFuture<?>> schedules = new ConcurrentHashMap<>();

		CertificateRenewalScheduler(RequestedCertificate requestedCertificate,
				Duration expiryThreshold) {
			this.requestedCertificate = requestedCertificate;
			this.expiryThreshold = expiryThreshold;
		}

		void scheduleRotation(CertificateHolder certificateHolder) {

			Duration renewalSeconds = getRenewalDelay(getRequiredTaskScheduler().getClock(), certificateHolder.expiry(),
					expiryThreshold);

			if (logger.isDebugEnabled()) {
				logger.debug("Scheduling certificate rotation for %s in %s, expiry %s ".formatted(requestedCertificate,
						DurationFormatterUtils.print(renewalSeconds, Style.COMPOSITE), certificateHolder.expiry()));
			}

			CertificateHolder current = CURRENT_UPDATER.get(this);
			CURRENT_UPDATER.set(this, certificateHolder);

			if (current != null) {
				cancelSchedule(current);
			}

			Runnable task = () -> {
				try {
					rotate(certificateHolder);
				} catch (Exception e) {
					onError(requestedCertificate, e);
				}
			};

			ScheduledFuture<?> scheduledFuture = getRequiredTaskScheduler().schedule(task,
					new OneShotTrigger(renewalSeconds));
			this.schedules.put(certificateHolder, scheduledFuture);
		}

		private @Nullable CertificateHolder rotate() {
			CertificateHolder current = CURRENT_UPDATER.get(this);
			Assert.state(current != null, "No current certificate to rotate");
			return rotate(current);
		}

		private @Nullable CertificateHolder rotate(CertificateHolder certificateHolder) {

			CertificateHolder current = CURRENT_UPDATER.get(CertificateRenewalScheduler.this);
			if (CURRENT_UPDATER.get(CertificateRenewalScheduler.this) != certificateHolder) {
				return current;
			}

			cancelSchedule(certificateHolder);

			if (logger.isDebugEnabled()) {
				logger.debug("Rotating certificate for %s…".formatted(requestedCertificate));
			}

			CertificateHolder renewedCertificate = null;
			try {
				renewedCertificate = request(requestedCertificate);

				if (logger.isDebugEnabled()) {
					logger.debug("Certificate for %s rotated, serial number %s".formatted(requestedCertificate,
							renewedCertificate.getSerialNumber()));
				}
				if (CURRENT_UPDATER.compareAndSet(CertificateRenewalScheduler.this, certificateHolder,
						renewedCertificate)) {
					scheduleRotation(renewedCertificate);
					onCertificateRotated(requestedCertificate, renewedCertificate.certificate());
					if (isExpired(certificateHolder.expiry())) {
						onCertificateExpired(requestedCertificate, certificateHolder.certificate());
					}
				} else {
					logger.debug("Race condition during certificate rotation of '%s'".formatted(requestedCertificate));
				}
				return renewedCertificate;
			} catch (Exception e) {
				onError(requestedCertificate, e);
			}


			return null;
		}

		private void cancelSchedule(CertificateHolder bundle) {
			ScheduledFuture<?> scheduledFuture = this.schedules.get(bundle);
			if (scheduledFuture != null && !scheduledFuture.isDone() && !scheduledFuture.isCancelled()) {
				scheduledFuture.cancel(false);
			}
		}

		/**
		 * Disables schedule for already scheduled renewals.
		 */
		void disableScheduleRenewal() {
			// capture the previous lease to revoke it
			Set<CertificateHolder> certificates = new HashSet<>(this.schedules.keySet());
			for (CertificateHolder certificate : certificates) {
				cancelSchedule(certificate);
				this.schedules.remove(certificate);
			}
		}

	}

	record CertificateHolder(Certificate certificate, X509Certificate x509Certificate, Instant expiry) {

		CertificateHolder(Certificate certificate) {
			this(certificate, certificate.getX509Certificate());
		}

		CertificateHolder(Certificate certificate, X509Certificate x509Certificate) {
			this(certificate, x509Certificate, x509Certificate.getNotAfter().toInstant());
		}

		public String getSerialNumber() {

			String serialNumber = certificate.getSerialNumber();
			if (StringUtils.hasText(serialNumber)) {
				return serialNumber;
			}

			byte[] serialBytes = x509Certificate.getSerialNumber().toByteArray();
			while (serialBytes.length != 0 && serialBytes[0] == 0x00) {
				byte[] tmp = new byte[serialBytes.length - 1];
				System.arraycopy(serialBytes, 1, tmp, 0, tmp.length);
				serialBytes = tmp;
			}

			if (serialBytes.length == 0) {
				return "00";
			}

			StringBuilder sb = new StringBuilder();
			for (byte serialByte : serialBytes) {
				if (!sb.isEmpty()) {
					sb.append(":");
				}
				sb.append(String.format("%02x", serialByte));
			}
			return sb.toString();
		}

	}

	/**
	 * This one-shot trigger creates only one execution time to trigger an execution
	 * only once.
	 */
	class OneShotTrigger implements Trigger {

		private static final AtomicIntegerFieldUpdater<OneShotTrigger> UPDATER = AtomicIntegerFieldUpdater
				.newUpdater(OneShotTrigger.class, "status");

		private static final int STATUS_ARMED = 0;

		private static final int STATUS_FIRED = 1;

		// see AtomicIntegerFieldUpdater UPDATER
		private volatile int status = 0;

		private final Duration delay;

		OneShotTrigger(Duration delay) {
			this.delay = delay;
		}

		@Override
		public @Nullable Instant nextExecution(TriggerContext triggerContext) {
			return UPDATER.compareAndSet(this, STATUS_ARMED, STATUS_FIRED)
					? getRequiredTaskScheduler().getClock().instant().plus(this.delay)
					: null;
		}

	}


	record ManagedCertificate(RequestedCertificate requestedCertificate,
			CertificateListener certificateListener, CertificateErrorListener errorListener) {

	}

}
