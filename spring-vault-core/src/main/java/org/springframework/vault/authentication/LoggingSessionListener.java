package org.springframework.vault.authentication;

import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.vault.support.VaultToken;

public class LoggingSessionListener implements SessionManagerListener {
    protected final Log logger = LogFactory.getLog(getClass());

    @Override
    public Optional<VaultToken> onSessionRenewalNeeded() {
        logger.info("Renewing token");
        return Optional.empty();
    }

    @Override
    public void onSessionRenewalSuccess(final VaultToken newToken) {
        logger.debug("Successfully renewed token!");
    }

    @Override
    public void onSessionRenewalFailure(final VaultToken oldToken) {
        logger.debug("Failed to successfully remove token!");
    }
}
