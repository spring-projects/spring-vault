/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.vault.authentication;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.RequiredArgsConstructor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.util.Assert;
import org.springframework.util.NumberUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

/**
 * Lifecycle-aware Session Manager. This {@link SessionManager} obtains tokens from a
 * {@link ClientAuthentication} upon {@link #getSessionToken() request}. Tokens are
 * renewed asynchronously if a token has a lease duration. This happens 5 seconds before
 * the token expires, see {@link #REFRESH_PERIOD_BEFORE_EXPIRY}.
 * <p>
 * This {@link SessionManager} also implements {@link DisposableBean} to revoke the
 * {@link LoginToken} once it's not required anymore. Token revocation will stop regular
 * token refresh.
 * <p>
 * If Token renewal runs into a client-side error, it assumes the token was
 * revoked/expired and discards the token state so the next attempt will lead to another
 * login attempt.
 *
 * @author Mark Paluch
 * @see LoginToken
 * @see SessionManager
 * @see AsyncTaskExecutor
 */
public class LifecycleAwareSessionManager implements SessionManager, DisposableBean {

	/**
	 * Refresh 5 seconds before the token expires.
	 */
	public static final int REFRESH_PERIOD_BEFORE_EXPIRY = 5;

	private final static RefreshTrigger DEFAULT_TRIGGER = new FixedTimeoutRefreshTrigger(
			REFRESH_PERIOD_BEFORE_EXPIRY, TimeUnit.SECONDS);

	private final static Log logger = LogFactory
			.getLog(LifecycleAwareSessionManager.class);

	private final ClientAuthentication clientAuthentication;

	private final RestOperations restOperations;

	private final TaskScheduler taskScheduler;

	private final RefreshTrigger refreshTrigger;

	private final Object lock = new Object();

	private volatile VaultToken token;

	/**
	 * Create a {@link LifecycleAwareSessionManager} given {@link ClientAuthentication},
	 * {@link AsyncTaskExecutor} and {@link RestOperations}.
	 *
	 * @param clientAuthentication must not be {@literal null}.
	 * @param taskScheduler must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 */
	public LifecycleAwareSessionManager(ClientAuthentication clientAuthentication,
			TaskScheduler taskScheduler, RestOperations restOperations) {

		this(clientAuthentication, taskScheduler, restOperations, DEFAULT_TRIGGER);
	}

	/**
	 * Create a {@link LifecycleAwareSessionManager} given {@link ClientAuthentication},
	 * {@link AsyncTaskExecutor} and {@link RestOperations}.
	 *
	 * @param clientAuthentication must not be {@literal null}.
	 * @param taskScheduler must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 * @param refreshTrigger must not be {@literal null}.
	 * @since 1.0.1
	 */
	public LifecycleAwareSessionManager(ClientAuthentication clientAuthentication,
			TaskScheduler taskScheduler, RestOperations restOperations,
			RefreshTrigger refreshTrigger) {

		Assert.notNull(clientAuthentication, "ClientAuthentication must not be null");
		Assert.notNull(taskScheduler, "TaskScheduler must not be null");
		Assert.notNull(restOperations, "RestOperations must not be null");
		Assert.notNull(refreshTrigger, "RefreshTrigger must not be null");

		this.clientAuthentication = clientAuthentication;
		this.restOperations = restOperations;
		this.taskScheduler = taskScheduler;
		this.refreshTrigger = refreshTrigger;
	}

	@Override
	public void destroy() {

		VaultToken token = this.token;
		this.token = null;

		if (token instanceof LoginToken) {
			revoke(token);
		}
	}

	private void revoke(VaultToken token) {

		try {
			restOperations.postForObject("/auth/token/revoke-self",
					new HttpEntity<Object>(VaultHttpHeaders.from(token)), Map.class);
		}
		catch (HttpStatusCodeException e) {
			logger.warn(String.format("Cannot revoke VaultToken: %s",
					VaultResponses.getError(e.getResponseBodyAsString())));
		}
	}

