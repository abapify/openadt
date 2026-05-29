package org.openadt.sap.adt.sdk;

/**
 * Structured result of SDK logon or logon-status (see {@link AdtSdkServiceGateway}).
 */
public record AdtLogonStatusReport(
    boolean loggedOn,
    String destinationId,
    boolean fromEclipse,
    String message
) {
}
