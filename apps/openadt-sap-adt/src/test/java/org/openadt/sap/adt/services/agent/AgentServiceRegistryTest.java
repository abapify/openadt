package org.openadt.sap.adt.services.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentServiceRegistryTest {

    @BeforeEach
    void clear() {
        AgentServiceRegistry.resetForTests();
    }

    @Test
    void registerAndLookupIsCaseInsensitive() {
        AgentService svc = (destinationId, args) -> AgentResult.ok();
        AgentServiceRegistry.register("adt_atc_run_check", svc);
        assertSame(svc, AgentServiceRegistry.lookup("adt_atc_run_check"));
        assertSame(svc, AgentServiceRegistry.lookup("ADT_ATC_RUN_CHECK"));
        assertSame(svc, AgentServiceRegistry.lookup(" adt_atc_run_check "));
    }

    @Test
    void unknownIdReturnsNull() {
        assertNull(AgentServiceRegistry.lookup("adt_does_not_exist"));
    }

    @Test
    void rejectsNullArgs() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentServiceRegistry.register(null, (d, a) -> AgentResult.ok()));
        assertThrows(IllegalArgumentException.class,
            () -> AgentServiceRegistry.register("x", null));
    }

    @Test
    void serviceIdsIsSorted() {
        AgentServiceRegistry.register("adt_z_last", (d, a) -> AgentResult.ok());
        AgentServiceRegistry.register("adt_a_first", (d, a) -> AgentResult.ok());
        AgentServiceRegistry.register("adt_m_middle", (d, a) -> AgentResult.ok());
        var ids = AgentServiceRegistry.serviceIds();
        assertEquals(3, ids.size());
        assertEquals("adt_a_first", ids.get(0));
        assertEquals("adt_m_middle", ids.get(1));
        assertEquals("adt_z_last", ids.get(2));
    }

    @Test
    void registeredServiceIsInvokable() {
        AgentServiceRegistry.register("adt_smoke", (destinationId, args) -> {
            assertNotNull(args);
            return AgentResult.ok();
        });
        AgentService svc = AgentServiceRegistry.lookup("adt_smoke");
        assertNotNull(svc);
        AgentResult r = svc.run("DEV", java.util.Map.of());
        assertTrue(r.success());
    }
}
