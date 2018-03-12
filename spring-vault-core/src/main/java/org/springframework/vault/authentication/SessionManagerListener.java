package org.springframework.vault.authentication;

import java.util.Optional;

import org.springframework.vault.support.VaultToken;

/**
 * Optional listeners, to get events from LifeCycleAwareSessionManager
 */
public interface SessionManagerListener {

    /**
     * SessionManager has detected a Token needs to be renewed.
     * @return Optional.empty() if not performing the renewal for the SessionManager, otherwise return a valid Optional<VaultToken>
     */
    Optional<VaultToken> onSessionRenewalNeeded();

    /**
     * Called when SessionManager has succeeded in renewing the Token.
     * @param newToken The new Token obtained
     */
    void onSessionRenewalSuccess(VaultToken newToken);

    /**
     * Called when SessionManager has failed to renew the token
     */
    void onSessionRenewalFailure();
}
