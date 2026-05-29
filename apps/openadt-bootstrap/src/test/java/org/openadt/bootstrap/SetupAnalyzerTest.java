package org.openadt.bootstrap;

import org.junit.jupiter.api.Test;
import org.openadt.config.OpenAdtConfig;
import org.openadt.config.SystemProfile;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupAnalyzerTest {
    @Test
    void defaultAnalyzerWiresEclipseAdtDetector() {
        List<SystemDetector> detectors = new SetupAnalyzer().systemDetectors();
        assertTrue(detectors.stream().anyMatch(EclipseAdtDetector.class::isInstance));
    }

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
        adt.setBaseUrl("https://sap-dev-app.example.com:8001");
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
                OpenAdtConfig.RuntimeConfig runtime = new OpenAdtConfig.RuntimeConfig();
                runtime.setAdtPluginsDir("/tmp/eclipse/plugins");
                return runtime;
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
        assertEquals("sdk", detected.getAdt().getTransport());
        assertEquals("sso", detected.getAdt().getAuthenticationKind());
        assertNotNull(detected.getProfiles());
        assertNotNull(detected.getProfiles().get("sso"));
        assertEquals("http", detected.getProfiles().get("sso").getTransport());
        assertEquals("browser-sso", detected.getProfiles().get("sso").getAuthenticationKind());
        assertEquals("https://sap-dev-app.example.com:8001", detected.getProfiles().get("sso").getBaseUrl());
    }

    @Test
    void defaultsToRestRfcWhenOnlyJcoRuntimeIsDetected() {
        SystemProfile system = new SystemProfile();
        system.setSystemId("DEV");
        system.setAdt(new SystemProfile.AdtConfig());

        RuntimeDetector runtimeDetector = new RuntimeDetector(List.of(), List.of(), List.of()) {
            @Override
            public OpenAdtConfig.RuntimeConfig detect() {
                OpenAdtConfig.RuntimeConfig runtime = new OpenAdtConfig.RuntimeConfig();
                runtime.setJcoJar("/tmp/com.sap.conn.jco_3.1.13.jar");
                return runtime;
            }
        };

        SetupAnalyzer analyzer = new SetupAnalyzer(
            List.of(() -> List.of(system)),
            new SecureLoginDetector() {
                @Override
                public DetectionResult detectSecureLogin() {
                    return new DetectionResult(false, null);
                }
            },
            runtimeDetector
        );

        SetupAnalyzer.SetupResult result = analyzer.analyze();

        assertEquals("rest-rfc", result.systems().get(0).getAdt().getTransport());
    }
}
