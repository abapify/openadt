package org.openadt.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the developer machine Secure Login registry — opt-in only, never required in CI.
 */
@Tag("integration")
@EnabledOnOs(OS.WINDOWS)
class SecureLoginRegistryParseTest {
    @Test
    void registryProvidesEnrollUrl() {
        var secureLogin = SecureLoginBootstrap.resolveSecureLogin(null);
        assertNotNull(secureLogin, "Secure Login registry should be readable on this machine");
        String enrollUrl = secureLogin.getEnrollUrl();
        assertNotNull(enrollUrl, "enrollURL0 from Web Adapter registry");
        assertTrue(enrollUrl.startsWith("https://"), "enroll URL: " + enrollUrl);
        assertTrue(enrollUrl.contains("doLogin"), "enroll URL: " + enrollUrl);
    }
}
