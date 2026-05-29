package org.openadt.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileFetchHintsTest {
    @Test
    void blankCliProfileReturnsNullWhenEnvUnset() {
        assertNull(ProfileFetchHints.resolveEffectiveProfile(null));
        assertNull(ProfileFetchHints.resolveEffectiveProfile("  "));
    }

    @Test
    void cliProfileTrimmed() {
        assertEquals("sso", ProfileFetchHints.resolveEffectiveProfile(" sso "));
    }

    @Test
    void appendsHintWhenDefaultSncAndSsoHttpProfileExists() {
        SystemProfile dest = new SystemProfile();
        dest.setDefaultProfile("snc");
        SystemProfile.ProfileConfig sso = new SystemProfile.ProfileConfig();
        sso.setTransport("http");
        dest.setProfiles(Map.of("snc", new SystemProfile.ProfileConfig(), "sso", sso));

        String msg = ProfileFetchHints.formatTransportError(
            dest,
            null,
            new IllegalStateException("Failed to initialize SAP JCo Eclipse bridge (com.sap.conn.jco.eclipse): null")
        );
        assertTrue(msg.contains("--profile=sso"));
        assertTrue(msg.contains("default_profile=snc"));
    }

    @Test
    void noHintWhenExplicitProfileSet() {
        SystemProfile dest = new SystemProfile();
        dest.setDefaultProfile("snc");
        String msg = ProfileFetchHints.formatTransportError(
            dest,
            "snc",
            new IllegalStateException("Failed to initialize SAP JCo Eclipse bridge")
        );
        assertEquals("Failed to initialize SAP JCo Eclipse bridge", msg);
    }
}
