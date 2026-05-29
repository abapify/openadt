package org.openadt.sap.adt.fallback.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.openadt.config.OpenAdtConfig;
import org.openadt.config.SystemProfile;
import org.openadt.sap.adt.sdk.ProxyRequest;
import org.openadt.sap.adt.sdk.ProxyResponse;
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
            "HTTP ADT transport requires destinations.<alias>.adt.base_url (SAP frontend origin, e.g. https://host).",
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
    void retriesBrowserSsoAfterCachedTicket401(@TempDir Path openadtHome) throws Exception {
        AtomicInteger httpCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sap/bc/adt/core/http/systeminformation", exchange -> {
            httpCalls.incrementAndGet();
            if (httpCalls.get() == 1) {
                exchange.sendResponseHeaders(401, -1);
            } else {
                byte[] body = "{}".getBytes();
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            String apiBase = "http://127.0.0.1:" + port + "/sap/bc/adt";
            SystemProfile system = system(apiBase, "200");
            HttpSsoTicketCache cache = new HttpSsoTicketCache(openadtHome, key -> null);
            cache.writeSession(
                system,
                new HttpSsoTicketCache.CachedSession("stale-ticket", apiBase, Map.of())
            );
            AtomicInteger acquireCount = new AtomicInteger();
            AdtHttpCookieProvider cookieProvider = new AdtHttpCookieProvider(
                key -> null,
                (cfg, profile) -> {
                    acquireCount.incrementAndGet();
                    return "fresh-ticket";
                },
                cache
            );
            HttpAdtTransportClient client = new HttpAdtTransportClient(
                new OpenAdtConfig(),
                HttpClient.newHttpClient(),
                cookieProvider,
                new com.fasterxml.jackson.databind.ObjectMapper()
            );
            ProxyResponse response = client.execute(
                system,
                new ProxyRequest("GET", "/sap/bc/adt/core/http/systeminformation", "HTTP/1.1", Map.of(), new byte[0])
            );
            assertEquals(200, response.statusCode());
            assertEquals(2, httpCalls.get());
            assertEquals(1, acquireCount.get());
        } finally {
            server.stop(0);
        }
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
        adt.setBaseUrl(discoveryUrl.replace("/sap/bc/adt", ""));
        system.setAdt(adt);
        return system;
    }
}
