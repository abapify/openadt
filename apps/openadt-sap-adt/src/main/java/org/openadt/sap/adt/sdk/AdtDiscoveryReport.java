package org.openadt.sap.adt.sdk;

import java.util.List;

/**
 * Structured result of SDK ADT discovery (see {@link AdtSdkServiceGateway#discover}).
 */
public record AdtDiscoveryReport(
    boolean ok,
    String statusMessage,
    String destinationId,
    boolean fromEclipse,
    String collectionUri,
    String categoryTerm,
    String memberUri,
    List<String> acceptedContentTypes
) {
}
