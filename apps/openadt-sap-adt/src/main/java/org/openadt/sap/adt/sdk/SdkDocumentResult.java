package org.openadt.sap.adt.sdk;

import java.util.Arrays;
import java.util.Objects;

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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SdkDocumentResult other)) {
            return false;
        }
        return ok == other.ok
            && statusCode == other.statusCode
            && fromEclipse == other.fromEclipse
            && Objects.equals(message, other.message)
            && Objects.equals(destinationId, other.destinationId)
            && Objects.equals(contentType, other.contentType)
            && Arrays.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ok, statusCode, message, destinationId, fromEclipse, contentType, Arrays.hashCode(body));
    }

    @Override
    public String toString() {
        return "SdkDocumentResult[ok=" + ok + ", statusCode=" + statusCode
            + ", message=" + message + ", destinationId=" + destinationId
            + ", fromEclipse=" + fromEclipse + ", contentType=" + contentType
            + ", body=" + Arrays.toString(body) + "]";
    }
}
