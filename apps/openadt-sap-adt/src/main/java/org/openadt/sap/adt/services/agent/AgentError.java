package org.openadt.sap.adt.services.agent;

/**
 * Stable, JSON-serializable error envelope. Always carry one of these when
 * {@link AgentResult#success()} is {@code false}; never throw raw
 * {@code com.sap.adt.*} or {@code java.lang} exceptions to the CLI/MCP layer.
 */
public record AgentError(AgentErrorCode code, String message) {

    /** Construct from a code + message; null message normalized to empty. */
    public AgentError {
        if (code == null) {
            throw new IllegalArgumentException("AgentError.code is required");
        }
        message = message == null ? "" : message;
    }

    /** Convenience for ad-hoc internal failures. */
    public static AgentError internal(String message) {
        return new AgentError(AgentErrorCode.INTERNAL, message);
    }

    /** Convenience for ADT URI parse failures. */
    public static AgentError invalidUri(String message) {
        return new AgentError(AgentErrorCode.INVALID_URI, message);
    }

    /** Convenience for transport-must-be-sdk failures. */
    public static AgentError sdkRequired(String message) {
        return new AgentError(AgentErrorCode.SDK_TRANSPORT_REQUIRED, message);
    }
}
