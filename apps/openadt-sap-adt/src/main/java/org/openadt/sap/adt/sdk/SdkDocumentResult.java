package org.openadt.sap.adt.sdk;

/**
 * HTTP document body from an SDK session (discovery Atom/XML, etc.).
 */
public record SdkDocumentResult(
    boolean ok,
    int statusCode,
    String message,
    String destinationId,
    boolean fromEclipse,
    String contentType,
    byte[] body
) implements SdkServiceResult {
    public SdkDocumentResult {
        body = body != null ? body : new byte[0];
    }
}
