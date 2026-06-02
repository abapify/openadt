package org.openadt.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionContextTest {
    @Test
    void cliAliasOverridesSession() {
        OpenAdtConfig config = new OpenAdtConfig();
        SessionConfig session = new SessionConfig();
        session.setSystem("S0D");
        config.setSession(session);
        assertEquals("DEV", SessionContext.resolveAlias(config, "DEV"));
    }

    @Test
    void sessionUsedWhenCliOmitted() {
        OpenAdtConfig config = new OpenAdtConfig();
        SessionConfig session = new SessionConfig();
        session.setSystem("S0D");
        config.setSession(session);
        assertEquals("S0D", SessionContext.resolveAlias(config, null));
    }

    @Test
    void requireAliasFailsWithoutContext() {
        assertThrows(IllegalArgumentException.class, () -> SessionContext.requireAlias(new OpenAdtConfig(), null));
    }
}
