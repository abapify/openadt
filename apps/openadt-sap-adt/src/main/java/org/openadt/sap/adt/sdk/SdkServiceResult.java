package org.openadt.sap.adt.sdk;

/**
 * Result of a registered SDK service ({@code SdkServiceRegistry}).
 */
public sealed interface SdkServiceResult permits SdkDocumentResult, SdkJsonResult {
    String destinationId();

    boolean fromEclipse();

    boolean ok();

    String message();
}
