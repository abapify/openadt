package org.openadt.sap.adt.fallback.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.openadt.config.SystemProfile;
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
    void cacheKeyIgnoresTrailingSlashOnBaseUrl() {
        SystemProfile a = sampleSystem();
        SystemProfile b = sampleSystem();
        b.getAdt().setBaseUrl("https://dev-adt.example.com/");
        assertEquals(HttpSsoTicketCache.cacheKey(a), HttpSsoTicketCache.cacheKey(b));
    }

    @Test
    void cacheKeyIncludesActiveProfile() {
        SystemProfile system = sampleSystem();
        system.setActiveProfile("sso");
        String withProfile = HttpSsoTicketCache.cacheKey(system);
        system.setActiveProfile("snc");
        assertNotEquals(withProfile, HttpSsoTicketCache.cacheKey(system));
    }

    @Test
    void skipsExpiredTicketOnRead(@TempDir Path openadtHome) throws Exception {
        SystemProfile system = sampleSystem();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        HttpSsoTicketCache cache = new HttpSsoTicketCache(openadtHome, key -> null, false, clock);
        String expiredTicket = SapLogonTicketValidityTest.ticketWithValidity("20200101000000");
        cache.writeSession(
            system,
            new HttpSsoTicketCache.CachedSession(expiredTicket, "https://dev-adt.example.com/sap/bc/adt")
        );
        assertTrue(cache.read(system).isEmpty());
        assertTrue(cache.readSession(system).isEmpty());
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
        adt.setBaseUrl("https://dev-adt.example.com");
        system.setAdt(adt);
        return system;
    }
}
