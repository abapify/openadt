package org.openadt.sap.adt.services.agent;

/**
 * Closed enum of error codes emitted by agent-foundation services.
 *
 * <p>The set is intentionally small and stable; it is the contract advertised by
 * {@code specs/adt-agent.md} §7 and surfaced verbatim in the JSON envelope
 * {@code error.code} field. Adding a code requires a spec delta.</p>
 */
public enum AgentErrorCode {
    /** Another user holds the object lock. */
    LOCKED_BY_OTHER,
    /** The object has no transport layer assigned. */
    NO_TRANSPORT,
    /** Object URI is unknown to the destination. */
    NOT_FOUND,
    /** Caller asked for non-SDK transport; agent verbs are SDK-only. */
    SDK_TRANSPORT_REQUIRED,
    /** URI could not be parsed as an ADT object URI. */
    INVALID_URI,
    /** The agent is being throttled to protect the destination. */
    THROTTLED,
    /** The verb is recognized but the requested object type is not supported. */
    UNSUPPORTED_OBJECT_TYPE,
    /** Catch-all for unexpected SDK or runtime failures. */
    INTERNAL
}
