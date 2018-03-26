package org.springframework.vault.authentication;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class LoggingSessionListener implements SessionManagerListener {
    protected final Log logger = LogFactory.getLog(getClass());


    @Override
    public void onSessionRenewalSuccess() {
        logger.debug("Successfully renewed token!");
    }

    @Override
    public void onSessionRenewalFailure() {
        logger.debug("Failed to successfully remove token!");
    }
}
