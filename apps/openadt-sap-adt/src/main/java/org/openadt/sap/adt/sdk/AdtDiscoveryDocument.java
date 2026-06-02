package org.openadt.sap.adt.sdk;

/**
 * Raw ADT discovery document fetched via the SDK transport ({@code GET /sap/bc/adt/discovery}).
 */
public record AdtDiscoveryDocument(
    boolean ok,
    int statusCode,
    String statusMessage,
    String destinationId,
    boolean fromEclipse,
    String contentType,
    byte[] body
) {
    public int bodySize() {
        return body != null ? body.length : 0;
    }
}
