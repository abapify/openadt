package org.openadt.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdtTransportFactoryTest {
    @Test
    void returnsHttpTransportWhenExplicitlyConfigured() throws Exception {
        OpenAdtConfig config = new OpenAdtConfig();
        SystemProfile system = new SystemProfile();
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setTransport("http");
        system.setAdt(adt);

        AdtTransportClient client = AdtTransportFactory.create(config, system);

        assertInstanceOf(HttpAdtTransportClient.class, client);
    }

    @Test
    void prefersSdkTransportWhenAdtPluginsAreConfigured() throws Exception {
        OpenAdtConfig config = new OpenAdtConfig();
        OpenAdtConfig.RuntimeConfig runtime = new OpenAdtConfig.RuntimeConfig();
        runtime.setAdtPluginsDir("/tmp/eclipse/plugins");
        config.setRuntime(runtime);
        SystemProfile system = new SystemProfile();

        AdtTransportClient client = AdtTransportFactory.create(config, system);

        assertInstanceOf(AdtSdkTransportClient.class, client);
    }

    @Test
    void restRfcTransportRequiresJcoJar() {
        OpenAdtConfig config = new OpenAdtConfig();
        SystemProfile system = new SystemProfile();
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setTransport("rest-rfc");
        system.setAdt(adt);

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> AdtTransportFactory.create(config, system));

        org.junit.jupiter.api.Assertions.assertEquals(
            "JCo jar not configured. Run 'openadt setup' first.",
            error.getMessage()
        );
    }
}
