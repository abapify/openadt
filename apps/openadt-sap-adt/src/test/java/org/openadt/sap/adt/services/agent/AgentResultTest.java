package org.openadt.sap.adt.services.agent;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentResultTest {

    @Test
    void okCarriesDataAndNoError() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", 3);
        AgentResult r = AgentResult.ok(data);
        assertTrue(r.success());
        assertEquals(3, r.data().get("count"));
        assertNull(r.error());
    }

    @Test
    void okWithoutDataDefaultsToEmpty() {
        AgentResult r = AgentResult.ok();
        assertTrue(r.success());
        assertNotNull(r.data());
        assertTrue(r.data().isEmpty());
    }

    @Test
    void failRequiresError() {
        assertThrows(IllegalArgumentException.class, () -> AgentResult.fail(null));
    }

    @Test
    void failCarriesErrorNoData() {
        AgentResult r = AgentResult.fail(AgentError.internal("boom"));
        assertFalse(r.success());
        assertEquals(AgentErrorCode.INTERNAL, r.error().code());
        assertEquals("boom", r.error().message());
    }

    @Test
    void toJsonMapSuccessShape() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("variants", java.util.List.of("DEFAULT", "TRANSPORT"));
        Map<String, Object> json = AgentResult.ok(data).toJsonMap();
        assertEquals(true, json.get("success"));
        assertNotNull(json.get("data"));
        assertNull(json.get("error"));
    }

    @Test
    void toJsonMapFailureShape() {
        Map<String, Object> json = AgentResult
            .fail(new AgentError(AgentErrorCode.NOT_FOUND, "object not found"))
            .toJsonMap();
        assertEquals(false, json.get("success"));
        assertNull(json.get("data"));
        @SuppressWarnings("unchecked")
        Map<String, Object> err = (Map<String, Object>) json.get("error");
        assertNotNull(err);
        assertEquals("NOT_FOUND", err.get("code"));
        assertEquals("object not found", err.get("message"));
    }

    @Test
    void agentErrorCodeEnumIsClosed() {
        // Add a test that fails compilation if anyone adds a new code without
        // updating this list — keeps the spec in sync.
        AgentErrorCode[] expected = {
            AgentErrorCode.LOCKED_BY_OTHER,
            AgentErrorCode.NO_TRANSPORT,
            AgentErrorCode.NOT_FOUND,
            AgentErrorCode.SDK_TRANSPORT_REQUIRED,
            AgentErrorCode.INVALID_URI,
            AgentErrorCode.THROTTLED,
            AgentErrorCode.UNSUPPORTED_OBJECT_TYPE,
            AgentErrorCode.INTERNAL
        };
        assertEquals(expected.length, AgentErrorCode.values().length);
    }
}
