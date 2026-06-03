package org.openadt.sap.adt.sdk;

/**
 * Structured SDK result serialized to JSON by the CLI.
 */
public record SdkJsonResult(
    boolean ok,
    String message,
    String destinationId,
    boolean fromEclipse,
    Object payload
) implements SdkServiceResult {
}
