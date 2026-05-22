package org.openadt.core;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

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
    void buildsCookieHeaderFromEnvironmentValueAndClient() {
        OpenAdtConfig config = new OpenAdtConfig();
        HttpAdtTransportClient client = client(config, "ticket-123");
        SystemProfile system = system("https://sap.example.com:443", "200");

        String cookieHeader = client.buildCookieHeader(system);

        assertEquals("MYSAPSSO2=ticket-123; sap-usercontext=sap-client=200", cookieHeader);
    }

    @Test
    void failsWhenCookieIsMissing() {
        OpenAdtConfig config = new OpenAdtConfig();
        HttpAdtTransportClient client = client(config, null);
        SystemProfile system = system("https://sap.example.com:443", "200");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> client.buildCookieHeader(system));

        assertEquals(true, error.getMessage().contains("MYSAPSSO2"));
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
