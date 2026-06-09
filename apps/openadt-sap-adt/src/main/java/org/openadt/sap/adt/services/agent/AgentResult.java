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
 * </pre>
 *
 * <p>Helpers keep the envelope construction at one call-site per service so
 * we never leak raw SDK exceptions or alternate shapes into the JSON output.</p>
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
        if (success) {
            map.put("data", data);
        } else {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("code", error.code().name());
            err.put("message", error.message());
            map.put("error", err);
        }
        return map;
    }
}
