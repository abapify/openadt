package org.openadt.core;

import java.nio.file.Path;

public final class AdtTransportFactory {
    private AdtTransportFactory() {
    }

    public static AdtTransportClient create(OpenAdtConfig config, SystemProfile system) throws Exception {
        String transport = system.getAdt() != null ? system.getAdt().getTransport() : null;
        if ("http".equalsIgnoreCase(transport)) {
            return new HttpAdtTransportClient(config);
        }
        boolean sdkExplicit = transport == null
            || "sdk".equalsIgnoreCase(transport)
            || transport.isBlank();
        if (sdkExplicit
            && !"rest-rfc".equalsIgnoreCase(transport)
            && config.getRuntime() != null
            && config.getRuntime().getAdtPluginsDir() != null
            && !config.getRuntime().getAdtPluginsDir().isBlank()) {
            return new AdtSdkTransportClient(config);
        }

        if (config.getRuntime() == null || config.getRuntime().getJcoJar() == null) {
            throw new IllegalStateException("JCo jar not configured. Run 'openadt setup' first.");
        }

        JCoRuntimeBootstrap.prepare(config.getRuntime());
        JCoDestinationFactory factory = JCoDestinationFactory.fromJarPath(
            Path.of(config.getRuntime().getJcoJar()),
            config.getRuntime()
        );
        return new AdtRestRfcClient(factory);
    }
}
