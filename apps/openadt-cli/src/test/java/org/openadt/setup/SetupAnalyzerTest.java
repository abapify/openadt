package org.openadt.setup;

import org.junit.jupiter.api.Test;
import org.openadt.core.OpenAdtConfig;
import org.openadt.core.SystemProfile;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SetupAnalyzerTest {
    @Test
    void appliesSsoDefaultsToDetectedSystems() {
        SystemProfile system = new SystemProfile();
        system.setSystemId("DEV");

        SystemProfile.JcoConfig jco = new SystemProfile.JcoConfig();
        jco.setSncMode("1");
        jco.setSncQop("9");
        jco.setSncPartnername("p:CN=SAPServiceDEV");
        jco.setSncSso("1");
        system.setJco(jco);

        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setDiscoveryUrl("https://sap-dev-app.example.com:8001/sap/bc/adt");
        system.setAdt(adt);

        SystemDetector detector = () -> List.of(system);
        SecureLoginDetector secureLoginDetector = new SecureLoginDetector() {
            @Override
            public DetectionResult detectSecureLogin() {
                return new DetectionResult(true, "https://127.0.0.1:34443");
            }
        };
        RuntimeDetector runtimeDetector = new RuntimeDetector(List.of(), List.of(), List.of()) {
            @Override
            public OpenAdtConfig.RuntimeConfig detect() {
                return null;
            }
        };

        SetupAnalyzer analyzer = new SetupAnalyzer(List.of(detector), secureLoginDetector, runtimeDetector);

        SetupAnalyzer.SetupResult result = analyzer.analyze();

        assertEquals(1, result.systems().size());
        SystemProfile detected = result.systems().get(0);
        assertEquals("DEV", detected.getAlias());
        assertEquals(System.getProperty("user.name").toUpperCase(Locale.ROOT), detected.getUser());
        assertEquals("EN", detected.getLanguage());
        assertEquals("1", detected.getJco().getSticky());
        assertEquals("1", detected.getJco().getDenyInitialPassword());
        assertNotNull(detected.getAdt());
        assertEquals("http", detected.getAdt().getTransport());
        assertEquals("sso", detected.getAdt().getAuthenticationKind());
    }
}
