package org.openadt.sap.adt.fallback.http;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.openadt.config.OpenAdtConfig;
import org.openadt.config.SystemProfile;

class AdtHttpReentranceTicketFlowTest {
    @Test
    void buildsCallbackUrlWithLocalhostHost() {
        URI callback = AdtHttpReentranceTicketFlow.buildCallbackUrl("localhost", 18080, "test-state");
        assertEquals("http://localhost:18080/adt/redirect?state=test-state", callback.toString());
    }

    @Test
    void buildsBrowserSsoEntryUrlAsSingleReentranceTicketUrlWithRedirectToLocalhost() {
        SystemProfile system = new SystemProfile();
        system.setClient("200");
        system.setLanguage("EN");
        URI callback = URI.create("http://localhost:65246/adt/redirect?state=csrf");
        URI entry = URI.create("https://s4-dev.sap.example.corp/");

        URI url = AdtHttpReentranceTicketFlow.buildBrowserSsoEntryUrl(entry, callback, system);

        assertTrue(
            url.toString().startsWith("https://s4-dev.sap.example.corp/sap/bc/adt/core/http/reentranceticket?")
        );
        assertTrue(url.toString().contains("sap-client=200"));
        assertTrue(url.toString().contains("sap-language=EN"));
        assertTrue(url.toString().contains("redirect-url=http%3A%2F%2Flocalhost%3A65246%2Fadt%2Fredirect"));
    }

    @Test
    void buildsBrowserSsoEntryUrlFromFrontendEvenWhenManualBrowserEntryDiffers() {
        SystemProfile system = new SystemProfile();
        system.setClient("200");
        system.setLanguage("EN");
        URI callback = URI.create("http://localhost:65246/adt/redirect?state=csrf");
        URI frontend = URI.create("https://s4-dev.sap.example.corp/");

        URI url = AdtHttpReentranceTicketFlow.buildBrowserSsoEntryUrl(frontend, callback, system);

        assertTrue(url.toString().startsWith("https://s4-dev.sap.example.corp/sap/bc/adt/core/http/reentranceticket?"));
        assertTrue(url.toString().contains("sap-client=200"));
        assertTrue(url.toString().contains("redirect-url=http%3A%2F%2Flocalhost%3A65246%2Fadt%2Fredirect"));
    }

    @Test
    void resolvesBrowserEntryUrlFromDestinationConfig() {
        SystemProfile system = new SystemProfile();
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setBrowserEntryUrl("https://idp.example.corp/app/sap/sso/saml");
        adt.setBaseUrl("https://s4-dev.sap.example.corp");
        system.setAdt(adt);

        URI landing = AdtHttpReentranceTicketFlow.resolveBrowserEntryUrl(system, key -> null);

        assertEquals("https://idp.example.corp/app/sap/sso/saml", landing.toString());
    }

    @Test
    void acceptsLegacyIdpUrlAliasFromConfig() throws Exception {
        String raw = """
            base_url = "https://s4-dev.sap.example.corp"
            idp_url = "https://idp.example.corp/app/sap/sso/saml"
            """;
        SystemProfile.AdtConfig adt = new TomlMapper().readValue(raw, SystemProfile.AdtConfig.class);
        SystemProfile system = new SystemProfile();
        system.setAdt(adt);
        assertEquals(
            "https://idp.example.corp/app/sap/sso/saml",
            system.getAdt().getBrowserEntryUrl()
        );
    }

    @Test
    void buildsBrowserSsoEntryUrlWithoutLegacyOpenBridgePath() {
        SystemProfile system = new SystemProfile();
        system.setClient("200");
        system.setLanguage("EN");
        URI callback = URI.create("http://localhost:65246/adt/redirect?state=csrf");
        URI entry = URI.create("https://s4-dev.sap.example.corp/");

        URI url = AdtHttpReentranceTicketFlow.buildBrowserSsoEntryUrl(entry, callback, system);

        assertFalse(url.toString().contains("/adt/open"));
    }

    @Test
    void defaultsLanguageToEnglishWhenNotProvided() {
        SystemProfile system = new SystemProfile();
        URI callback = URI.create("http://localhost:18080/adt/redirect");
        URI entry = URI.create("https://abap.example.invalid/");

        URI url = AdtHttpReentranceTicketFlow.buildBrowserSsoEntryUrl(entry, callback, system);

        assertTrue(url.toString().contains("sap-language=EN"));
    }

    @Test
    void defaultsBrowserEntryUrlToNullWithoutSystem() {
        URI landing = AdtHttpReentranceTicketFlow.resolveBrowserEntryUrl(null);

        assertNull(landing);
    }

    @Test
    void defaultsBrowserEntryUrlToNullWhenNotConfigured() {
        SystemProfile system = new SystemProfile();
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setBaseUrl("https://abap.example.corp");
        system.setAdt(adt);

        URI landing = AdtHttpReentranceTicketFlow.resolveBrowserEntryUrl(system);

        assertNull(landing);
    }

    @Test
    void rejectsFictionalBaseUrlBeforeBrowserSso() {
        SystemProfile system = new SystemProfile();
        system.setAlias("DEV");
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setBaseUrl("https://dev-adt.example.com");
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
    void prefersConfiguredBrowserEntryUrlFromProfile() {
        SystemProfile system = new SystemProfile();
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setBaseUrl("https://frontend.example.corp");
        adt.setBrowserEntryUrl("https://idp.example.corp/app/sso/saml");
        system.setAdt(adt);

        URI landing = AdtHttpReentranceTicketFlow.resolveBrowserEntryUrl(system);

        assertEquals("https://idp.example.corp/app/sso/saml", landing.toString());
    }

    @Test
    void rejectsFictionalBrowserEntryUrlFromProfile() {
        SystemProfile system = new SystemProfile();
        system.setAlias("DEV");
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setBaseUrl("https://dev-adt.example.com");
        adt.setBrowserEntryUrl("https://dev-adt.example.com/login");
        system.setAdt(adt);

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> AdtHttpReentranceTicketFlow.resolveBrowserEntryUrl(system)
        );
        assertTrue(error.getMessage().contains("browser_entry_url"));
        assertTrue(error.getMessage().contains("dev-adt.example.com"));
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
}
