package org.openadt.sap.adt.sdk;

import java.util.Arrays;
import java.util.Objects;

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
    public AdtDiscoveryDocument {
        body = body != null ? body : new byte[0];
    }

    public int bodySize() {
        return body.length;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AdtDiscoveryDocument other)) {
            return false;
        }
        return ok == other.ok
            && statusCode == other.statusCode
            && fromEclipse == other.fromEclipse
            && Objects.equals(statusMessage, other.statusMessage)
            && Objects.equals(destinationId, other.destinationId)
            && Objects.equals(contentType, other.contentType)
            && Arrays.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ok, statusCode, statusMessage, destinationId, fromEclipse, contentType, Arrays.hashCode(body));
    }

    @Override
    public String toString() {
        return "AdtDiscoveryDocument[ok=" + ok + ", statusCode=" + statusCode
            + ", statusMessage=" + statusMessage + ", destinationId=" + destinationId
            + ", fromEclipse=" + fromEclipse + ", contentType=" + contentType
            + ", body=" + Arrays.toString(body) + "]";
    }
}
