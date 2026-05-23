package org.openadt.core;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdtHttpReentranceTicketFlowTest {
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
}
