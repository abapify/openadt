package org.openadt.sap.adt.sdk;

/**
 * Serializable transport request row for {@code transport.list}.
 */
public record AdtTransportRequestRow(
    String number,
    String targetSystem,
    String description,
    String user,
    String uri,
    String repositoryId,
    String lastChanged
) {
}
