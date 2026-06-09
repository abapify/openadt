package org.openadt.sap.adt.services.agent;

import java.util.Map;

/**
 * Contract for a single agent-foundation service (one per verb).
 *
 * <p>Implementations live under {@code org.openadt.sap.adt.services.agent}
 * and are registered with {@link AgentServiceRegistry} under their {@code adt_*}
 * service id (see {@code specs/adt-agent.md} §5).</p>
 *
 * <p>Implementations MUST:</p>
 * <ul>
 *   <li>return an {@link AgentResult}; never throw raw {@code com.sap.adt.*} or
 *       {@code java.lang} exceptions to the caller;</li>
 *   <li>map SDK exceptions to {@link AgentErrorCode} values;</li>
 *   <li>call {@link AgentThrottle#acquire(String)} before any per-object SDK
 *       call that the LSP layer throttles (format, diagnostics, references).</li>
 * </ul>
 *
 * <p>The signature intentionally takes only the destination alias as a
 * {@code String} (not a {@code SapAdtSessionContext}) so the interface
 * compiles under the distribution profile without SAP SDK jars on the
 * classpath. Verb implementations that need a full SDK session re-open
 * one from the alias via {@code SapAdtSessionContext.open(...)}; that
 * code lives in a separate file and is excluded from the distribution
 * profile the same way {@code TransportService} is.</p>
 */
@FunctionalInterface
public interface AgentService {

    /**
     * Run the service.
     *
     * @param destinationId resolved destination alias (e.g. {@code "DEV"}).
     * @param args          parsed CLI/MCP input; the implementation validates
     *                      required keys and returns
     *                      {@link AgentErrorCode#INVALID_URI} or
     *                      {@link AgentErrorCode#NOT_FOUND} on failure.
     */
    AgentResult run(String destinationId, Map<String, String> args);
}
