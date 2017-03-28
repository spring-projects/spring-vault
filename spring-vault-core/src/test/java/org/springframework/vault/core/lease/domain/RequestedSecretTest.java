package org.springframework.vault.core.lease.domain;

import org.junit.Test;
import org.springframework.vault.core.lease.domain.RequestedSecret.Mode;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestedSecretTest {

    @Test
    public void should_build_rotating_requested_secret() {
        RequestedSecret requestedSecret = RequestedSecret.buildFromMode(Mode.ROTATE, "my/path");

        assertThat(requestedSecret.getMode()).isEqualTo(Mode.ROTATE);
    }

    @Test
    public void should_build_renewal_requested_secret() {
        RequestedSecret requestedSecret = RequestedSecret.buildFromMode(Mode.RENEW, "my/path");

        assertThat(requestedSecret.getMode()).isEqualTo(Mode.RENEW);
    }

}