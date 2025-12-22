/*
 * Copyright 2018-2025 the original author or authors.
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
import java.util.concurrent.atomic.AtomicReference;

import reactor.core.publisher.Mono;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.event.*;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Reactive implementation of Lifecycle-aware {@link ReactiveSessionManager
 * session manager}. This {@link ReactiveSessionManager} obtains tokens from an
 * {@link VaultTokenSupplier authentication method} upon
 * {@link #getSessionToken() request} guaranteeing a token to be obtained only
 * once if multiple threads attempt to obtain a token concurrently.
 * <p>Tokens are renewed asynchronously if a token has a lease duration. This
 * happens 5 seconds before the token expires, see
 * {@link #REFRESH_PERIOD_BEFORE_EXPIRY}.
 * <p>This {@link ReactiveSessionManager} also implements {@link DisposableBean}
 * to revoke the {@link LoginToken} once it's not required anymore. Token
 * revocation will stop regular token refresh. Tokens are only revoked if the
 * associated {@link VaultTokenSupplier} returns a
 * {@link LoginToken#isServiceToken() service token}.
 * <p>If Token renewal runs into a client-side error, it assumes the token was
 * revoked/expired. It discards the token state so the next attempt will lead to
 * another login attempt.
 * <p>By default, {@link VaultToken} are looked up in Vault to determine
 * renewability, remaining TTL, accessor and type, see
 * {@link #setTokenSelfLookupEnabled(boolean)}.
 * <p>The session manager dispatches authentication events to
 * {@link AuthenticationListener} and {@link AuthenticationErrorListener}.
 * <p>This class is thread-safe and uses lock-free synchronization.
 *
 * @author Mark Paluch
 * @since 2.0
 * @see LoginToken
 * @see ReactiveSessionManager
 * @see TaskScheduler
 * @see AuthenticationEventPublisher
 */
