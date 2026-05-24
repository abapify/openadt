package org.openadt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalProxyRegistryTest {
    @TempDir
    Path tempHome;

    @Test
    void registerReadAndUnregisterRoundTrip() throws IOException {
        System.setProperty("user.home", tempHome.toString());
        try {
            LocalProxyRegistry.ProxyEndpoint endpoint = new LocalProxyRegistry.ProxyEndpoint(
                "DEV",
                "127.0.0.1",
                8079,
                false,
                "openadt"
            );
            LocalProxyRegistry.register(endpoint);
            assertTrue(LocalProxyRegistry.read("DEV").isPresent());
            assertEquals(8079, LocalProxyRegistry.read("DEV").orElseThrow().getPort());
            LocalProxyRegistry.unregister("DEV");
            assertFalse(LocalProxyRegistry.read("DEV").isPresent());
        } finally {
            System.clearProperty("user.home");
        }
    }

    @Test
    void sanitizeAliasLowercasesAndReplacesUnsafeCharacters() {
        assertEquals("s0d", LocalProxyRegistry.sanitizeAlias("S0D"));
        assertEquals("dev_ms", LocalProxyRegistry.sanitizeAlias("DEV/MS"));
    }

    @Test
    void isAliveReturnsFalseForClosedPort() {
        LocalProxyRegistry.ProxyEndpoint endpoint = new LocalProxyRegistry.ProxyEndpoint(
            "DEV",
            "127.0.0.1",
            19,
            false,
            "openadt"
        );
        assertFalse(LocalProxyRegistry.isAlive(endpoint));
    }
}
