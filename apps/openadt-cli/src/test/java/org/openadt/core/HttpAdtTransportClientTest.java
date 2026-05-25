package org.openadt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpAdtTransportClientTest {
    @Test
    void failsWhenDiscoveryUrlIsMissing() {
        OpenAdtConfig config = new OpenAdtConfig();
        HttpAdtTransportClient client = client(config, "ticket-123");
        SystemProfile system = new SystemProfile();
        system.setAlias("DEV");
        system.setClient("200");
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setTransport("http");
        system.setAdt(adt);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> client.buildTargetUri(system, "/sap/bc/adt/core/http/systeminformation"));

        assertEquals(
            "HTTP ADT transport requires destinations.<alias>.adt.discovery_url to be configured with a logical frontend URL.",
            error.getMessage()
        );
    }

    @Test
    void resolvesCookieOnlyOnceDuringDiscoveryLookups(@TempDir Path openadtHome) {
        OpenAdtConfig config = new OpenAdtConfig();
        AtomicInteger resolveCount = new AtomicInteger();
        AdtHttpCookieProvider cookieProvider = isolatedCookieProvider(
            openadtHome,
            (cfg, profile) -> {
                resolveCount.incrementAndGet();
                return "ticket-once";
            }
        );
        HttpAdtTransportClient client = new HttpAdtTransportClient(
            config,
            HttpClient.newHttpClient(),
            cookieProvider,
            new com.fasterxml.jackson.databind.ObjectMapper()
        );
        SystemProfile system = system("https://sap.example.com:443", "200");

        client.buildCookieHeader(system);
        client.buildCookieHeader(system);

        assertEquals(1, resolveCount.get());
    }

    @Test
    void buildsCookieHeaderFromEnvironmentValueAndClient() {
        OpenAdtConfig config = new OpenAdtConfig();
        HttpAdtTransportClient client = client(config, "ticket-123");
        SystemProfile system = system("https://sap.example.com:443", "200");

        String cookieHeader = client.buildCookieHeader(system);

        assertEquals("MYSAPSSO2=ticket-123; sap-usercontext=sap-client=200", cookieHeader);
    }

    @Test
    void failsWhenCookieIsMissing(@TempDir Path openadtHome) {
        OpenAdtConfig config = new OpenAdtConfig();
        AdtHttpCookieProvider cookieProvider = isolatedCookieProvider(openadtHome, (cfg, profile) -> null);
        HttpAdtTransportClient client = new HttpAdtTransportClient(
            config,
            HttpClient.newHttpClient(),
            cookieProvider,
            new com.fasterxml.jackson.databind.ObjectMapper()
        );
        SystemProfile system = system("https://sap.example.com:443", "200");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> client.buildCookieHeader(system));

        assertEquals(true, error.getMessage().contains("MYSAPSSO2"));
    }

    private static AdtHttpCookieProvider isolatedCookieProvider(Path openadtHome, AdtHttpTicketProvider ticketProvider) {
        HttpSsoTicketCache cache = new HttpSsoTicketCache(openadtHome, HttpAdtTransportClientTest::cacheDisabledEnv);
        return new AdtHttpCookieProvider(key -> null, ticketProvider, cache);
    }

    private static String cacheDisabledEnv(String key) {
        if ("OPENADT_MYSAPSSO2".equals(key) || "OPENADT_COOKIE_FILE".equals(key)) {
            return null;
        }
        if (HttpSsoTicketCache.DISABLE_ENV.equals(key)) {
            return "1";
        }
        return null;
    }

    private static HttpAdtTransportClient client(OpenAdtConfig config, String ticket) {
        AdtHttpCookieProvider cookieProvider = new AdtHttpCookieProvider(key -> ticket);
        return new HttpAdtTransportClient(
            config,
            HttpClient.newHttpClient(),
            cookieProvider,
            new com.fasterxml.jackson.databind.ObjectMapper()
        );
    }

    private static SystemProfile system(String discoveryUrl, String client) {
        SystemProfile system = new SystemProfile();
        system.setAlias("DEV");
        system.setClient(client);
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setTransport("http");
        adt.setDiscoveryUrl(discoveryUrl);
        system.setAdt(adt);
        return system;
    }
}
