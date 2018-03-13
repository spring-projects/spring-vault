package org.springframework.vault.authentication;

/**
 * Optional listeners, to get events from LifeCycleAwareSessionManager
 */
public interface SessionManagerListener {

    /**
     * Called when SessionManager has succeeded in renewing the Token.
     */
    void onSessionRenewalSuccess();

    /**
     * Called when SessionManager has failed to renew the token
     */
    void onSessionRenewalFailure();
}
