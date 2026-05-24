package org.openadt.core;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void defaultsSsoLandingUrlToNullWithoutSystem() {
        URI landing = AdtHttpReentranceTicketFlow.resolveSsoLandingUrl(null);

        assertNull(landing);
    }

    @Test
    void neverAutoOpensFrontendRootAsLanding() {
        SystemProfile system = new SystemProfile();
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setDiscoveryUrl("https://abap.example.corp/sap/bc/adt");
        system.setAdt(adt);

        assertNull(AdtHttpReentranceTicketFlow.resolveSsoLandingUrl(system));
    }

    @Test
    void detectsFictionalExampleHosts() {
        assertTrue(AdtHttpReentranceTicketFlow.isFictionalExampleHost("dev-adt.example.com"));
        assertTrue(AdtHttpReentranceTicketFlow.isFictionalExampleHost("abap.example.invalid"));
        assertTrue(AdtHttpReentranceTicketFlow.isFictionalExampleHost("DEV-ADT.EXAMPLE.COM"));
        assertTrue(AdtHttpReentranceTicketFlow.isFictionalExampleHost("sap-dev-app.example.invalid"));
        assertFalse(AdtHttpReentranceTicketFlow.isFictionalExampleHost("s4-dev.sap.example.corp"));
    }

    @Test
    void rejectsFictionalDiscoveryUrlBeforeBrowserSso() {
        SystemProfile system = new SystemProfile();
        system.setAlias("DEV");
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setDiscoveryUrl("https://dev-adt.example.com/sap/bc/adt");
        system.setAdt(adt);

        AdtHttpReentranceTicketFlow flow = new AdtHttpReentranceTicketFlow(key -> null, uri -> { });

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> flow.acquireTicket(new OpenAdtConfig(), system)
        );
        assertTrue(error.getMessage().contains("dev-adt.example.com"));
        assertTrue(error.getMessage().contains("destinations.DEV"));
    }

    @Test
    void prefersConfiguredSsoLandingUrlFromProfile() {
        SystemProfile system = new SystemProfile();
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setDiscoveryUrl("https://abap.example.corp/sap/bc/adt");
        adt.setSsoLandingUrl("https://sso.example.corp/");
        system.setAdt(adt);

        URI landing = AdtHttpReentranceTicketFlow.resolveSsoLandingUrl(system);

        assertEquals("https://sso.example.corp/", landing.toString());
    }

    @Test
    void rejectsFictionalSsoLandingUrlFromProfile() {
        SystemProfile system = new SystemProfile();
        system.setAlias("DEV");
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setDiscoveryUrl("https://abap.example.corp/sap/bc/adt");
        adt.setSsoLandingUrl("https://dev-adt.example.com/");
        system.setAdt(adt);

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> AdtHttpReentranceTicketFlow.resolveSsoLandingUrl(system)
        );
        assertTrue(error.getMessage().contains("sso_landing_url"));
        assertTrue(error.getMessage().contains("dev-adt.example.com"));
    }

    @Test
    void resolvesSsoBridgeUrlToFullDiscoveryWhenDiscoveryEndsAtAdtCollection() {
        URI bridge = AdtHttpReentranceTicketFlow.resolveSsoBridgeUrl(
            URI.create("https://abap.example.invalid/sap/bc/adt")
        );

        assertEquals("https://abap.example.invalid/sap/bc/adt/discovery", bridge.toString());
    }

    @Test
    void resolvesSsoBridgeUrlToFullDiscoveryWhenDiscoveryHasTrailingSlash() {
        URI bridge = AdtHttpReentranceTicketFlow.resolveSsoBridgeUrl(
            URI.create("https://abap.example.invalid/sap/bc/adt/")
        );

        assertEquals("https://abap.example.invalid/sap/bc/adt/discovery", bridge.toString());
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
    void skipsBridgeTabByDefaultInInteractiveMode() {
        assertFalse(AdtHttpReentranceTicketFlow.shouldOpenBridgeInBrowser(key -> null, true));
    }

    @Test
    void opensBridgeTabWhenOpenBridgeEnvSet() {
        assertTrue(AdtHttpReentranceTicketFlow.shouldOpenBridgeInBrowser(
            key -> "OPENADT_HTTP_SSO_OPEN_BRIDGE".equals(key) ? "1" : null,
            true
        ));
    }

    @Test
    void skipsBridgeTabWhenSkipBridgeEnvSet() {
        assertFalse(AdtHttpReentranceTicketFlow.shouldOpenBridgeInBrowser(
            key -> "OPENADT_HTTP_SSO_SKIP_BRIDGE".equals(key) ? "1" : null,
            true
        ));
    }

    @Test
    void skipsBridgeTabInNonInteractiveModeWhenBridgeWaitIsZero() {
        assertFalse(AdtHttpReentranceTicketFlow.shouldOpenBridgeInBrowser(
            key -> "OPENADT_HTTP_SSO_BRIDGE_WAIT_SECONDS".equals(key) ? "0" : null,
            false
        ));
    }

    @Test
    void opensBridgeTabInNonInteractiveModeWhenBridgeWaitIsPositive() {
        assertTrue(AdtHttpReentranceTicketFlow.shouldOpenBridgeInBrowser(
            key -> switch (key) {
                case "OPENADT_HTTP_SSO_OPEN_BRIDGE" -> "1";
                case "OPENADT_HTTP_SSO_BRIDGE_WAIT_SECONDS" -> "15";
                default -> null;
            },
            false
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
