package org.openadt.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpTlsTrustResolverTest {
    @Test
    void prefersDestinationAdtCaCertOverRuntime() {
        OpenAdtConfig config = new OpenAdtConfig();
        OpenAdtConfig.RuntimeConfig runtime = new OpenAdtConfig.RuntimeConfig();
        runtime.setHttpCaCert("C:\\global.pem");
        config.setRuntime(runtime);

        SystemProfile system = new SystemProfile();
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setHttpCaCert("C:\\s0d.pem");
        system.setAdt(adt);

        assertEquals(
            "C:\\s0d.pem",
            HttpTlsTrustResolver.resolveCaCert(config, system, key -> null)
        );
    }

    @Test
    void fallsBackToRuntimeWhenDestinationHasNoCaCert() {
        OpenAdtConfig config = new OpenAdtConfig();
        OpenAdtConfig.RuntimeConfig runtime = new OpenAdtConfig.RuntimeConfig();
        runtime.setHttpCaCert("C:\\global.pem");
        config.setRuntime(runtime);

        assertEquals(
            "C:\\global.pem",
            HttpTlsTrustResolver.resolveCaCert(config, new SystemProfile(), key -> null)
        );
    }

    @Test
    void fallsBackToEnvWhenNoDestinationOrRuntimeCaCert() {
        assertEquals(
            "C:\\env.pem",
            HttpTlsTrustResolver.resolveCaCert(
                new OpenAdtConfig(),
                new SystemProfile(),
                key -> "OPENADT_HTTP_CA_CERT".equals(key) ? "C:\\env.pem" : null
            )
        );
    }

    @Test
    void profileHttpCaCertAppliesToEffectiveAdt() {
        OpenAdtConfig config = new OpenAdtConfig();
        SystemProfile destination = new SystemProfile();
        destination.setAlias("DEV");
        destination.setDefaultProfile("sso");
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setDiscoveryUrl("https://abap.example.invalid/sap/bc/adt");
        destination.setAdt(adt);

        SystemProfile.ProfileConfig profile = new SystemProfile.ProfileConfig();
        profile.setHttpCaCert("C:\\profile-sso.pem");
        destination.setProfiles(java.util.Map.of("sso", profile));
        config.setSystems(java.util.List.of(destination));

        SystemProfile effective = DestinationProfileResolver.resolve(config, "DEV", "sso");

        assertEquals("C:\\profile-sso.pem", effective.getAdt().getHttpCaCert());
        assertEquals(
            "C:\\profile-sso.pem",
            HttpTlsTrustResolver.resolveCaCert(config, effective, key -> null)
        );
    }

    @Test
    void returnsNullWhenNoTrustConfigured() {
        assertNull(HttpTlsTrustResolver.resolveCaCert(new OpenAdtConfig(), new SystemProfile(), key -> null));
    }
}
