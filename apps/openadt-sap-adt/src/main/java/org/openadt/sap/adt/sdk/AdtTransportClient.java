package org.openadt.sap.adt.sdk;

import org.openadt.config.SystemProfile;

public interface AdtTransportClient {
    ProxyResponse execute(SystemProfile system, ProxyRequest request);

    /** Whether this transport can retain an ADT lock context across proxy requests. */
    default boolean supportsStatefulSessions() {
        return false;
    }

    /** Opens a caller-owned stateful ADT session when {@link #supportsStatefulSessions()} is true. */
    default StatefulAdtTransportSession openStatefulSession(SystemProfile system) {
        throw new UnsupportedOperationException("This ADT transport does not support stateful sessions");
    }

    /**
     * Whether this transport keeps its own authenticated session and satisfies SAP's CSRF protection
     * upstream, so a token handed to the local client is never checked against anything.
     *
     * <p>Only such a transport may have the CSRF handshake answered locally: for one that forwards the
     * client's token to SAP, minting a token here would produce a value SAP rejects on the first write.
     * Defaults to {@code false} so a new transport has to opt in deliberately.
     */
    default boolean managesCsrfUpstream() {
        return false;
    }
}
