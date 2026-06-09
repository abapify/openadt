package org.openadt.sap.adt.services.agent;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static service-id → {@link AgentService} registry for agent-foundation verbs.
 *
 * <p>Separate from {@code SdkServiceRegistry} because agent services
 * (a) return {@link AgentResult} rather than {@code SdkServiceResult}, and
 * (b) must be reachable from the CLI ({@code openadt adt <verb>}) without going
 * through the reflective handler chain.</p>
 *
 * <p>Registration is one line per verb in the {@link #register} block; the
 * CLI and the future MCP shell both look up by id.</p>
 */
public final class AgentServiceRegistry {

    private static final Map<String, AgentService> SERVICES = new ConcurrentHashMap<>();

    static {
        // Verb ids must match `specs/adt-agent.md` §5 and the CLI subcommand names.
        // Filled by individual verb tasks (T2..T20). T1 ships no verbs.
    }

    private AgentServiceRegistry() {
    }

    public static void register(String serviceId, AgentService service) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("serviceId is required");
        }
        if (service == null) {
            throw new IllegalArgumentException("service is required");
        }
        SERVICES.put(serviceId.trim().toLowerCase(), service);
    }

    /** Test-only: drop all registrations. */
    public static void resetForTests() {
        SERVICES.clear();
    }

    public static AgentService lookup(String serviceId) {
        if (serviceId == null) {
            return null;
        }
        return SERVICES.get(serviceId.trim().toLowerCase());
    }

    /** Sorted list of registered service ids. */
    public static List<String> serviceIds() {
        return List.copyOf(new TreeMap<>(SERVICES).keySet());
    }
}
