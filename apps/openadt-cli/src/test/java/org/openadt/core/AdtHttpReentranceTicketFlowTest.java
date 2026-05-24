package org.openadt.core;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdtHttpReentranceTicketFlowTest {
    @Test
    void buildsCallbackUrlWithLocalhostHost() {
        URI callback = AdtHttpReentranceTicketFlow.buildCallbackUrl("localhost", 18080, "test-state");
        assertTrue(callback.toString().equals("http://localhost:18080/adt/redirect?state=test-state"));
    }

    @Test
    void buildsReentranceTicketUrlWithClientLanguageAndRedirect() {
        SystemProfile system = new SystemProfile();
        system.setClient("100");
        system.setLanguage("EN");
        URI callback = URI.create("http://localhost:18080/adt/redirect");

        URI url = AdtHttpReentranceTicketFlow.buildReentranceTicketUrl(
            URI.create("https://abap.example.invalid/sap/bc/adt"),
            system,
            callback
        );

        assertTrue(url.toString().startsWith("https://abap.example.invalid/sap/bc/adt/core/http/reentranceticket?"));
        assertTrue(url.toString().contains("sap-client=100"));
        assertTrue(url.toString().contains("sap-language=EN"));
        assertTrue(url.toString().contains("redirect-url=http%3A%2F%2Flocalhost%3A18080%2Fadt%2Fredirect"));
    }

    @Test
    void defaultsLanguageToEnglishWhenNotProvided() {
        SystemProfile system = new SystemProfile();
        URI callback = URI.create("http://localhost:18080/adt/redirect");

        URI url = AdtHttpReentranceTicketFlow.buildReentranceTicketUrl(
            URI.create("https://abap.example.invalid"),
            system,
            callback
        );

        assertTrue(url.toString().contains("sap-language=EN"));
    }

    @Test
    void defaultsSsoLandingUrlToNullWithoutExplicitConfig() {
        URI landing = AdtHttpReentranceTicketFlow.resolveSsoLandingUrl(null);

        assertTrue(landing == null);
    }

    @Test
    void prefersConfiguredSsoLandingUrlFromProfile() {
        SystemProfile system = new SystemProfile();
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setSsoLandingUrl("https://sso.example.invalid/");
        system.setAdt(adt);

        URI landing = AdtHttpReentranceTicketFlow.resolveSsoLandingUrl(system);

        assertTrue(landing.toString().equals("https://sso.example.invalid/"));
    }

    @Test
    void resolvesSsoBridgeUrlFromDiscoveryPath() {
        URI bridge = AdtHttpReentranceTicketFlow.resolveSsoBridgeUrl(
            URI.create("https://abap.example.invalid/sap/bc/adt")
        );

        assertTrue(bridge.toString().equals("https://abap.example.invalid/sap/bc/adt"));
    }

    @Test
    void skipsSsoBridgeWhenDiscoveryIsOriginOnly() {
        assertTrue(AdtHttpReentranceTicketFlow.resolveSsoBridgeUrl(
            URI.create("https://abap.example.invalid/")
        ) == null);
    }

    @Test
    void normalizesCallbackStateWhenSapAppendsSecondQuestionMark() {
        String normalized = AdtHttpReentranceTicketFlow.normalizeCallbackState(
            "487d1d71-44b3-45e3-9d36-a01b5fcdfb7a?_=20260524191034.9654920"
        );

        assertTrue(normalized.equals("487d1d71-44b3-45e3-9d36-a01b5fcdfb7a"));
    }
}
