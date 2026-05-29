package org.openadt.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;

import javax.net.ssl.SSLHandshakeException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopbackHubTlsProbeTest {
    @Test
    void isUnavailableHubFailureDetectsConnectException() {
        assertTrue(LoopbackHubTlsProbe.isUnavailableHubFailure(new ConnectException("connection refused")));
    }

    @Test
    void isUnavailableHubFailureIgnoresSslHandshakeErrors() {
        assertFalse(LoopbackHubTlsProbe.isUnavailableHubFailure(new SSLHandshakeException("bad cert")));
    }

    @Test
    void isUnavailableHubFailureDoesNotTrustUnavailableHubExceptionAlone() {
        assertFalse(LoopbackHubTlsProbe.isUnavailableHubFailure(
            new LoopbackHubTlsProbe.UnavailableHubException("internal setup error", new IOException("detail"))
        ));
    }

    @Test
    void probeOnClosedPortThrowsUnavailableHubException() {
        IOException error = assertThrows(
            IOException.class,
            () -> LoopbackHubTlsProbe.probeCertificate(InetAddress.getLoopbackAddress(), 59997)
        );
        assertInstanceOf(LoopbackHubTlsProbe.UnavailableHubException.class, error);
    }

    @Test
    void probeRejectsNonLoopbackAddress() {
        IOException error = assertThrows(
            IOException.class,
            () -> LoopbackHubTlsProbe.probeCertificate(InetAddress.getByName("8.8.8.8"), 443)
        );
        assertFalse(error instanceof LoopbackHubTlsProbe.UnavailableHubException);
    }
}
