package org.openadt.sap.adt.sdk;

import org.openadt.config.SystemProfile;

public interface AdtTransportClient {
    ProxyResponse execute(SystemProfile system, ProxyRequest request);

    /**
     * Whether this transport can safely accept a locally-minted CSRF token.
     * The ADT SDK manages its own authenticated session and ignores the token value,
     * so the proxy may synthesize one. HTTP and REST-RFC transports must forward a
     * real SAP token, so they must not receive a synthetic one.
     */
    default boolean canSynthesizeCsrfToken() {
        return true;
    }
}
