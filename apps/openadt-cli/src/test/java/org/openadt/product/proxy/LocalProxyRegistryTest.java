package org.openadt.product.proxy;

import com.sun.net.httpserver.HttpServer;
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
        String previousHome = System.getProperty("user.home");
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
            if (previousHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", previousHome);
            }
        }
    }

    @Test
    void sanitizeAliasLowercasesAndReplacesUnsafeCharacters() {
        assertEquals("dev", LocalProxyRegistry.sanitizeAlias("DEV"));
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

    @Test
    void isAliveReturnsTrueForLoopbackHttpServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/__openadt/health", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            LocalProxyRegistry.ProxyEndpoint endpoint = new LocalProxyRegistry.ProxyEndpoint(
                "DEV",
                "127.0.0.1",
                server.getAddress().getPort(),
                false,
                "openadt"
            );
            assertTrue(LocalProxyRegistry.isAlive(endpoint));
        } finally {
            server.stop(0);
        }
    }
}
