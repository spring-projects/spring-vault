/*
 * Copyright 2016-2019 the original author or authors.
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
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.event.AfterLoginEvent;
import org.springframework.vault.authentication.event.AfterLoginTokenRenewedEvent;
import org.springframework.vault.authentication.event.AfterLoginTokenRevocationEvent;
import org.springframework.vault.authentication.event.AuthenticationErrorEvent;
import org.springframework.vault.authentication.event.AuthenticationErrorListener;
import org.springframework.vault.authentication.event.AuthenticationListener;
import org.springframework.vault.authentication.event.BeforeLoginTokenRenewedEvent;
import org.springframework.vault.authentication.event.BeforeLoginTokenRevocationEvent;
import org.springframework.vault.authentication.event.LoginFailedEvent;
import org.springframework.vault.authentication.event.LoginTokenExpiredEvent;
import org.springframework.vault.authentication.event.LoginTokenRenewalFailedEvent;
import org.springframework.vault.authentication.event.LoginTokenRevocationFailedEvent;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

/**
 * Lifecycle-aware {@link SessionManager Session Manager}. This {@link SessionManager}
 * obtains tokens from a {@link ClientAuthentication} upon {@link #getSessionToken()
 * request} synchronizing multiple threads attempting to obtain a token concurrently.
 * <p>
 * Tokens are renewed asynchronously if a token has a lease duration. This happens 5
 * seconds before the token expires, see {@link #REFRESH_PERIOD_BEFORE_EXPIRY}.
 * <p>
 * This {@link SessionManager} also implements {@link DisposableBean} to revoke the
 * {@link LoginToken} once it's not required anymore. Token revocation will stop regular
 * token refresh. Tokens are only revoked only if the associated
 * {@link ClientAuthentication} returns a {@link LoginToken}.
 * <p>
 * If Token renewal runs into a client-side error, it assumes the token was
 * revoked/expired. It discards the token state so the next attempt will lead to another
 * login attempt.
 * <p>
 * By default, {@link VaultToken} are looked up in Vault to determine renewability and the
 * remaining TTL, see {@link #setTokenSelfLookupEnabled(boolean)}.
 * <p>
 * The session manager dispatches authentication events to {@link AuthenticationListener}
 * and {@link AuthenticationErrorListener}. Event notifications are dispatched either on
 * the calling {@link Thread} or worker threads used for background renewal.
 * <p>
 * This class is thread-safe.
 *
 * @author Mark Paluch
 * @author Steven Swor
 * @see LoginToken
 * @see SessionManager
 * @see TaskScheduler
 * @see AuthenticationEventPublisher
 */
