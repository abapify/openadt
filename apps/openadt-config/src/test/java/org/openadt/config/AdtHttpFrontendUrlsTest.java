package org.openadt.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdtHttpFrontendUrlsTest {
    @Test
    void baseUrlDefinesOriginAndFixedAdtPath() {
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setBaseUrl("https://abap.example.corp");

        assertEquals("https://abap.example.corp/", AdtHttpFrontendUrls.resolveFrontendOrigin(adt));
        assertEquals("https://abap.example.corp/sap/bc/adt", AdtHttpFrontendUrls.resolveAdtApiBase(adt));
    }

    @Test
    void normalizesBaseUrlWithTrailingPathToOrigin() {
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setBaseUrl("https://abap.example.corp/sap/bc/adt/");

        assertEquals("https://abap.example.corp/", AdtHttpFrontendUrls.resolveFrontendOrigin(adt));
        assertEquals("https://abap.example.corp/sap/bc/adt", AdtHttpFrontendUrls.resolveAdtApiBase(adt));
    }

    @Test
    void fallsBackToAshost() {
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setAshost("sap-dev-app.example.com");

        assertEquals("https://sap-dev-app.example.com/", AdtHttpFrontendUrls.resolveFrontendOrigin(adt));
    }

    @Test
    void missingConfigReturnsNull() {
        assertNull(AdtHttpFrontendUrls.resolveFrontendOrigin(null));
        assertNull(AdtHttpFrontendUrls.resolveAdtApiBase(new SystemProfile.AdtConfig()));
    }
}
