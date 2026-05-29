package org.openadt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpSsoTicketCacheTest {
    @Test
    void roundTripTicket(@TempDir Path openadtHome) throws Exception {
        SystemProfile system = sampleSystem();
        HttpSsoTicketCache cache = new HttpSsoTicketCache(openadtHome, key -> null);

        assertTrue(cache.read(system).isEmpty());
        cache.writeSession(system, new HttpSsoTicketCache.CachedSession("cached-ticket-value", "https://dev-adt.example.com/sap/bc/adt"));
        assertEquals(Optional.of("cached-ticket-value"), cache.read(system));
        assertTrue(cache.readSession(system).get().hasApiBase());

        cache.invalidate(system);
        assertTrue(cache.read(system).isEmpty());
    }

    @Test
    void disabledByEnvironment(@TempDir Path openadtHome) throws Exception {
        SystemProfile system = sampleSystem();
        HttpSsoTicketCache cache = new HttpSsoTicketCache(
            openadtHome,
            key -> "OPENADT_HTTP_SSO_NO_CACHE".equals(key) ? "1" : null
        );
        cache.write(system, "ticket");
        assertTrue(cache.read(system).isEmpty());
    }

    @Test
    void disabledForRequestNoCache(@TempDir Path openadtHome) throws Exception {
        SystemProfile system = sampleSystem();
        HttpSsoTicketCache cache = new HttpSsoTicketCache(openadtHome, key -> null, true);
        cache.write(system, "ticket");
        assertTrue(cache.read(system).isEmpty());
    }

    @Test
    void cacheKeyIgnoresTrailingSlashOnDiscoveryUrl() {
        SystemProfile a = sampleSystem();
        SystemProfile b = sampleSystem();
        b.getAdt().setDiscoveryUrl("https://dev-adt.example.com/sap/bc/adt/");
        assertEquals(HttpSsoTicketCache.cacheKey(a), HttpSsoTicketCache.cacheKey(b));
    }

    @Test
    void cacheKeyIncludesActiveProfile() {
        SystemProfile system = sampleSystem();
        system.setActiveProfile("sso");
        String withProfile = HttpSsoTicketCache.cacheKey(system);
        system.setActiveProfile("snc");
        assertTrue(!withProfile.equals(HttpSsoTicketCache.cacheKey(system)));
    }

    @Test
    void cacheKeySeparatesDestinations(@TempDir Path openadtHome) throws Exception {
        SystemProfile a = sampleSystem();
        SystemProfile b = sampleSystem();
        b.setAlias("QAS");
        HttpSsoTicketCache cache = new HttpSsoTicketCache(openadtHome, key -> null);
        cache.write(a, "ticket-a");
        assertEquals(Optional.of("ticket-a"), cache.read(a));
        assertTrue(cache.read(b).isEmpty());
    }

    private static SystemProfile sampleSystem() {
        SystemProfile system = new SystemProfile();
        system.setAlias("DEV");
        system.setClient("200");
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setDiscoveryUrl("https://dev-adt.example.com/sap/bc/adt");
        system.setAdt(adt);
        return system;
    }
}
