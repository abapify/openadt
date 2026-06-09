package org.openadt.sap.adt.services.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stable JSON envelope returned by every agent-foundation service.
 *
 * <p>Wire shape (matches {@code specs/adt-agent.md} §5):</p>
 * <pre>
 *   { "success": true,  "data": { ...verb-specific... } }
 *   { "success": false, "error": { "code": "...", "message": "..." } }
 *   { "success": false, "error": { ... }, "data": { ... } }
 * </pre>
 *
 * <p>The third shape (failure with diagnostic data) is used by verb stubs
 * (plans/lsp-agent-foundation.md T2..T20) to surface structured information
 * alongside the error so an agent can tell apart "verb missing" from
 * "verb registered, awaiting SDK wiring".</p>
 */
public final class AgentResult {

    private final boolean success;
    private final Map<String, Object> data;
    private final AgentError error;

    private AgentResult(boolean success, Map<String, Object> data, AgentError error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static AgentResult ok(Map<String, Object> data) {
        return new AgentResult(true, data == null ? Map.of() : data, null);
    }

    public static AgentResult ok() {
        return new AgentResult(true, Map.of(), null);
    }

    public static AgentResult fail(AgentError error) {
        if (error == null) {
            throw new IllegalArgumentException("AgentResult.fail requires a non-null AgentError");
        }
        return new AgentResult(false, null, error);
    }

    /**
     * Failure that also carries structured diagnostic data. Used by verb
     * stubs so the agent can read {@code data.verb}, {@code data.plannedSdkClass},
     * etc. alongside the error.
     */
    public static AgentResult fail(AgentError error, Map<String, Object> data) {
        if (error == null) {
            throw new IllegalArgumentException("AgentResult.fail requires a non-null AgentError");
        }
        return new AgentResult(false, data == null ? Map.of() : data, error);
    }

    public boolean success() {
        return success;
    }

    public Map<String, Object> data() {
        return data == null ? Map.of() : data;
    }

    public AgentError error() {
        return error;
    }

    /**
     * Build the JSON map. Order is preserved for stable test snapshots:
     * success, data, error.
     */
    public Map<String, Object> toJsonMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", success);
        if (data != null && !data.isEmpty()) {
            map.put("data", data);
        }
        if (!success) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("code", error.code().name());
            err.put("message", error.message());
            map.put("error", err);
        }
        return map;
    }
}
