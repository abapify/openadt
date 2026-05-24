package org.openadt.core;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdtHttpReentranceTicketFlowTest {
    @Test
    void buildsCallbackUrlWithLocalhostHost() {
        URI callback = AdtHttpReentranceTicketFlow.buildCallbackUrl("localhost", 18080, "test-state");
        assertEquals("http://localhost:18080/adt/redirect?state=test-state", callback.toString());
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

        assertNull(landing);
    }

    @Test
    void prefersConfiguredSsoLandingUrlFromProfile() {
        SystemProfile system = new SystemProfile();
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setSsoLandingUrl("https://sso.example.invalid/");
        system.setAdt(adt);

        URI landing = AdtHttpReentranceTicketFlow.resolveSsoLandingUrl(system);

        assertEquals("https://sso.example.invalid/", landing.toString());
    }

    @Test
    void resolvesSsoBridgeUrlToCoreDiscoveryWhenDiscoveryEndsAtAdtCollection() {
        URI bridge = AdtHttpReentranceTicketFlow.resolveSsoBridgeUrl(
            URI.create("https://abap.example.invalid/sap/bc/adt")
        );

        assertEquals("https://abap.example.invalid/sap/bc/adt/core/discovery", bridge.toString());
    }

    @Test
    void resolvesSsoBridgeUrlToCoreDiscoveryWhenDiscoveryHasTrailingSlash() {
        URI bridge = AdtHttpReentranceTicketFlow.resolveSsoBridgeUrl(
            URI.create("https://abap.example.invalid/sap/bc/adt/")
        );

        assertEquals("https://abap.example.invalid/sap/bc/adt/core/discovery", bridge.toString());
    }

    @Test
    void leavesSsoBridgeUrlUnchangedWhenDiscoveryAlreadyHasSubpath() {
        URI bridge = AdtHttpReentranceTicketFlow.resolveSsoBridgeUrl(
            URI.create("https://abap.example.invalid/sap/bc/adt/core/discovery")
        );

        assertEquals("https://abap.example.invalid/sap/bc/adt/core/discovery", bridge.toString());
    }

    @Test
    void buildsSsoLaunchUrlWithEncodedReentranceTarget() {
        URI reentrance = URI.create(
            "https://abap.example.invalid/sap/bc/adt/core/http/reentranceticket?redirect-url=http%3A%2F%2Flocalhost%3A65246%2Fadt%2Fredirect"
        );
        URI launch = AdtHttpReentranceTicketFlow.buildSsoLaunchUrl("localhost", 65246, reentrance);

        assertTrue(launch.toString().startsWith("http://localhost:65246/adt/open?target="));
        assertTrue(launch.toString().contains("reentranceticket"));
    }

    @Test
    void skipsSsoBridgeWhenDiscoveryIsOriginOnly() {
        assertNull(AdtHttpReentranceTicketFlow.resolveSsoBridgeUrl(
            URI.create("https://abap.example.invalid/")
        ));
    }

    @Test
    void stripsMalformedQuerySuffixWhenSapAppendsSecondQuestionMark() {
        assertEquals(
            "487d1d71-44b3-45e3-9d36-a01b5fcdfb7a",
            AdtHttpReentranceTicketFlow.stripMalformedQuerySuffix(
                "487d1d71-44b3-45e3-9d36-a01b5fcdfb7a?_=20260524191034.9654920"
            )
        );
    }

    @Test
    void leavesQueryValueUntouchedWhenNoSecondQuestionMark() {
        assertEquals(
            "integration-ticket-value",
            AdtHttpReentranceTicketFlow.stripMalformedQuerySuffix("integration-ticket-value")
        );
        assertNull(AdtHttpReentranceTicketFlow.stripMalformedQuerySuffix(null));
    }
}