public class ReactiveLifecycleAwareSessionManager extends LifecycleAwareSessionManagerSupport
		implements ReactiveSessionManager, DisposableBean {

	private static final Mono<TokenWrapper> EMPTY = Mono.empty();

	private static final Mono<TokenWrapper> TERMINATED = Mono.error(new TerminatedException());


	/**
	 * Client authentication mechanism. Used to obtain a {@link VaultToken} or
	 * {@link LoginToken}.
	 */
	private final VaultTokenSupplier clientAuthentication;

	/**
	 * HTTP client.
	 */
	private final WebClient webClient;

	/**
	 * The token state: Contains the currently valid token that identifies the Vault
	 * session.
	 */
	private volatile AtomicReference<Mono<TokenWrapper>> token = new AtomicReference<>(EMPTY);


	/**
	 * Create a {@link ReactiveLifecycleAwareSessionManager} given
	 * {@link ClientAuthentication}, {@link TaskScheduler} and {@link WebClient}.
	 * @param clientAuthentication must not be {@literal null}.
	 * @param taskScheduler must not be {@literal null}.
	 * @param webClient must not be {@literal null}.
	 */
	public ReactiveLifecycleAwareSessionManager(VaultTokenSupplier clientAuthentication, TaskScheduler taskScheduler,
			WebClient webClient) {
		super(taskScheduler);
		Assert.notNull(clientAuthentication, "VaultTokenSupplier must not be null");
		Assert.notNull(taskScheduler, "TaskScheduler must not be null");
		Assert.notNull(webClient, "RestOperations must not be null");
		this.clientAuthentication = clientAuthentication;
		this.webClient = webClient;
	}

	/**
	 * Create a {@link ReactiveLifecycleAwareSessionManager} given
	 * {@link VaultTokenSupplier}, {@link TaskScheduler} and {@link WebClient}.
	 * @param clientAuthentication must not be {@literal null}.
	 * @param taskScheduler must not be {@literal null}.
	 * @param webClient must not be {@literal null}.
	 * @param refreshTrigger must not be {@literal null}.
	 */
	public ReactiveLifecycleAwareSessionManager(VaultTokenSupplier clientAuthentication, TaskScheduler taskScheduler,
			WebClient webClient, RefreshTrigger refreshTrigger) {
		super(taskScheduler, refreshTrigger);
		Assert.notNull(clientAuthentication, "VaultTokenSupplier must not be null");
		Assert.notNull(taskScheduler, "TaskScheduler must not be null");
		Assert.notNull(webClient, "WebClient must not be null");
		Assert.notNull(refreshTrigger, "RefreshTrigger must not be null");
		this.clientAuthentication = clientAuthentication;
		this.webClient = webClient;
	}

	@Override
	public void destroy() {
		Mono<TokenWrapper> tokenMono = this.token.get();
		this.token.set(TERMINATED);
		revokeNow(tokenMono);
	}

	/**
	 * Revoke and drop the current {@link VaultToken}.
	 * @return a mono emitting completion upon successful revocation.
	 * @since 3.0.2
	 */
	public Mono<Void> revoke() {
		return doRevoke(this.token.get()).doOnSuccess(unused -> this.token.set(EMPTY));
	}

	/**
	 * Revoke and drop the current {@link VaultToken} now.
	 * @since 3.0.2
	 */
	public void revokeNow() {
		revoke().block(Duration.ofSeconds(5));
	}

	/**
	 * Revoke a {@link VaultToken} now and block execution until revocation
	 * completes.
	 * @param tokenMono
	 */
	protected void revokeNow(Mono<TokenWrapper> tokenMono) {
		doRevoke(tokenMono).block(Duration.ofSeconds(5));
	}

	protected Mono<Void> doRevoke(Mono<TokenWrapper> tokenMono) {
		return tokenMono.filter(TokenWrapper::isRevocable).map(TokenWrapper::getToken).flatMap(this::revoke);
	}

	/**
	 * Revoke a {@link VaultToken}.
	 * @param token the token to revoke, must not be {@literal null}.
	 */
	protected Mono<Void> revoke(VaultToken token) {
		return this.webClient.post().uri("auth/token/revoke-self").headers(httpHeaders -> {
			httpHeaders.addAll(VaultHttpHeaders.from(token));
		})
				.retrieve()
				.bodyToMono(String.class)
				.doOnSubscribe(ignore -> multicastEvent(new BeforeLoginTokenRevocationEvent(token)))
				.doOnNext(ignore -> multicastEvent(new AfterLoginTokenRevocationEvent(token)))
				.onErrorResume(WebClientResponseException.class, e -> onRevokeFailed(token, e))
				.onErrorResume(Exception.class, e -> onRevokeFailed(token, e))
				.then();
	}

	private Mono<String> onRevokeFailed(VaultToken token, Throwable e) {
		if (LoginToken.hasAccessor(token)) {
			this.logger.warn("Cannot revoke VaultToken with accessor: %s".formatted(((LoginToken) token).getAccessor()),
					e);
		} else {
			this.logger.warn("Cannot revoke VaultToken", e);
		}
		multicastEvent(new LoginTokenRevocationFailedEvent(token, e));
		return Mono.empty();
	}

	/**
	 * Performs a token refresh. Creates a new token if no token was obtained
	 * before. If a token was obtained before, it uses self-renewal to renew the
	 * current token. Client-side errors (like permission denied) indicate the token
	 * cannot be renewed because it's expired or simply not found.
	 * @return the {@link VaultToken} if the refresh was successful or a new token
	 * was obtained. {@link Mono#empty()} if a new the token expired or
	 * {@link Mono#error(Throwable)} if refresh failed.
	 */
	public Mono<VaultToken> renewToken() {
		this.logger.info("Renewing token");
		Mono<TokenWrapper> tokenWrapper = this.token.get();
		if (tokenWrapper == TERMINATED) {
			return tokenWrapper.map(TokenWrapper::getToken);
		}
		if (tokenWrapper == EMPTY) {
			return getVaultToken();
		}
		return tokenWrapper.flatMap(this::doRenewToken).map(TokenWrapper::getToken);
	}

	private Mono<TokenWrapper> doRenewToken(TokenWrapper wrapper) {
		return doRenew(wrapper).onErrorResume(RuntimeException.class, e -> {
			VaultTokenRenewalException exception = new VaultTokenRenewalException(format("Cannot renew token", e), e);
			boolean shouldDrop = getLeaseStrategy().shouldDrop(exception);
			if (shouldDrop) {
				dropCurrentToken();
			}

			if (this.logger.isDebugEnabled()) {
				this.logger.debug(exception.getMessage(), exception);
			} else {
				this.logger.warn(exception.getMessage());
			}

			multicastEvent(new LoginTokenRenewalFailedEvent(wrapper.getToken(), exception));
			return shouldDrop ? EMPTY : Mono.just(wrapper);
		});
	}

	private Mono<TokenWrapper> doRenew(TokenWrapper tokenWrapper) {
		Mono<VaultResponse> exchange = this.webClient.post()
				.uri("auth/token/renew-self")
				.headers(httpHeaders -> httpHeaders.putAll(VaultHttpHeaders.from(tokenWrapper.token)))
				.retrieve()
				.bodyToMono(VaultResponse.class);

		return exchange
				.doOnSubscribe(ignore -> multicastEvent(new BeforeLoginTokenRenewedEvent(tokenWrapper.getToken())))
				.handle((response, sink) -> {

					LoginToken renewed = LoginTokenUtil.from(response.getRequiredAuth());
					if (!isExpired(renewed)) {
						sink.next(new TokenWrapper(renewed, tokenWrapper.revocable));
						multicastEvent(new AfterLoginTokenRenewedEvent(renewed));
						return;
					}
					if (this.logger.isDebugEnabled()) {
						Duration validTtlThreshold = getRefreshTrigger().getValidTtlThreshold(renewed);
						this.logger.info("Token TTL (%s) exceeded validity TTL threshold (%s). Dropping token."
								.formatted(renewed.getLeaseDuration(), validTtlThreshold));
					} else {
						this.logger.info("Token TTL exceeded validity TTL threshold. Dropping token.");
					}

					dropCurrentToken();
					multicastEvent(new LoginTokenExpiredEvent(renewed));
				});
	}

	private void dropCurrentToken() {
		Mono<TokenWrapper> tokenWrapper = this.token.get();
		if (tokenWrapper != TERMINATED) {
			this.token.compareAndSet(tokenWrapper, EMPTY);
		}
	}

	@Override
	public Mono<VaultToken> getVaultToken() throws VaultException {
		Mono<TokenWrapper> tokenWrapper = this.token.get();
		if (tokenWrapper == EMPTY) {
			Mono<TokenWrapper> obtainToken = this.clientAuthentication.getVaultToken()
					.flatMap(this::doSelfLookup) //
					.onErrorMap(it -> {
						multicastEvent(new LoginFailedEvent(this.clientAuthentication, it));
						return it;
					})
					.doOnNext(it -> {
						if (isTokenRenewable(it.getToken())) {
							scheduleRenewal(it.getToken());
						}
						multicastEvent(new AfterLoginEvent(it.getToken()));
					});

			this.token.compareAndSet(tokenWrapper, obtainToken.cache());
		}

		return this.token.get().map(TokenWrapper::getToken);
	}

	private Mono<TokenWrapper> doSelfLookup(VaultToken token) {
		TokenWrapper wrapper = new TokenWrapper(token, token instanceof LoginToken);
		if (isTokenSelfLookupEnabled() && !ClassUtils.isAssignableValue(LoginToken.class, token)) {
			Mono<VaultToken> loginTokenMono = augmentWithSelfLookup(this.webClient, token);
			return loginTokenMono.onErrorResume(e -> {
				this.logger.warn("Cannot enhance VaultToken to a LoginToken: %s".formatted(e.getMessage()));
				multicastEvent(new AuthenticationErrorEvent(token, e));
				return Mono.just(token);
			}).map(it -> new TokenWrapper(it, false));
		}
		return Mono.just(wrapper);
	}

	/**
	 * @return {@literal true} if the token is renewable.
	 */
	protected boolean isTokenRenewable(VaultToken token) {
		return Optional.of(token)
				.filter(LoginToken.class::isInstance)
				.filter(it -> {
					LoginToken loginToken = (LoginToken) it;
					return !loginToken.getLeaseDuration().isZero() && loginToken.isRenewable();
				})
				.isPresent();
	}

	private void scheduleRenewal(VaultToken token) {
		this.logger.info("Scheduling Token renewal");
		Runnable task = () -> {
			try {

				Mono<TokenWrapper> tokenWrapper = ReactiveLifecycleAwareSessionManager.this.token.get();
				if (tokenWrapper == Mono.<TokenWrapper>empty() || tokenWrapper == TERMINATED) {
					return;
				}
				if (isTokenRenewable(token)) {
					renewToken().subscribe(this::scheduleRenewal, e -> {
						this.logger.error("Cannot renew VaultToken", e);
						multicastEvent(new LoginTokenRenewalFailedEvent(token, e));
					});
				}
			} catch (Exception e) {
				this.logger.error("Cannot renew VaultToken", e);
				multicastEvent(new LoginTokenRenewalFailedEvent(token, e));
			}
		};
		getTaskScheduler().schedule(task, createTrigger(token));
	}

	private OneShotTrigger createTrigger(VaultToken token) {
		return new OneShotTrigger(getRefreshTrigger().nextExecution((LoginToken) token));
	}

	private static Mono<VaultToken> augmentWithSelfLookup(WebClient webClient, VaultToken token) {
		Mono<Map<String, Object>> data = lookupSelf(webClient, token);
		return data.map(it -> LoginTokenUtil.from(token.toCharArray(), it));
	}

	private static Mono<Map<String, Object>> lookupSelf(WebClient webClient, VaultToken token) {
		return webClient.get()
				.uri("auth/token/lookup-self")
				.headers(httpHeaders -> httpHeaders.putAll(VaultHttpHeaders.from(token)))
				.retrieve()
				.bodyToMono(VaultResponse.class)
				.map(it -> {
					Assert.state(it.getData() != null, "Token response is null");
					return it.getRequiredData();
				})
				.onErrorMap(WebClientResponseException.class, e -> {
					return new VaultTokenLookupException(format("Token self-lookup", e), e);
				});
	}

	private static String format(String message, RuntimeException e) {
		if (e instanceof WebClientResponseException wce) {
			return "%s: Status %s %s %s".formatted(message, wce.getStatusCode().value(), wce.getStatusText(),
					VaultResponses.getError(wce.getResponseBodyAsString()));
		}
		return message;
	}


	/**
	 * Wraps a {@link VaultToken} and specifies whether the token is revocable on
	 * factory shutdown.
	 *
	 * @since 2.0
	 */
	protected static class TokenWrapper {

		private final VaultToken token;

		private final boolean revocable;


		public TokenWrapper(VaultToken token, boolean revocable) {
			this.token = token;
			this.revocable = revocable;
		}


		public VaultToken getToken() {
			return this.token;
		}

		public boolean isRevocable() {
			if (token instanceof LoginToken login && login.isServiceToken()) {
				return this.revocable;
			}
			return false;
		}

	}


	/**
	 * Exception thrown if {@link ReactiveSessionManager} is disposed (terminated).
	 */
	static class TerminatedException extends IllegalStateException {

		TerminatedException() {
			super("Session manager terminated");
			setStackTrace(new StackTraceElement[0]);
		}

	}

}
