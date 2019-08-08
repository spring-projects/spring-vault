/*
 * Copyright 2018-2019 the original author or authors.
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
package org.springframework.vault.authentication;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.lang.Nullable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.util.Assert;
import org.springframework.vault.support.LeaseStrategy;
import org.springframework.vault.support.VaultToken;

/**
 * Support class to build Lifecycle-aware Session Manager implementations, defining common
 * properties such as the {@link TaskScheduler} and {@link RefreshTrigger}. Typically used
 * within the framework itself.
 * <p>
 * Not intended to be used directly.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public abstract class LifecycleAwareSessionManagerSupport
		extends AuthenticationEventPublisher {

	/**
	 * Refresh 5 seconds before the token expires.
	 */
	public static final int REFRESH_PERIOD_BEFORE_EXPIRY = 5;

	private static final RefreshTrigger DEFAULT_TRIGGER = new FixedTimeoutRefreshTrigger(
			REFRESH_PERIOD_BEFORE_EXPIRY, TimeUnit.SECONDS);

	/**
	 * Logger available to subclasses.
	 */
	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * Threading infrastructure for token renewal/refresh.
	 */
	private final TaskScheduler taskScheduler;

	/**
	 * Trigger to calculate the next renewal time.
	 */
	private final RefreshTrigger refreshTrigger;

	/**
	 * Controls whether to perform a token self-lookup using
	 * {@code auth/token/lookup-self} for {@link VaultToken}s obtained from a
	 * {@link ClientAuthentication}. Self-lookup determines whether a token is renewable
	 * and its TTL. Self lookup is skipped for {@link LoginToken}. Self-lookup requests
	 * decrement token usage count by one.
	 */
	private boolean tokenSelfLookupEnabled = true;

	private LeaseStrategy leaseStrategy = LeaseStrategy.dropOnError();

	/**
	 * Create a {@link LifecycleAwareSessionManager} given {@link TaskScheduler}. Using
	 * {@link #DEFAULT_TRIGGER} to trigger refresh.
	 *
	 * @param taskScheduler must not be {@literal null}.
	 */
	public LifecycleAwareSessionManagerSupport(TaskScheduler taskScheduler) {
		this(taskScheduler, DEFAULT_TRIGGER);
	}

	/**
	 * Create a {@link LifecycleAwareSessionManager} given {@link TaskScheduler} and
	 * {@link RefreshTrigger}.
	 *
	 * @param taskScheduler must not be {@literal null}.
	 * @param refreshTrigger must not be {@literal null}.
	 */
	public LifecycleAwareSessionManagerSupport(TaskScheduler taskScheduler,
			RefreshTrigger refreshTrigger) {

		Assert.notNull(taskScheduler, "TaskScheduler must not be null");
		Assert.notNull(refreshTrigger, "RefreshTrigger must not be null");

		this.taskScheduler = taskScheduler;
		this.refreshTrigger = refreshTrigger;
	}

	/**
	 * Returns whether token self-lookup is enabled to augment {@link VaultToken} obtained
	 * from a {@link ClientAuthentication}. Self-lookup determines whether a token is
	 * renewable and its TTL. Self lookup is skipped for {@link LoginToken}. Self-lookup
	 * requests decrement token usage count by one. Skipped for {@link LoginToken}.
	 * <p>
	 * Self-lookup for tokens without a permission to access
	 * {@code auth/token/lookup-self} will fail gracefully and continue without token
	 * renewal.
	 *
	 * @return {@literal true} to enable self-lookup, {@literal false} to disable
	 * self-lookup. Enabled by default.
	 */
	protected boolean isTokenSelfLookupEnabled() {
		return tokenSelfLookupEnabled;
	}

	/**
	 * Enables/disables token self-lookup. Self-lookup augments {@link VaultToken}
	 * obtained from a {@link ClientAuthentication}. Self-lookup determines whether a
	 * token is renewable and its TTL.
	 *
	 * @param tokenSelfLookupEnabled {@literal true} to enable self-lookup,
	 *     {@literal false} to disable self-lookup. Enabled by default.
	 */
	public void setTokenSelfLookupEnabled(boolean tokenSelfLookupEnabled) {
		this.tokenSelfLookupEnabled = tokenSelfLookupEnabled;
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

	LeaseStrategy getLeaseStrategy() {
		return leaseStrategy;
	}

	/**
	 * @return the underlying {@link TaskScheduler}.
	 */
	protected TaskScheduler getTaskScheduler() {
		return taskScheduler;
	}

	/**
	 * @return the underlying {@link RefreshTrigger}.
	 */
	protected RefreshTrigger getRefreshTrigger() {
		return refreshTrigger;
	}

	/**
	 * Check whether the Token falls below its
	 * {@link RefreshTrigger#getValidTtlThreshold(LoginToken) validity threshold}.
	 * Typically used to discard a token.
	 *
	 * @param loginToken must not be {@literal null}.
	 * @return {@literal true} if token validity falls below validity threshold,
	 * {@literal false} if still valid.
	 */
	protected boolean isExpired(LoginToken loginToken) {

		Duration validTtlThreshold = getRefreshTrigger().getValidTtlThreshold(loginToken);
		return loginToken.getLeaseDuration().compareTo(validTtlThreshold) <= 0;
	}

	/**
	 * This one-shot trigger creates only one execution time to trigger an execution only
	 * once.
	 */
	protected static class OneShotTrigger implements Trigger {

		private final AtomicBoolean fired = new AtomicBoolean();

		private final Date nextExecutionTime;

		public OneShotTrigger(Date nextExecutionTime) {
			this.nextExecutionTime = nextExecutionTime;
		}

		@Nullable
		public Date nextExecutionTime(TriggerContext triggerContext) {

			if (fired.compareAndSet(false, true)) {
				return nextExecutionTime;
			}

			return null;
		}
	}

	/**
	 * Common interface for trigger objects that determine the next execution time of a
	 * refresh task.
	 */
	public interface RefreshTrigger {

		/**
		 * Determine the next execution time according to the given trigger context.
		 *
		 * @param loginToken login token encapsulating renewability and lease duration.
		 * @return the next execution time as defined by the trigger, or {@code null} if
		 * the trigger won't fire anymore
		 */
		Date nextExecutionTime(LoginToken loginToken);

		/**
		 * Returns the minimum TTL duration to consider a token valid after renewal.
		 * Tokens with a shorter TTL are revoked and considered expired.
		 *
		 * @param loginToken the login token after renewal.
		 * @return minimum TTL {@link Duration} to consider a token valid.
		 * @since 2.0
		 */
		Duration getValidTtlThreshold(LoginToken loginToken);
	}

	/**
	 * {@link RefreshTrigger} implementation using a fixed timeout to schedule renewal
	 * before a {@link LoginToken} expires.
	 *
	 * @author Mark Paluch
	 * @since 1.0.1
	 */
	public static class FixedTimeoutRefreshTrigger implements RefreshTrigger {

		private static final Duration ONE_SECOND = Duration.ofSeconds(1);

		private final Duration duration;
		private final Duration validTtlThreshold;

		/**
		 * Create a new {@link FixedTimeoutRefreshTrigger} to calculate execution times of
		 * {@code timeout} before the {@link LoginToken} expires
		 *
		 * @param timeout timeout value, non-negative long value.
		 * @param timeUnit must not be {@literal null}.
		 */
		public FixedTimeoutRefreshTrigger(long timeout, TimeUnit timeUnit) {

			Assert.isTrue(timeout >= 0,
					"Timeout duration must be greater or equal to zero");
			Assert.notNull(timeUnit, "TimeUnit must not be null");

			this.duration = Duration.ofMillis(timeUnit.toMillis(timeout));
			this.validTtlThreshold = Duration.ofMillis(timeUnit.toMillis(timeout) + 2000);
		}

		/**
		 * Create a new {@link FixedTimeoutRefreshTrigger} to calculate execution times of
		 * {@code timeout} before the {@link LoginToken} expires. Valid TTL threshold is
		 * set to two seconds longer to compensate for timing issues during scheduling.
		 *
		 * @param timeout timeout value.
		 * @since 2.0
		 */
		public FixedTimeoutRefreshTrigger(Duration timeout) {
			this(timeout, timeout.plus(Duration.ofSeconds(2)));
		}

		/**
		 * Create a new {@link FixedTimeoutRefreshTrigger} to calculate execution times of
		 * {@code timeout} before the {@link LoginToken} expires.
		 *
		 * @param timeout timeout value.
		 * @param validTtlThreshold minimum TTL duration to consider a Token as valid.
		 *     Tokens with a shorter TTL are not used anymore. Should be greater than
		 *     {@code timeout} to prevent token expiry.
		 * @since 2.0
		 */
		public FixedTimeoutRefreshTrigger(Duration timeout, Duration validTtlThreshold) {

			Assert.isTrue(timeout.toMillis() >= 0,
					"Timeout duration must be greater or equal to zero");

			Assert.notNull(validTtlThreshold, "Valid TTL threshold must not be null");

			this.duration = timeout;
			this.validTtlThreshold = validTtlThreshold;
		}

		@Override
		public Date nextExecutionTime(LoginToken loginToken) {

			long milliseconds = Math.max(ONE_SECOND.toMillis(),
					loginToken.getLeaseDuration().toMillis() - duration.toMillis());

			return new Date(System.currentTimeMillis() + milliseconds);
		}

		@Override
		public Duration getValidTtlThreshold(LoginToken loginToken) {
			return validTtlThreshold;
		}
	}
}