public class LifecycleAwareSessionManager extends LifecycleAwareSessionManagerSupport
		implements SessionManager, DisposableBean {

	/**
	 * Client authentication mechanism. Used to obtain a {@link VaultToken} or
	 * {@link LoginToken}.
	 */
	private final ClientAuthentication clientAuthentication;

	/**
	 * HTTP client.
	 */
	private final RestOperations restOperations;

	private final Object lock = new Object();

	/**
	 * The token state: Contains the currently valid token that identifies the Vault
	 * session.
	 */
	private volatile Optional<TokenWrapper> token = Optional.empty();

	/**
	 * Create a {@link LifecycleAwareSessionManager} given {@link ClientAuthentication},
	 * {@link TaskScheduler} and {@link RestOperations}.
	 *
	 * @param clientAuthentication must not be {@literal null}.
	 * @param taskScheduler must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 * @since 1.0.1
	 */
	public LifecycleAwareSessionManager(ClientAuthentication clientAuthentication,
			TaskScheduler taskScheduler, RestOperations restOperations) {

		super(taskScheduler);

		Assert.notNull(clientAuthentication, "ClientAuthentication must not be null");
		Assert.notNull(taskScheduler, "TaskScheduler must not be null");
		Assert.notNull(restOperations, "RestOperations must not be null");

		this.clientAuthentication = clientAuthentication;
		this.restOperations = restOperations;
	}

	/**
	 * Create a {@link LifecycleAwareSessionManager} given {@link ClientAuthentication},
	 * {@link TaskScheduler} and {@link RestOperations}.
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

		super(taskScheduler, refreshTrigger);

		Assert.notNull(clientAuthentication, "ClientAuthentication must not be null");
		Assert.notNull(taskScheduler, "TaskScheduler must not be null");
		Assert.notNull(restOperations, "RestOperations must not be null");
		Assert.notNull(refreshTrigger, "RefreshTrigger must not be null");

		this.clientAuthentication = clientAuthentication;
		this.restOperations = restOperations;
	}

	/**
	 * The token state: Contains the currently valid token that identifies the Vault
	 * session.
	 */
	protected Optional<TokenWrapper> getToken() {
		return token;
	}

	protected void setToken(Optional<TokenWrapper> token) {
		this.token = token;
	}

	@Override
	public void destroy() {

		Optional<TokenWrapper> token = getToken();
		setToken(Optional.empty());

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
			dispatch(new BeforeLoginTokenRevocationEvent(token));
			restOperations.postForObject("auth/token/revoke-self", new HttpEntity<>(
					VaultHttpHeaders.from(token)), Map.class);
			dispatch(new AfterLoginTokenRevocationEvent(token));
		}
		catch (RuntimeException e) {
			logger.warn("Cannot revoke VaultToken: %s", e);
			dispatch(new LoginTokenRevocationFailedEvent(token, e));
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

		Optional<TokenWrapper> token = getToken();
		if (!token.isPresent()) {
			getSessionToken();
			return false;
		}

		TokenWrapper tokenWrapper = token.get();
		try {
			return doRenew(tokenWrapper);
		}
		catch (HttpStatusCodeException e) {

			setToken(Optional.empty());

			String message = "Cannot renew token, resetting token and performing re-login";

			if (e.getStatusCode().is4xxClientError()) {

				logger.warn(format(message, e));
				dispatch(new LoginTokenRenewalFailedEvent(tokenWrapper.getToken(), e));
				return false;
			}

			logger.debug(format(message, e));

			throw new VaultTokenRenewalException(format("Cannot renew token", e), e);
		}
		catch (RuntimeException e) {

			logger.debug(String.format(
					"Cannot renew token, resetting token and performing re-login: %s",
					e.toString()));
			setToken(Optional.empty());

			throw new VaultTokenRenewalException("Cannot renew token", e);
		}
	}

	private boolean doRenew(TokenWrapper wrapper) {

		dispatch(new BeforeLoginTokenRenewedEvent(wrapper.getToken()));
		VaultResponse vaultResponse = restOperations.postForObject(
				"auth/token/renew-self",
				new HttpEntity<>(VaultHttpHeaders.from(wrapper.token)),
				VaultResponse.class);

		LoginToken renewed = LoginTokenUtil.from(vaultResponse.getRequiredAuth());

		if (isExpired(renewed)) {

			if (logger.isDebugEnabled()) {
				Duration validTtlThreshold = getRefreshTrigger().getValidTtlThreshold(
						renewed);
				logger.info(String
						.format("Token TTL (%s) exceeded validity TTL threshold (%s). Dropping token.",
								renewed.getLeaseDuration(), validTtlThreshold));
			}
			else {
				logger.info("Token TTL exceeded validity TTL threshold. Dropping token.");
			}

			setToken(Optional.empty());
			dispatch(new LoginTokenExpiredEvent(renewed));
			return false;
		}

		setToken(Optional.of(new TokenWrapper(renewed, wrapper.revocable)));
		dispatch(new AfterLoginTokenRenewedEvent(renewed));

		return true;
	}

	@Override
	public VaultToken getSessionToken() {

		if (!getToken().isPresent()) {

			synchronized (lock) {

				if (!getToken().isPresent()) {
					doGetSessionToken();
				}
			}
		}

		return getToken().map(TokenWrapper::getToken).orElseThrow(
				() -> new IllegalStateException("Cannot obtain VaultToken"));
	}

	private void doGetSessionToken() {

		VaultToken token;

		try {
			token = clientAuthentication.login();
		}
		catch (VaultException e) {
			dispatch(new LoginFailedEvent(clientAuthentication, e));
			throw e;
		}

		TokenWrapper wrapper = new TokenWrapper(token, token instanceof LoginToken);

		if (isTokenSelfLookupEnabled()
				&& !ClassUtils.isAssignableValue(LoginToken.class, token)) {
			try {
				token = LoginTokenAdapter.augmentWithSelfLookup(this.restOperations,
						token);
				wrapper = new TokenWrapper(token, false);
			}
			catch (VaultTokenLookupException e) {
				logger.warn(String.format(
						"Cannot enhance VaultToken to a LoginToken: %s", e.getMessage()));
				dispatch(new AuthenticationErrorEvent(token, e));
			}
		}

		setToken(Optional.of(wrapper));
		dispatch(new AfterLoginEvent(token));

		if (isTokenRenewable()) {
			scheduleRenewal();
		}
	}

	protected VaultToken login() {
		return clientAuthentication.login();
	}

	/**
	 * @return {@literal true} if the token is renewable.
	 */
	protected boolean isTokenRenewable() {

		return getToken().map(TokenWrapper::getToken)
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

		Runnable task = () -> {
			Optional<TokenWrapper> tokenWrapper = getToken();

			if (!tokenWrapper.isPresent()) {
				return;
			}

			VaultToken token = tokenWrapper.get().getToken();

			try {
				if (isTokenRenewable()) {
					if (renewToken()) {
						scheduleRenewal();
					}
				}
			}
			catch (Exception e) {
				logger.error("Cannot renew VaultToken", e);
				dispatch(new LoginTokenRenewalFailedEvent(token, e));
			}
		};

		Optional<TokenWrapper> token = getToken();

		token.ifPresent(tokenWrapper -> getTaskScheduler().schedule(task,
				createTrigger(tokenWrapper)));
	}

	private OneShotTrigger createTrigger(TokenWrapper tokenWrapper) {

		return new OneShotTrigger(getRefreshTrigger().nextExecutionTime(
				(LoginToken) tokenWrapper.getToken()));
	}

	private static String format(String message, HttpStatusCodeException e) {
		return String.format("%s: Status %s %s %s", message, e.getRawStatusCode(),
				e.getStatusText(), VaultResponses.getError(e.getResponseBodyAsString()));
	}

	/**
	 * Wraps a {@link VaultToken} and specifies whether the token is revocable on factory
	 * shutdown.
	 *
	 * @since 2.0
	 */
	protected static class TokenWrapper {

		private final VaultToken token;
		private final boolean revocable;

		TokenWrapper(VaultToken token, boolean revocable) {
			this.token = token;
			this.revocable = revocable;
		}

		public VaultToken getToken() {
			return this.token;
		}

		public boolean isRevocable() {
			return this.revocable;
		}
	}
}
