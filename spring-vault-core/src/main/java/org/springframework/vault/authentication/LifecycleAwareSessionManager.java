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

import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.Getter;
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
import org.springframework.util.ClassUtils;
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
 * token refresh. Tokens are only revoked only if the associated
 * {@link ClientAuthentication} returned a {@link LoginToken}.
 * <p>
 * If Token renewal runs into a client-side error, it assumes the token was
 * revoked/expired and discards the token state so the next attempt will lead to another
 * login attempt.
 * <p>
 * By default, {@link VaultToken} are looked up in Vault to determine renewability and the
 * remaining TTL, see {@link #setTokenSelfLookupEnabled(boolean)}.
 *
 * @author Mark Paluch
 * @author Steven Swor
 * @see LoginToken
 * @see SessionManager
 * @see AsyncTaskExecutor
 */
public class LifecycleAwareSessionManager implements SessionManager, DisposableBean {

	/**
	 * Refresh 5 seconds before the token expires.
	 */
	public static final int REFRESH_PERIOD_BEFORE_EXPIRY = 5;

	private static final RefreshTrigger DEFAULT_TRIGGER = new FixedTimeoutRefreshTrigger(
			REFRESH_PERIOD_BEFORE_EXPIRY, TimeUnit.SECONDS);

	private static final Log logger = LogFactory
			.getLog(LifecycleAwareSessionManager.class);

	/**
	 * Client authentication mechanism. Used to obtain a {@link VaultToken} or
	 * {@link LoginToken}.
	 */
	private final ClientAuthentication clientAuthentication;

	/**
	 * HTTP client.
	 */
	private final RestOperations restOperations;

	/**
	 * Threading infrastructure for token renewal/refresh.
	 */
	private final TaskScheduler taskScheduler;

	/**
	 * Trigger to calculate the next renewal time.
	 */
	private final RefreshTrigger refreshTrigger;

	private final Object lock = new Object();

	/**
	 * Controls whether to perform a token self-lookup using
	 * {@code auth/token/lookup-self} for {@link VaultToken}s obtained from a
	 * {@link ClientAuthentication}. Self-lookup determines whether a token is renewable
	 * and its TTL. Self lookup is skipped for {@link LoginToken}. Self-lookup requests
	 * decrement token usage count by one. Skipped for {@link LoginToken}.
	 */
	private boolean tokenSelfLookupEnabled = true;

	/**
	 * The token state: Contains the currently valid token that identifies the Vault
	 * session.
	 */
	private volatile Optional<TokenWrapper> token = Optional.empty();

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

	/**
	 * Returns whether token self-lookup is enabled to augment {@link VaultToken} obtained
	 * from a {@link ClientAuthentication}. Self-lookup determines whether a token is
	 * renewable and its TTL. Self lookup is skipped for {@link LoginToken}. Self-lookup
	 * requests decrement token usage count by one. Skipped for {@link LoginToken}.
	 * <p/>
	 * Self-lookup for tokens without a permission to access
	 * {@code auth/token/lookup-self} will fail gracefully and continue without token
	 * renewal.
	 *
	 * @return {@literal true} to enable self-lookup, {@literal false} to disable
	 * self-lookup. Enabled by default.
	 * @since 2.0
	 */
	public boolean isTokenSelfLookupEnabled() {
		return tokenSelfLookupEnabled;
	}

	/**
	 * Enables/disables token self-lookup. Self-lookup augments {@link VaultToken}
	 * obtained from a {@link ClientAuthentication}. Self-lookup determines whether a
	 * token is renewable and its TTL.
	 *
	 * @param tokenSelfLookupEnabled {@literal true} to enable self-lookup,
	 * {@literal false} to disable self-lookup. Enabled by default.
	 * @since 2.0
	 */
	public void setTokenSelfLookupEnabled(boolean tokenSelfLookupEnabled) {
		this.tokenSelfLookupEnabled = tokenSelfLookupEnabled;
	}

	@Override
	public void destroy() {

		Optional<TokenWrapper> token = this.token;
		this.token = Optional.empty();

		token.filter(TokenWrapper::isRevocable).map(TokenWrapper::getToken)
				.ifPresent(this::revoke);
	}

	/**
	 * Revoke a {@link VaultToken}.
	 *
	 * @param token the token to revoke, must not be {@literal null}.
	 */
	protected void revoke(VaultToken token) {

		try {
			restOperations.postForObject("auth/token/revoke-self", new HttpEntity<>(
					VaultHttpHeaders.from(token)), Map.class);
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

		if (!token.isPresent()) {
			getSessionToken();
			return false;
		}

		try {
			restOperations.postForObject("auth/token/renew-self", new HttpEntity<>(
					VaultHttpHeaders.from(token.get().getToken())), Map.class);
			return true;
		}
		catch (HttpStatusCodeException e) {

			if (e.getStatusCode().is4xxClientError()) {
				logger.debug(String
						.format("Cannot refresh token, resetting token and performing re-login: %s",
								VaultResponses.getError(e.getResponseBodyAsString())));
				token = Optional.empty();
				return false;
			}

			throw new VaultException(VaultResponses.getError(e.getResponseBodyAsString()));
		}
		catch (RestClientException e) {
			throw new VaultException("Cannot refresh token", e);
		}
	}

	@Override
	public VaultToken getSessionToken() {

		if (!token.isPresent()) {

			synchronized (lock) {

				if (!token.isPresent()) {

					VaultToken token = clientAuthentication.login();
					TokenWrapper wrapper = new TokenWrapper(token,
							token instanceof LoginToken);

					if (isTokenSelfLookupEnabled()
							&& !ClassUtils.isAssignableValue(LoginToken.class, token)) {
						try {
							token = LoginTokenAdapter.augmentWithSelfLookup(
									this.restOperations, token);
							wrapper = new TokenWrapper(token, false);
						}
						catch (VaultTokenLookupException e) {
							logger.warn(String.format(
									"Cannot enhance VaultToken to a LoginToken: %s",
									e.getMessage()));
						}
					}

					this.token = Optional.of(wrapper);

					if (isTokenRenewable()) {
						scheduleRenewal();
					}
				}
			}
		}

		return token.map(TokenWrapper::getToken).orElseThrow(
				() -> new IllegalStateException("Cannot obtain VaultToken"));
	}

	protected VaultToken login() {
		return clientAuthentication.login();
	}

	protected boolean isTokenRenewable() {

		return token.map(TokenWrapper::getToken)
				.filter(LoginToken.class::isInstance)
				//
				.filter(it -> {

					LoginToken loginToken = (LoginToken) it;
					return !loginToken.getLeaseDuration().isZero()
							&& loginToken.isRenewable();
				}).isPresent();
	}

	private void scheduleRenewal() {

		logger.info("Scheduling Token renewal");

		final Runnable task = new Runnable() {
			@Override
			public void run() {
				try {
					if (LifecycleAwareSessionManager.this.token.isPresent()
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
		return new OneShotTrigger(refreshTrigger.nextExecutionTime((LoginToken) token
				.map(TokenWrapper::getToken).get()));
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

		private static final Duration ONE_SECOND = Duration.ofSeconds(1);

		private final Duration duration;

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

			this.duration = Duration.ofMillis(timeUnit.toMillis(timeout));
		}

		/**
		 * Create a new {@link FixedTimeoutRefreshTrigger} to calculate execution times of
		 * {@code timeout} before the {@link LoginToken} expires
		 * @param timeout timeout value.
		 * @since 2.0
		 */
		public FixedTimeoutRefreshTrigger(Duration timeout) {

			Assert.isTrue(timeout.toMillis() >= 0,
					"Timeout duration must be greater or equal to zero");

			this.duration = timeout;
		}

		@Override
		public Date nextExecutionTime(LoginToken loginToken) {

			long milliseconds = Math.max(ONE_SECOND.toMillis(), loginToken
					.getLeaseDuration().toMillis() - duration.toMillis());

			return new Date(System.currentTimeMillis() + milliseconds);
		}
	}

	/**
	 * Wraps a {@link VaultToken} and specifies whether the token is revocable on factory
	 * shutdown.
	 *
	 * @since 2.0
	 */
	@RequiredArgsConstructor
	@Getter
	static class TokenWrapper {

		private final VaultToken token;
		private final boolean revocable;
	}
}
