/*
 * Copyright 2016-2018 the original author or authors.
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
import java.util.Map;
import java.util.Optional;

import javax.swing.text.html.Option;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
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
 * This class is thread-safe.
 *
 * @author Mark Paluch
 * @author Steven Swor
 * @see LoginToken
 * @see SessionManager
 * @see TaskScheduler
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

		Optional<VaultToken> listenerToken = listener.onSessionRenewalNeeded();
		if (listenerToken.isPresent()) {
			// Dubious logic
			token = Optional.of(new TokenWrapper(listenerToken.get(), false));
			return true;
		}

		Optional<TokenWrapper> token = this.token;
		if (!token.isPresent()) {
			getSessionToken();
			return false;
		}

		TokenWrapper wrapper = token.get();

		try {

			VaultResponse vaultResponse = restOperations.postForObject(
					"auth/token/renew-self",
					new HttpEntity<>(VaultHttpHeaders.from(token.get().getToken())),
					VaultResponse.class);

			LoginToken renewed = LoginTokenUtil.from(vaultResponse.getRequiredAuth());

			Duration validTtlThreshold = getRefreshTrigger()
					.getValidTtlThreshold(renewed);
			if (renewed.getLeaseDuration().compareTo(validTtlThreshold) <= 0) {

				if (logger.isDebugEnabled()) {
					logger.info(String
							.format("Token TTL (%s) exceeded validity TTL threshold (%s). Dropping token.",
									renewed.getLeaseDuration(), validTtlThreshold));
				}
				else {
					logger.info("Token TTL exceeded validity TTL threshold. Dropping token.");
				}
				failedToRenew();
				return false;
			}

			this.token = Optional.of(new TokenWrapper(renewed, wrapper.revocable));
			successfullyRenewed();
			return true;
		}
		catch (HttpStatusCodeException e) {

			if (e.getStatusCode().is4xxClientError()) {
				logger.debug(String
						.format("Cannot refresh token, resetting token and performing re-login: %s",
								VaultResponses.getError(e.getResponseBodyAsString())));
				failedToRenew();
				return false;
			}

			throw new VaultException(VaultResponses.getError(e.getResponseBodyAsString()));
		}
		catch (RestClientException e) {
			throw new VaultException("Cannot refresh token", e);
		}
	}

	private void failedToRenew() {
		try {
			listener.onSessionRenewalFailure();
		} catch (RuntimeException e) {
			logger.error("Error in listener", e);
		}
		token = Optional.empty();
	}

	private void successfullyRenewed() {
		try {
			listener.onSessionRenewalSuccess(token.map(tokenWrapper -> tokenWrapper.token).orElse(null));
		} catch (RuntimeException e) {
			logger.error("Error in listener", e);
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

	/**
	 * @return {@literal true} if the token is renewable.
	 */
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

		Runnable task = () -> {
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
		};

		Optional<TokenWrapper> token = this.token;

		token.ifPresent(tokenWrapper -> getTaskScheduler().schedule(task,
				createTrigger(tokenWrapper)));
	}

	private OneShotTrigger createTrigger(TokenWrapper tokenWrapper) {

		return new OneShotTrigger(
getRefreshTrigger().nextExecutionTime(
				(LoginToken) tokenWrapper.getToken()));
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