	/**
	 * Performs a token refresh. Create a new token if no token was obtained before. If a
	 * token was obtained before, it uses self-renewal to renew the current token.
	 * Client-side errors (like permission denied) indicate the token cannot be renewed
	 * because it's expired or simply not found.
	 *
	 * @return {@literal true} if the refresh was successful. {@literal false} if a new
	 * token was obtained or refresh failed.
	 */
	protected boolean renewToken() {

		logger.info("Renewing token");

		if (token == null) {
			getSessionToken();
			return false;
		}

		try {
			restOperations.postForObject("/auth/token/renew-self",
					new HttpEntity<Object>(VaultHttpHeaders.from(token)), Map.class);
			return true;
		}
		catch (HttpStatusCodeException e) {

			if (e.getStatusCode().is4xxClientError()) {
				logger.debug(String.format(
						"Cannot refresh token, resetting token and performing re-login: %s",
						VaultResponses.getError(e.getResponseBodyAsString())));
				token = null;
				return false;
			}

			throw new VaultException(
					VaultResponses.getError(e.getResponseBodyAsString()));
		}
		catch (RestClientException e) {
			throw new VaultException("Cannot refresh token", e);
		}
	}

	@Override
	public VaultToken getSessionToken() {

		if (token == null) {

			synchronized (lock) {

				if (token == null) {
					token = login();

					if (isTokenRenewable()) {
						scheduleRenewal();
					}
				}
			}
		}

		return token;
	}

	protected VaultToken login() {
		return clientAuthentication.login();
	}

	protected boolean isTokenRenewable() {

		if (token instanceof LoginToken) {

			LoginToken loginToken = (LoginToken) token;
			return loginToken.getLeaseDuration() > 0 && loginToken.isRenewable();
		}

		return false;
	}

	private void scheduleRenewal() {

		logger.info("Scheduling Token renewal");

		final Runnable task = new Runnable() {
			@Override
			public void run() {
				try {
					if (LifecycleAwareSessionManager.this.token != null
							&& isTokenRenewable()) {
						if (renewToken()) {
							scheduleRenewal();
						}
					}
				}
				catch (Exception e) {
					logger.error("Cannot renew VaultToken", e);
				}
			}
		};

		taskScheduler.schedule(task, createTrigger());
	}

	private OneShotTrigger createTrigger() {
		return new OneShotTrigger(refreshTrigger.nextExecutionTime((LoginToken) token));
	}

	/**
	 * This one-shot trigger creates only one execution time to trigger an execution only
	 * once.
	 */
	@RequiredArgsConstructor
	private static class OneShotTrigger implements Trigger {

		private final AtomicBoolean fired = new AtomicBoolean();

		private final Date nextExecutionTime;

		public Date nextExecutionTime(TriggerContext triggerContext) {

			if (fired.compareAndSet(false, true)) {
				return nextExecutionTime;
			}

			return null;
		}
	}

	/**
	 * Common interface for trigger objects that determine the next execution time of a
	 * refresh task that they get associated with.
	 */
	public interface RefreshTrigger {

		/**
		 * Determine the next execution time according to the given trigger context.
		 * @param loginToken login token encapsulating renewability and lease duration.
		 * @return the next execution time as defined by the trigger, or {@code null} if
		 * the trigger won't fire anymore
		 */
		Date nextExecutionTime(LoginToken loginToken);
	}

	/**
	 * {@link RefreshTrigger} implementation using a fixed timeout to schedule renewal
	 * before a {@link LoginToken} expires.
	 *
	 * @author Mark Paluch
	 * @since 1.0.1
	 */
	public static class FixedTimeoutRefreshTrigger implements RefreshTrigger {

		private final long duration;

		private final TimeUnit timeUnit;

		/**
		 * Create a new {@link FixedTimeoutRefreshTrigger} to calculate execution times of
		 * {@code timeout} before the {@link LoginToken} expires
		 * @param timeout timeout value, non-negative long value.
		 * @param timeUnit must not be {@literal null}.
		 */
		public FixedTimeoutRefreshTrigger(long timeout, TimeUnit timeUnit) {

			Assert.isTrue(timeout >= 0,
					"Timeout duration must be greater or equal to zero");
			Assert.notNull(timeUnit, "TimeUnit must not be null");

			this.duration = timeout;
			this.timeUnit = timeUnit;
		}

		@Override
		public Date nextExecutionTime(LoginToken loginToken) {

			long milliseconds = NumberUtils
					.convertNumberToTargetClass(
							Math.max(1000,
									loginToken.getLeaseDuration()
											- timeUnit.toMillis(duration)),
							Integer.class);

			return new Date(System.currentTimeMillis() + milliseconds);
		}
	}
}
