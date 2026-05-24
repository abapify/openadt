package org.openadt.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpTlsConfigurerTest {
    @Test
    void returnsNullWhenNoCustomTrustConfigurationIsProvided() {
        HttpTlsConfigurer configurer = new HttpTlsConfigurer(key -> null);

        assertNull(configurer.buildSslContext(new OpenAdtConfig()));
    }

    @Test
    void failsWhenConfiguredTruststoreDoesNotExist() {
        OpenAdtConfig config = new OpenAdtConfig();
        OpenAdtConfig.RuntimeConfig runtime = new OpenAdtConfig.RuntimeConfig();
        runtime.setHttpTruststore("/does/not/exist.p12");
        runtime.setHttpTruststorePassword("secret");
        config.setRuntime(runtime);

        HttpTlsConfigurer configurer = new HttpTlsConfigurer(key -> null);

        assertThrows(IllegalStateException.class, () -> configurer.buildSslContext(config));
    }
}
