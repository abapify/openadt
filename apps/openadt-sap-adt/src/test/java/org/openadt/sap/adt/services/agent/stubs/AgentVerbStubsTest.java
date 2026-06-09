package org.openadt.sap.adt.services.agent.stubs;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openadt.sap.adt.services.agent.AgentErrorCode;
import org.openadt.sap.adt.services.agent.AgentResult;
import org.openadt.sap.adt.services.agent.AgentService;
import org.openadt.sap.adt.services.agent.AgentServiceRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVerbStubsTest {

    @BeforeAll
    static void ensureStubsRegistered() {
        // The static {} block in AgentVerbStubs fires on first reference
        // to a static method or field; we call registerAll() to be sure
        // (idempotent).
        AgentVerbStubs.registerAll();
    }

    @Test
    void registerAllPopulatesAllVerbs() {
        var ids = AgentServiceRegistry.serviceIds();
        assertTrue(ids.contains("adt_atc_get_variants"), "atc_get_variants missing");
        assertTrue(ids.contains("adt_atc_run_check"), "atc_run_check missing");
        assertTrue(ids.contains("adt_lock_object"), "lock_object missing");
        assertTrue(ids.contains("adt_unlock_object"), "unlock_object missing");
        assertTrue(ids.contains("adt_format_code"), "format_code missing");
        assertTrue(ids.contains("adt_get_diagnostics"), "get_diagnostics missing");
        assertTrue(ids.contains("adt_find_references"), "find_references missing");
        assertTrue(ids.contains("adt_quick_search"), "quick_search missing");
        assertTrue(ids.contains("adt_check_transport_lock"), "check_transport_lock missing");
        assertTrue(ids.contains("adt_create_transport"), "create_transport missing");
        assertTrue(ids.contains("adt_assign_transport"), "assign_transport missing");
        assertTrue(ids.contains("adt_toggle_version"), "toggle_version missing");
        assertTrue(ids.contains("adt_get_hover"), "get_hover missing");
        assertTrue(ids.contains("adt_document_symbols"), "document_symbols missing");
        assertTrue(ids.contains("adt_run_application"), "run_application missing");
        assertTrue(ids.contains("adt_get_coverage"), "get_coverage missing");
        assertTrue(ids.contains("adt_search_transports"), "search_transports missing");
        assertTrue(ids.contains("adt_refresh_object"), "refresh_object missing");
    }

    @Test
    void stubReturnsInternalWithPlannedSdkClass() {
        AgentService service = AgentServiceRegistry.lookup("adt_lock_object");
        assertNotNull(service);
        AgentResult result = service.run("DEV", Map.of("uri", "/sap/bc/adt/oo/classes/zcl_foo"));
        assertFalse(result.success(), "stub verbs are not yet wired, so they must return failure");
        assertEquals(AgentErrorCode.INTERNAL, result.error().code());
        assertEquals("awaiting-sap-sdk-wiring", result.data().get("status"));
        assertEquals("IAdtLockService", result.data().get("plannedSdkClass"));
        assertEquals("DEV", result.data().get("destination"));
        assertEquals("adt_lock_object", result.data().get("verb"));
    }

    @Test
    void stubFailsInvalidUri() {
        AgentService service = AgentServiceRegistry.lookup("adt_lock_object");
        assertNotNull(service);
        AgentResult result = service.run("DEV", Map.of("uri", "not a uri"));
        assertFalse(result.success());
        assertEquals(AgentErrorCode.INVALID_URI, result.error().code());
    }

    @Test
    void stubAcceptsValidAbsoluteUri() {
        AgentService service = AgentServiceRegistry.lookup("adt_format_code");
        assertNotNull(service);
        AgentResult result = service.run("DEV",
            Map.of("uri", "/sap/bc/adt/oo/classes/zcl_foo", "content", ""));
        // Should NOT be INVALID_URI — uri parses fine; should fail with
        // INTERNAL awaiting-wiring.
        assertEquals(AgentErrorCode.INTERNAL, result.error().code());
    }

    @Test
    void stubAcceptsTypedShorthand() {
        AgentService service = AgentServiceRegistry.lookup("adt_format_code");
        assertNotNull(service);
        AgentResult result = service.run("DEV", Map.of("uri", "class:zcl_foo"));
        assertEquals(AgentErrorCode.INTERNAL, result.error().code());
    }

    @Test
    void toJsonMapIncludesErrorAndData() {
        AgentService service = AgentServiceRegistry.lookup("adt_quick_search");
        assertNotNull(service);
        AgentResult result = service.run("DEV", Map.of("searchTerm", "Z*"));
        Map<String, Object> json = result.toJsonMap();
        assertEquals(false, json.get("success"));
        assertNotNull(json.get("error"));
        assertNotNull(json.get("data"));
    }
}
